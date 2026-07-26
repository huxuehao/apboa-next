package com.hxh.apboa.gateway.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hxh.apboa.common.entity.Workflow;
import com.hxh.apboa.common.enums.workflow.WorkflowStatus;
import com.hxh.apboa.common.mp.support.MP;
import com.hxh.apboa.common.mp.support.PageParams;
import com.hxh.apboa.common.util.TenantUtils;
import com.hxh.apboa.gateway.cluster.GatewaySyncPublisher;
import com.hxh.apboa.gateway.cluster.GatewaySyncType;
import com.hxh.apboa.gateway.entity.GatewayApi;
import com.hxh.apboa.gateway.entity.GatewayApiWorkflow;
import com.hxh.apboa.gateway.entity.GatewayApp;
import com.hxh.apboa.gateway.entity.GatewayClientApi;
import com.hxh.apboa.gateway.enums.GatewayParamPosition;
import com.hxh.apboa.gateway.mapper.GatewayApiMapper;
import com.hxh.apboa.gateway.mapper.GatewayApiWorkflowMapper;
import com.hxh.apboa.gateway.mapper.GatewayAppMapper;
import com.hxh.apboa.gateway.mapper.GatewayClientApiMapper;
import com.hxh.apboa.gateway.option.GatewayApiConfig;
import com.hxh.apboa.gateway.option.GatewayApiParam;
import com.hxh.apboa.gateway.service.GatewayApiService;
import com.hxh.apboa.gateway.vo.GatewayApiVO;
import com.hxh.apboa.workflowbiz.mapper.WorkflowMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 描述：网关API服务实现
 *
 * @author huxuehao
 **/
@Service
@RequiredArgsConstructor
public class GatewayApiServiceImpl extends ServiceImpl<GatewayApiMapper, GatewayApi> implements GatewayApiService {
    /** 路径占位符提取（vertx风格 :param） */
    private static final Pattern PATH_PARAM_PATTERN = Pattern.compile(":([A-Za-z0-9]+)");
    /** 合法路径校验 */
    private static final Pattern PATH_PATTERN = Pattern.compile("^(/(:?[A-Za-z0-9._-]+))+/?$");

    private final GatewayAppMapper gatewayAppMapper;
    private final GatewayApiWorkflowMapper apiWorkflowMapper;
    private final GatewayClientApiMapper clientApiMapper;
    private final WorkflowMapper workflowMapper;
    private final GatewaySyncPublisher syncPublisher;

    @Override
    public IPage<GatewayApiVO> pageVO(GatewayApi query, PageParams pageParams) {
        IPage<GatewayApi> page = page(MP.getPage(pageParams), MP.getQueryWrapper(query));
        Page<GatewayApiVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(enrich(page.getRecords()));
        return result;
    }

