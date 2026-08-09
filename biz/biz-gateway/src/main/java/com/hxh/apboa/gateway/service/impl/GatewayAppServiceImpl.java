package com.hxh.apboa.gateway.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hxh.apboa.common.util.JsonUtils;
import com.hxh.apboa.gateway.cluster.GatewaySyncPublisher;
import com.hxh.apboa.gateway.cluster.GatewaySyncType;
import com.hxh.apboa.gateway.entity.GatewayApi;
import com.hxh.apboa.gateway.entity.GatewayApp;
import com.hxh.apboa.gateway.mapper.GatewayApiMapper;
import com.hxh.apboa.gateway.mapper.GatewayAppMapper;
import com.hxh.apboa.gateway.option.GatewayAppConfig;
import com.hxh.apboa.gateway.option.GatewayAppWhitelistItem;
import com.hxh.apboa.gateway.service.GatewayAppService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 描述：网关应用服务实现
 *
 * @author huxuehao
 **/
@Service
@RequiredArgsConstructor
public class GatewayAppServiceImpl extends ServiceImpl<GatewayAppMapper, GatewayApp> implements GatewayAppService {
    /** Docker 单机部署中由平台服务和中间件占用的宿主机端口 */
    private static final Set<Integer> RESERVED_HOST_PORTS = Set.of(
            3060, 3061, 3062, 3064,
            3306, 5432, 6379
    );
    private static final Pattern IPV4_PATTERN =
            Pattern.compile("^((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)$");

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
                            : JsonUtils.toJsonStr(app.getConfig()))
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
            gatewayApiMapper.update(null, new LambdaUpdateWrapper<GatewayApi>()
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
        if (RESERVED_HOST_PORTS.contains(app.getPort())) {
            throw new RuntimeException("端口 " + app.getPort() + " 已被平台服务或中间件保留");
        }
        validateWhitelist(app.obtainConfig());
    }

    /**
     * 白名单校验：IP必填且格式合法、不允许重复、描述长度受限
     */
    private void validateWhitelist(GatewayAppConfig config) {
        List<GatewayAppWhitelistItem> whitelist = config.getWhitelist();
        if (whitelist == null || whitelist.isEmpty()) {
            return;
        }
        Set<String> seen = new HashSet<>();
        for (GatewayAppWhitelistItem item : whitelist) {
            String ip = item.getIp() == null ? "" : item.getIp().trim();
            if (ip.isEmpty()) {
                throw new RuntimeException("白名单IP不能为空");
            }
            if (!isValidIp(ip)) {
                throw new RuntimeException("白名单IP格式不合法：" + ip);
            }
            // 用归一化值去重，避免IPv6缩写与完整展开形式绕过重复检测
            if (!seen.add(normalizeIp(ip))) {
                throw new RuntimeException("白名单IP重复：" + ip);
            }
            if (item.getRemark() != null && item.getRemark().length() > 200) {
                throw new RuntimeException("白名单描述长度不能超过200字符");
            }
        }
    }

    /**
     * IP格式校验：IPv4走正则，含冒号视为IPv6字面量由InetAddress解析校验
     */
    private boolean isValidIp(String ip) {
        if (ip.contains(":")) {
            try {
                InetAddress.getByName(ip);
                return true;
            } catch (UnknownHostException e) {
                return false;
            }
        }
        return IPV4_PATTERN.matcher(ip).matches();
    }

    /**
     * IP归一化，与数据面WhitelistHandler的匹配口径保持一致
     */
    private String normalizeIp(String ip) {
        try {
            return InetAddress.getByName(ip).getHostAddress();
        } catch (UnknownHostException e) {
            return ip;
        }
    }
}
