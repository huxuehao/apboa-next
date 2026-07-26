package com.hxh.apboa.gateway.core;

import com.hxh.apboa.gateway.auth.PlatformAuthVerifier;
import com.hxh.apboa.gateway.option.GatewayApiOption;
import com.hxh.apboa.gateway.option.GatewayAppOption;
import com.hxh.apboa.gateway.service.GatewayDataService;
import com.hxh.apboa.gateway.log.GatewayLogWriter;
import com.hxh.apboa.workflow.run.cache.RunWorkflowCache;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 描述：网关应用生命周期管理器
 * 负责应用HTTP服务的部署与卸载、API路由的动态挂载与重置。
 * 生命周期操作低频且需要保证顺序性，统一以同步方法执行；
 * 数据路径（请求处理）完全运行在Vert.x事件循环上，不受影响
 *
 * @author huxuehao
 **/
@Slf4j
@Component
@RequiredArgsConstructor
public class GatewayLifecycleManager {
    private static final long LISTEN_TIMEOUT_SECONDS = 10;

    private final Vertx vertx;
    private final GatewayDataService dataService;
    private final PlatformAuthVerifier authVerifier;
    private final WorkflowInvoker workflowInvoker;
    private final GatewayLogWriter logWriter;

    /** 已部署应用：appId -> DeployedApp */
    private final Map<Long, DeployedApp> apps = new ConcurrentHashMap<>();

    /**
     * 上线应用并挂载其下所有在线API
     */
    public synchronized void onlineApp(GatewayAppOption option) {
        if (apps.containsKey(option.getId())) {
            log.info("网关应用 [{}:{}] 已在线，跳过部署", option.getName(), option.getPort());
            return;
        }
        try {
            Router router = Router.router(vertx);
            HttpServer server = vertx.createHttpServer()
                    .requestHandler(router)
                    .listen(option.getPort())
                    .toCompletionStage().toCompletableFuture()
                    .get(LISTEN_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            DeployedApp app = new DeployedApp(option, router, server, authVerifier, workflowInvoker, logWriter);
            app.initAppRoutes();
            apps.put(option.getId(), app);
            log.info("网关应用 [{}] 已部署，监听端口 {}", option.getName(), option.getPort());

            // 挂载应用下所有在线API
            List<GatewayApiOption> apis = dataService.loadOnlineApis(option.getId());
            apis.forEach(api -> mountApi(app, api));
        } catch (Exception e) {
            log.error("网关应用 [{}:{}] 部署失败: {}", option.getName(), option.getPort(), e.getMessage(), e);
        }
    }

    /**
     * 下线应用（卸载全部路由并关闭端口）
     */
    public synchronized void offlineApp(Long appId) {
        DeployedApp app = apps.remove(appId);
        if (app == null) {
            return;
        }
        app.close();
        log.info("网关应用 [{}] 已卸载，端口 {} 释放", app.getAppOption().getName(), app.getAppOption().getPort());
    }

    /**
     * 重置应用（配置变更后先卸载再按最新配置部署）
     */
    public synchronized void resetApp(Long appId) {
        offlineApp(appId);
        dataService.loadApps(List.of(appId)).stream()
                .findFirst()
                .ifPresent(this::onlineApp);
    }

    /**
     * 上线API集合
     */
    public synchronized void onlineApis(List<GatewayApiOption> apis) {
        for (GatewayApiOption api : apis) {
            DeployedApp app = apps.get(api.getAppId());
            if (app == null) {
                log.warn("网关API [{}] 上线失败：所属应用未部署", api.getName());
                continue;
            }
            mountApi(app, api);
        }
    }

    /**
     * 下线API集合
     */
    public synchronized void offlineApis(List<Long> apiIds) {
        for (Long apiId : apiIds) {
            for (DeployedApp app : apps.values()) {
                if (app.removeApi(apiId)) {
                    log.info("网关API [{}] 已从应用 [{}] 卸载", apiId, app.getAppOption().getName());
                    break;
                }
            }
        }
    }

    /**
     * 重置API集合（先卸载后按最新配置重挂载）
     */
    public synchronized void resetApis(List<Long> apiIds) {
        List<GatewayApiOption> apis = dataService.loadApis(apiIds);
        for (GatewayApiOption api : apis) {
            DeployedApp app = apps.get(api.getAppId());
            if (app == null) {
                log.warn("网关API [{}] 重置失败：所属应用未部署", api.getName());
                continue;
            }
            mountApi(app, api);
        }
    }

    /**
     * 工作流重新发布：失效编译缓存并重挂载绑定该工作流的在线API
     */
    public synchronized void onWorkflowPublished(Long workflowId) {
        RunWorkflowCache.remove(String.valueOf(workflowId));
        List<Long> apiIds = dataService.onlineApiIdsBoundToWorkflow(workflowId);
        if (!apiIds.isEmpty()) {
            log.info("工作流 [{}] 重新发布，重挂载绑定的在线API: {}", workflowId, apiIds);
            resetApis(apiIds);
        }
    }

    /**
     * 工作流被删除：失效编译缓存并下线绑定该工作流的API
     */
    public synchronized void onWorkflowRemoved(Long workflowId) {
        RunWorkflowCache.remove(String.valueOf(workflowId));
        List<Long> apiIds = dataService.onlineApiIdsBoundToWorkflow(workflowId);
        if (!apiIds.isEmpty()) {
            log.warn("工作流 [{}] 已删除，下线绑定的API: {}", workflowId, apiIds);
            offlineApis(apiIds);
        }
    }

    private void mountApi(DeployedApp app, GatewayApiOption api) {
        try {
            app.mountApi(api);
            log.info("网关API [{} {} {}] 已挂载到应用 [{}]",
                    api.getMethod(), api.getPath(), api.getName(), app.getAppOption().getName());
        } catch (Exception e) {
            log.error("网关API [{}] 挂载失败: {}", api.getName(), e.getMessage(), e);
        }
    }

    @PreDestroy
    public void shutdown() {
        apps.values().forEach(DeployedApp::close);
        apps.clear();
    }
}
