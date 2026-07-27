package com.hxh.apboa.gateway.core;

import com.hxh.apboa.common.UserDetail;
import com.hxh.apboa.common.util.TenantUtils;
import com.hxh.apboa.gateway.option.GatewayApiOption;
import com.hxh.apboa.node.base.request.ParamItem;
import com.hxh.apboa.workflowbiz.dto.WorkflowRunRequest;
import com.hxh.apboa.workflowbiz.service.WorkflowRunService;
import com.hxh.apboa.workflowbiz.vo.WorkflowRunResult;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 描述：工作流调用器
 * 在虚拟线程中同步执行工作流（数据库写入与节点执行均为阻塞操作），
 * 避免阻塞Vert.x事件循环；执行前恢复API归属租户的上下文
 *
 * @author huxuehao
 **/
@Component
@RequiredArgsConstructor
public class WorkflowInvoker {
    private final WorkflowRunService workflowRunService;
    private final TenantCodeRegistry tenantCodeRegistry;

    /** 虚拟线程执行器：每个请求独立虚拟线程，天然隔离租户上下文（ThreadLocal） */
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    /**
     * 异步执行API绑定的工作流（最新已发布版本）
     *
     * @param api      API运行时选项
     * @param params   转换后的工作流入参
     * @param authUser 鉴权通过的平台用户（免鉴权API为null，回退为API创建人身份）
     */
    public CompletableFuture<WorkflowRunResult> invoke(GatewayApiOption api, List<ParamItem> params, UserDetail authUser) {
        return CompletableFuture.supplyAsync(() -> {
            String tenantCode = tenantCodeRegistry.codeOf(api.getTenantId());
            // 恢复租户上下文，保证工作流内部的数据访问带租户隔离
            TenantUtils.setCurrentTenant(api.getTenantId(), tenantCode);
            try {
                // 鉴权API以凭证用户身份执行，与平台内直接运行工作流的体验一致
                UserDetail userDetail = authUser != null ? authUser : UserDetail.builder()
                        .id(api.getCreatedBy())
                        .name("gateway")
                        .username("gateway")
                        .tenantId(api.getTenantId())
                        .tenantCode(tenantCode)
                        .build();

                WorkflowRunRequest request = new WorkflowRunRequest();
                request.setParams(params);
                return workflowRunService.run(api.getWorkflowId(), request, userDetail);
            } finally {
                // 清理租户上下文
                TenantUtils.clear();
            }
        }, executor);
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
    }
}
