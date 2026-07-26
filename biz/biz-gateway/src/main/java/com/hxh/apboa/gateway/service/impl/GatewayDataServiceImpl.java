package com.hxh.apboa.gateway.service.impl;

import com.hxh.apboa.gateway.entity.GatewayApi;
import com.hxh.apboa.gateway.entity.GatewayApiWorkflow;
import com.hxh.apboa.gateway.entity.GatewayApp;
import com.hxh.apboa.gateway.entity.GatewayClient;
import com.hxh.apboa.gateway.entity.GatewayClientApi;
import com.hxh.apboa.gateway.mapper.GatewayApiMapper;
import com.hxh.apboa.gateway.mapper.GatewayApiWorkflowMapper;
import com.hxh.apboa.gateway.mapper.GatewayAppMapper;
import com.hxh.apboa.gateway.mapper.GatewayClientApiMapper;
import com.hxh.apboa.gateway.mapper.GatewayClientMapper;
import com.hxh.apboa.gateway.option.GatewayApiOption;
import com.hxh.apboa.gateway.option.GatewayAppOption;
import com.hxh.apboa.gateway.option.GatewayClientOption;
import com.hxh.apboa.gateway.service.GatewayDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 描述：网关数据面装配服务实现
 * 全部走IgnoreTenant查询，网关运行时无登录上下文
 *
 * @author huxuehao
 **/
@Service
@RequiredArgsConstructor
public class GatewayDataServiceImpl implements GatewayDataService {
    private final GatewayAppMapper appMapper;
    private final GatewayApiMapper apiMapper;
    private final GatewayApiWorkflowMapper apiWorkflowMapper;
    private final GatewayClientMapper clientMapper;
    private final GatewayClientApiMapper clientApiMapper;

    @Override
    public List<GatewayAppOption> loadOnlineApps() {
        return appMapper.selectOnlineIgnoreTenant().stream().map(this::toOption).toList();
    }

    @Override
    public List<GatewayAppOption> loadApps(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return appMapper.selectByIdsIgnoreTenant(ids).stream().map(this::toOption).toList();
    }

    @Override
    public List<GatewayApiOption> loadOnlineApis(Long appId) {
        return assembleApis(apiMapper.selectOnlineByAppIgnoreTenant(appId));
    }

    @Override
    public List<GatewayApiOption> loadApis(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return assembleApis(apiMapper.selectByIdsIgnoreTenant(ids));
    }

    @Override
    public List<GatewayClientOption> loadAllClients() {
        return assembleClients(clientMapper.selectAllIgnoreTenant());
    }

    @Override
    public List<GatewayClientOption> loadClients(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return assembleClients(clientMapper.selectByIdsIgnoreTenant(ids));
    }

    @Override
    public List<Long> onlineApiIdsBoundToWorkflow(Long workflowId) {
        List<Long> apiIds = apiWorkflowMapper.selectByWorkflowIgnoreTenant(workflowId)
                .stream().map(GatewayApiWorkflow::getApiId).toList();
        if (apiIds.isEmpty()) {
            return List.of();
        }
        return apiMapper.selectByIdsIgnoreTenant(apiIds).stream()
                .filter(api -> api.getOnline() != null && api.getOnline() == 1)
                .map(GatewayApi::getId)
                .toList();
    }

    private GatewayAppOption toOption(GatewayApp app) {
        return new GatewayAppOption(app.getId(), app.getTenantId(), app.getName(), app.getPort(), app.obtainConfig());
    }

    /**
     * 装配API运行时选项（附带工作流绑定关系）
     */
    private List<GatewayApiOption> assembleApis(List<GatewayApi> apis) {
        if (apis == null || apis.isEmpty()) {
            return List.of();
        }
        List<Long> apiIds = apis.stream().map(GatewayApi::getId).toList();
        Map<Long, Long> workflowIdMap = apiWorkflowMapper.selectByApiIdsIgnoreTenant(apiIds).stream()
                .collect(Collectors.toMap(GatewayApiWorkflow::getApiId, GatewayApiWorkflow::getWorkflowId, (a, b) -> a));
        return apis.stream().map(api -> new GatewayApiOption(
                api.getId(), api.getAppId(), api.getTenantId(), api.getName(), api.getMethod(),
                api.getPath(), workflowIdMap.get(api.getId()), api.getCreatedBy(), api.obtainConfig()
        )).toList();
    }

    /**
     * 装配客户端运行时选项（附带API授权集合）
     */
    private List<GatewayClientOption> assembleClients(List<GatewayClient> clients) {
        if (clients == null || clients.isEmpty()) {
            return List.of();
        }
        List<Long> clientIds = clients.stream().map(GatewayClient::getId).toList();
        Map<Long, Set<Long>> authMap = clientApiMapper.selectByClientIdsIgnoreTenant(clientIds).stream()
                .collect(Collectors.groupingBy(GatewayClientApi::getClientId,
                        Collectors.mapping(GatewayClientApi::getApiId, Collectors.toSet())));
        return clients.stream().map(client -> {
            GatewayClientOption option = new GatewayClientOption();
            option.setId(client.getId());
            option.setTenantId(client.getTenantId());
            option.setCode(client.getCode());
            option.setName(client.getName());
            option.setExpireAt(client.getExpireAt() == null
                    ? null
                    : client.getExpireAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
            option.setTokenSecret(client.getTokenSecret());
            option.setTokenTtl(client.getTokenTtl());
            option.setOnline(client.getOnline());
            option.setApiIds(authMap.getOrDefault(client.getId(), new HashSet<>()));
            return option;
        }).toList();
    }
}