    @Override
    public GatewayApiVO detail(Long id) {
        GatewayApi api = getById(id);
        if (api == null) {
            throw new RuntimeException("网关API不存在");
        }
        return enrich(List.of(api)).getFirst();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean saveApi(GatewayApiVO vo) {
        validateApi(vo, null);
        GatewayApi api = new GatewayApi();
        BeanUtils.copyProperties(vo, api);
        api.setId(null);
        api.setOnline(0);
        boolean saved = save(api);
        if (saved) {
            bindWorkflow(api, vo.getWorkflowId());
        }
        return saved;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateApi(GatewayApiVO vo) {
        GatewayApi exists = getById(vo.getId());
        if (exists == null) {
            throw new RuntimeException("网关API不存在");
        }
        if (exists.getOnline() != null && exists.getOnline() == 1 && !exists.getAppId().equals(vo.getAppId())) {
            throw new RuntimeException("API在线时不允许变更所属应用，请先下线");
        }
        validateApi(vo, vo.getId());

        GatewayApi api = new GatewayApi();
        BeanUtils.copyProperties(vo, api);
        boolean updated = updateById(api);
        if (updated) {
            // 重建工作流绑定关系
            apiWorkflowMapper.delete(new LambdaQueryWrapper<GatewayApiWorkflow>().eq(GatewayApiWorkflow::getApiId, api.getId()));
            bindWorkflow(exists, vo.getWorkflowId());
            // 在线API配置变更后广播重挂载
            if (exists.getOnline() != null && exists.getOnline() == 1) {
                syncPublisher.publish(GatewaySyncType.API_RESET, List.of(api.getId()));
            }
        }
        return updated;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteApis(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return true;
        }
        List<Long> onlineIds = lambdaQuery().in(GatewayApi::getId, ids).eq(GatewayApi::getOnline, 1)
                .list().stream().map(GatewayApi::getId).toList();
        boolean removed = removeByIds(ids);
        if (removed) {
            apiWorkflowMapper.delete(new LambdaQueryWrapper<GatewayApiWorkflow>().in(GatewayApiWorkflow::getApiId, ids));
            clientApiMapper.delete(new LambdaQueryWrapper<GatewayClientApi>().in(GatewayClientApi::getApiId, ids));
            if (!onlineIds.isEmpty()) {
                syncPublisher.publish(GatewaySyncType.API_OFFLINE, onlineIds);
            }
        }
        return removed;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateOnline(Long id, Integer v) {
        GatewayApi api = getById(id);
        if (api == null) {
            throw new RuntimeException("网关API不存在");
        }
        boolean online = Integer.valueOf(1).equals(v);
        if (online) {
            GatewayApp app = gatewayAppMapper.selectById(api.getAppId());
            if (app == null || app.getOnline() == null || app.getOnline() != 1) {
                throw new RuntimeException("所属应用未上线，请先上线应用");
            }
            requirePublishedWorkflow(boundWorkflowId(id));
        }
        boolean updated = lambdaUpdate()
                .eq(GatewayApi::getId, id)
                .set(GatewayApi::getOnline, online ? 1 : 0)
                .update();
        if (updated) {
            syncPublisher.publish(online ? GatewaySyncType.API_ONLINE : GatewaySyncType.API_OFFLINE, List.of(id));
        }
        return updated;
    }

    @Override
    public List<String> categories() {
        return baseMapper.selectCategories(TenantUtils.getCurrentTenantId());
    }

    @Override
    public List<GatewayApiVO> listBrief() {
        List<GatewayApi> apis = lambdaQuery()
                .select(GatewayApi::getId, GatewayApi::getAppId, GatewayApi::getName,
                        GatewayApi::getCategory, GatewayApi::getMethod, GatewayApi::getPath, GatewayApi::getOnline)
                .orderByDesc(GatewayApi::getCreatedAt)
                .list();
        return enrich(apis);
    }

    /**
     * 附加应用与工作流关联信息
     */
    private List<GatewayApiVO> enrich(List<GatewayApi> apis) {
        if (apis == null || apis.isEmpty()) {
            return List.of();
        }
        List<Long> apiIds = apis.stream().map(GatewayApi::getId).toList();
        List<Long> appIds = apis.stream().map(GatewayApi::getAppId).distinct().toList();

        Map<Long, GatewayApp> appMap = gatewayAppMapper.selectBatchIds(appIds).stream()
                .collect(Collectors.toMap(GatewayApp::getId, Function.identity()));
        Map<Long, Long> workflowIdMap = apiWorkflowMapper
                .selectList(new LambdaQueryWrapper<GatewayApiWorkflow>().in(GatewayApiWorkflow::getApiId, apiIds))
                .stream().collect(Collectors.toMap(GatewayApiWorkflow::getApiId, GatewayApiWorkflow::getWorkflowId, (a, b) -> a));
        Map<Long, Workflow> workflowMap = workflowIdMap.isEmpty()
                ? Map.of()
                : workflowMapper.selectBatchIds(workflowIdMap.values().stream().distinct().toList()).stream()
                        .collect(Collectors.toMap(Workflow::getId, Function.identity()));

        return apis.stream().map(api -> {
            GatewayApiVO vo = new GatewayApiVO();
            BeanUtils.copyProperties(api, vo);
            GatewayApp app = appMap.get(api.getAppId());
            if (app != null) {
                vo.setAppName(app.getName());
                vo.setAppPort(app.getPort());
            }
            Long workflowId = workflowIdMap.get(api.getId());
            vo.setWorkflowId(workflowId);
            Workflow workflow = workflowId == null ? null : workflowMap.get(workflowId);
            if (workflow != null) {
                vo.setWorkflowName(workflow.getName());
                vo.setWorkflowStatus(workflow.getStatus() == null ? null : workflow.getStatus().name());
            }
            return vo;
        }).toList();
    }

    /**
     * 保存工作流绑定关系
     */
    private void bindWorkflow(GatewayApi api, Long workflowId) {
        GatewayApiWorkflow binding = new GatewayApiWorkflow();
        binding.setApiId(api.getId());
        binding.setWorkflowId(workflowId);
        apiWorkflowMapper.insert(binding);
    }

    /**
     * 查询API绑定的工作流ID
     */
    private Long boundWorkflowId(Long apiId) {
        GatewayApiWorkflow binding = apiWorkflowMapper.selectOne(
                new LambdaQueryWrapper<GatewayApiWorkflow>().eq(GatewayApiWorkflow::getApiId, apiId).last("limit 1"));
        return binding == null ? null : binding.getWorkflowId();
    }

    /**
     * 校验工作流存在且已发布
     */
    private void requirePublishedWorkflow(Long workflowId) {
        if (workflowId == null) {
            throw new RuntimeException("API未绑定工作流");
        }
        Workflow workflow = workflowMapper.selectById(workflowId);
        if (workflow == null) {
            throw new RuntimeException("绑定的工作流不存在");
        }
        if (workflow.getStatus() != WorkflowStatus.PUBLISHED) {
            throw new RuntimeException("绑定的工作流尚未发布，请先发布工作流");
        }
    }

    /**
     * API完整性校验（基础字段、路径合法性、参数定义、路径唯一性、工作流有效性）
     */
    private void validateApi(GatewayApiVO vo, Long excludeId) {
        if (vo.getName() == null || vo.getName().isBlank()) {
            throw new RuntimeException("API名称不能为空");
        }
        if (vo.getAppId() == null) {
            throw new RuntimeException("请选择所属应用");
        }
        if (gatewayAppMapper.selectById(vo.getAppId()) == null) {
            throw new RuntimeException("所属应用不存在");
        }
        if (vo.getMethod() == null) {
            throw new RuntimeException("请求方法不能为空");
        }
        if (vo.getPath() == null || vo.getPath().isBlank()) {
            throw new RuntimeException("路由路径不能为空");
        }
        if (!PATH_PATTERN.matcher(vo.getPath()).matches()) {
            throw new RuntimeException("路由路径格式非法，示例：/order/:orderId/detail");
        }
        requirePublishedWorkflow(vo.getWorkflowId());
        validateParams(vo);
        validatePathUnique(vo, excludeId);
    }

    /**
     * 参数定义校验：参数名必填且同位置不重复，PATH参数必须存在于路径占位符中
     */
    private void validateParams(GatewayApiVO vo) {
        GatewayApiConfig config = vo.obtainConfig();
        List<GatewayApiParam> params = config.getParams();
        if (params == null || params.isEmpty()) {
            return;
        }
        Set<String> pathPlaceholders = extractPathParams(vo.getPath());
        Set<String> uniqueKeys = new HashSet<>();
        for (GatewayApiParam param : params) {
            if (param.getPosition() == null) {
                throw new RuntimeException("存在未指定位置的参数定义");
            }
            if (param.getKey() == null || param.getKey().isBlank()) {
                throw new RuntimeException("存在未命名的参数定义");
            }
            if (!uniqueKeys.add(param.getPosition() + "::" + param.getKey())) {
                throw new RuntimeException("参数定义重复：" + param.getKey());
            }
            if (param.getPosition() == GatewayParamPosition.PATH && !pathPlaceholders.contains(param.getKey())) {
                throw new RuntimeException("PATH参数 " + param.getKey() + " 未出现在路由路径中");
            }
        }
    }

    /**
     * 同应用下 method+path 唯一性校验
     */
    private void validatePathUnique(GatewayApiVO vo, Long excludeId) {
        List<GatewayApi> conflicts = lambdaQuery()
                .eq(GatewayApi::getAppId, vo.getAppId())
                .eq(GatewayApi::getPath, vo.getPath())
                .eq(GatewayApi::getMethod, vo.getMethod())
                .list();
        boolean conflicted = conflicts.stream().anyMatch(api -> !api.getId().equals(excludeId));
        if (conflicted) {
            throw new RuntimeException("同应用下已存在相同方法和路径的API");
        }
    }

    /**
     * 提取路径中的占位符参数名
     */
    private Set<String> extractPathParams(String path) {
        Set<String> names = new HashSet<>();
        Matcher matcher = PATH_PARAM_PATTERN.matcher(path);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }
}
