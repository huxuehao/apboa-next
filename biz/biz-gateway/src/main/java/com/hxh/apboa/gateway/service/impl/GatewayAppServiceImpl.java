package com.hxh.apboa.gateway.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hxh.apboa.gateway.cluster.GatewaySyncPublisher;
import com.hxh.apboa.gateway.cluster.GatewaySyncType;
import com.hxh.apboa.gateway.entity.GatewayApi;
import com.hxh.apboa.gateway.entity.GatewayApp;
import com.hxh.apboa.gateway.mapper.GatewayApiMapper;
import com.hxh.apboa.gateway.mapper.GatewayAppMapper;
import com.hxh.apboa.gateway.service.GatewayAppService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 描述：网关应用服务实现
 *
 * @author huxuehao
 **/
@Service
@RequiredArgsConstructor
public class GatewayAppServiceImpl extends ServiceImpl<GatewayAppMapper, GatewayApp> implements GatewayAppService {
    private final GatewayApiMapper gatewayApiMapper;
    private final GatewaySyncPublisher syncPublisher;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean saveApp(GatewayApp app) {
        validateApp(app);
        app.setId(null);
        app.setProtocol("HTTP");
        app.setOnline(0);
        try {
            return save(app);
        } catch (DuplicateKeyException e) {
            throw new RuntimeException("端口 " + app.getPort() + " 已被其他应用占用");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateApp(GatewayApp app) {
        GatewayApp exists = getById(app.getId());
        if (exists == null) {
            throw new RuntimeException("网关应用不存在");
        }
        validateApp(app);
        if (exists.getOnline() != null && exists.getOnline() == 1 && !exists.getPort().equals(app.getPort())) {
            throw new RuntimeException("应用在线时不允许修改端口，请先下线");
        }
        boolean updated;
        try {
            updated = lambdaUpdate()
                    .eq(GatewayApp::getId, app.getId())
                    .set(GatewayApp::getName, app.getName())
                    .set(GatewayApp::getRemark, app.getRemark())
                    .set(GatewayApp::getPort, app.getPort())
                    .set(GatewayApp::getConfig, app.getConfig() == null
                            ? null
                            : com.hxh.apboa.common.util.JsonUtils.toJsonStr(app.getConfig()))
                    .update();
        } catch (DuplicateKeyException e) {
            throw new RuntimeException("端口 " + app.getPort() + " 已被其他应用占用");
        }
        // 在线应用配置变更后广播重置，各节点重新部署该应用
        if (updated && exists.getOnline() != null && exists.getOnline() == 1) {
            syncPublisher.publish(GatewaySyncType.APP_RESET, List.of(app.getId()));
        }
        return updated;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteApps(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return true;
        }
        Long apiCount = gatewayApiMapper.selectCount(new LambdaQueryWrapper<GatewayApi>().in(GatewayApi::getAppId, ids));
        if (apiCount != null && apiCount > 0) {
            throw new RuntimeException("应用下存在API定义，请先删除API");
        }
        List<Long> onlineIds = lambdaQuery().in(GatewayApp::getId, ids).eq(GatewayApp::getOnline, 1)
                .list().stream().map(GatewayApp::getId).toList();
        boolean removed = removeByIds(ids);
        if (removed && !onlineIds.isEmpty()) {
            syncPublisher.publish(GatewaySyncType.APP_OFFLINE, onlineIds);
        }
        return removed;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateOnline(Long id, Integer v) {
        GatewayApp app = getById(id);
        if (app == null) {
            throw new RuntimeException("网关应用不存在");
        }
        boolean online = Integer.valueOf(1).equals(v);
        boolean updated = lambdaUpdate()
                .eq(GatewayApp::getId, id)
                .set(GatewayApp::getOnline, online ? 1 : 0)
                .update();
        if (!updated) {
            return false;
        }
        if (online) {
            syncPublisher.publish(GatewaySyncType.APP_ONLINE, List.of(id));
        } else {
            // 应用下线时级联下线其下所有在线API，数据面卸载应用时会一并卸载路由
            gatewayApiMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<GatewayApi>()
                    .eq(GatewayApi::getAppId, id)
                    .eq(GatewayApi::getOnline, 1)
                    .set(GatewayApi::getOnline, 0));
            syncPublisher.publish(GatewaySyncType.APP_OFFLINE, List.of(id));
        }
        return true;
    }

    /**
     * 应用基础字段校验
     */
    private void validateApp(GatewayApp app) {
        if (app.getName() == null || app.getName().isBlank()) {
            throw new RuntimeException("应用名称不能为空");
        }
        if (app.getPort() == null || app.getPort() < 1024 || app.getPort() > 65535) {
            throw new RuntimeException("端口必须在 1024-65535 之间");
        }
    }
}
