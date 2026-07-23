package com.hxh.apboa.console.agent;

import com.hxh.apboa.agent.service.AgentDefinitionService;
import com.hxh.apboa.common.cluster.core.MessagePublisher;
import com.hxh.apboa.common.config.auth.ChatKeyAccess;
import com.hxh.apboa.common.config.auth.RoleNeed;
import com.hxh.apboa.common.config.auth.SkAccess;
import com.hxh.apboa.common.consts.RedisChannelTopic;
import com.hxh.apboa.common.dto.AgentDefinitionDTO;
import com.hxh.apboa.common.entity.*;
import com.hxh.apboa.common.enums.TenantRole;
import com.hxh.apboa.common.mp.support.MP;
import com.hxh.apboa.common.mp.support.PageParams;
import com.hxh.apboa.common.r.R;
import com.hxh.apboa.common.util.BeanUtils;
import com.hxh.apboa.common.vo.AgentDefinitionVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hxh.apboa.studio.mapper.AgentStudioMapper;
import com.hxh.apboa.longterm.mapper.AgentLongTermMemoryMapper;
import com.hxh.apboa.common.enums.ModelProviderType;
import com.hxh.apboa.common.wrapper.ModelWrapper;
import com.hxh.apboa.model.service.ModelConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 智能体定义Controller
 *
 * @author huxuehao
 */
@RestController
@RequestMapping("/agent/definition")
@RequiredArgsConstructor
public class AgentDefinitionController {

    private final AgentDefinitionService agentDefinitionService;
    private final ModelConfigService modelConfigService;
    private final IJobInfoMapper iJobInfoMapper;
    private final AgentStudioMapper agentStudioMapper;
    private final AgentLongTermMemoryMapper agentLongTermMemoryMapper;
    private final MessagePublisher messagePublisher;

    /**
     * 分页查询
     */
    @GetMapping("/page")
    public R<IPage<AgentDefinitionVO>> page(PageParams pageParams, AgentDefinitionDTO query) {
        IPage<AgentDefinition> page = agentDefinitionService.page(MP.getPage(pageParams), MP.getQueryWrapper(query));
        IPage<AgentDefinitionVO> pageVo = BeanUtils.copyPage(page, AgentDefinitionVO.class);
        List<AgentStudio> agentStudios = agentStudioMapper.selectList(null);
        List<AgentLongTermMemory> agentLongTermMemories = agentLongTermMemoryMapper.selectList(null);
        if (!agentStudios.isEmpty() || !agentLongTermMemories.isEmpty()) {
            Map<Long, Long> agentStudioMap = agentStudios.stream().collect(Collectors.toMap(
                    AgentStudio::getAgentDefinitionId,
                    AgentStudio::getStudioId,
                    (existing, replacement) -> existing));
            Map<Long, Long> agentLongTermMemoryMap = agentLongTermMemories.stream().collect(Collectors.toMap(
                    AgentLongTermMemory::getAgentDefinitionId,
                    AgentLongTermMemory::getLongTermMemoryConfigId,
                    (existing, replacement) -> existing));
            pageVo.getRecords().forEach(agentVo -> {
                if (agentStudioMap.containsKey(agentVo.getId())) {
                    agentVo.setStudioConfigId(agentStudioMap.get(agentVo.getId()));
                }
                if (agentLongTermMemoryMap.containsKey(agentVo.getId())) {
                    agentVo.setLongTermMemoryConfigId(agentLongTermMemoryMap.get(agentVo.getId()));
                }
            });
        }

        return R.data(pageVo);
    }

    /**
     * 详情
     */
    @SkAccess
    @ChatKeyAccess
    @GetMapping("/{id}")
    public R<AgentDefinitionVO> detail(@PathVariable("id") Long id) {
        AgentDefinitionVO vo = agentDefinitionService.agentDefinitionDetail(id);
        vo.setUsed(agentDefinitionService.usedWithAgent(List.of(id)));
        vo.setThinkingSwitchSupported(resolveThinkingSwitchSupported(vo.getModelConfigId()));
        List<JobInfo> agent = iJobInfoMapper.selectList(
                new LambdaQueryWrapper<JobInfo>()
                        .eq(JobInfo::getType, "AGENT")
                        .eq(JobInfo::getBizId, String.valueOf(id)));
        if (agent.size() == 1) {
            vo.setJobInfo(agent.getFirst());
        }

        return R.data(vo);
    }

    /**
     * 当前模型是否支持会话级思考模式开关：仅 DASH_SCOPE 全链路支持
     * （enable_thinking 为百炼官方参数；其他供应商的思考参数在各自服务端不受控，
     * 见 ChatModelFactory 合成点）。查询失败按不支持处理（按钮宁缺勿误）。
     */
    private Boolean resolveThinkingSwitchSupported(Long modelConfigId) {
        if (modelConfigId == null) {
            return false;
        }
        try {
            ModelWrapper wrapper = modelConfigService.getModelWrapperById(modelConfigId);
            return wrapper != null && wrapper.getProvider() != null
                    && wrapper.getProvider().getType() == ModelProviderType.DASH_SCOPE;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 新增（返回回填了 id 的 VO，供前端串行保存头像等子资源）
     */
    @PostMapping
    @RoleNeed({TenantRole.TENANT_ADMIN, TenantRole.TENANT_EDITOR})
    public R<AgentDefinitionVO> save(@RequestBody AgentDefinitionVO vo) {
        agentDefinitionService.saveAgentDefinition(vo);
        messagePublisher.publishAfterCommit(RedisChannelTopic.AGENT_REREGISTER_CHANNEL, String.valueOf(vo.getId()));
        return R.data(vo);
    }

    /**
     * 修改
     */
    @PutMapping
    @RoleNeed({TenantRole.TENANT_ADMIN, TenantRole.TENANT_EDITOR})
    public R<Boolean> update(@RequestBody AgentDefinitionVO vo) {
        return R.data(agentDefinitionService.updateAgentDefinition(vo));
    }

    /**
     * 获取头像（独立于 VO：base64 不随列表/详情接口返回）
     */
    @SkAccess
    @ChatKeyAccess
    @GetMapping("/{id}/avatar")
    public R<String> getAvatar(@PathVariable("id") Long id) {
        return R.data(agentDefinitionService.getAvatar(id));
    }

    /**
     * 更新头像（body: {"avatar": base64 data URL}，空值清除）
     */
    @PutMapping("/{id}/avatar")
    @RoleNeed({TenantRole.TENANT_ADMIN, TenantRole.TENANT_EDITOR})
    public R<Boolean> updateAvatar(@PathVariable("id") Long id, @RequestBody Map<String, String> body) {
        return R.data(agentDefinitionService.updateAvatar(id, body.get("avatar")));
    }

    /**
     * 删除
     */
    @DeleteMapping
    @RoleNeed({TenantRole.TENANT_ADMIN, TenantRole.TENANT_EDITOR})
    public R<Boolean> delete(@RequestBody List<Long> ids) {
        return R.data(agentDefinitionService.deleteAgentDefinition(ids));
    }

    /**
     * 被哪些Agent使用
     */
    @PostMapping("used-with-agent")
    public R<List<Object>> usedWithAgent(@RequestBody List<Long> ids) {
        return R.data(agentDefinitionService.usedWithAgent(ids));
    }

    /**
     * 获取所有Tag
     */
    @GetMapping("/get/tags")
    public R<List<String>> listTags() {
        return R.data(agentDefinitionService.listTags());
    }

    @SkAccess
    @ChatKeyAccess
    @GetMapping("/{id}/allow/file-type")
    public R<List<String>> allowFileType(@PathVariable("id") Long id) {
        return R.data(agentDefinitionService.allowFileType(id));
    }

    /**
     * 获取Agent启用的工具
     */
    @SkAccess
    @ChatKeyAccess
    @GetMapping("/{agentId}/enabled/tools")
    public R<List<ToolConfig>> getEnabledToolsOfAgent(@PathVariable("agentId") Long agentId) {
        return R.data(agentDefinitionService.getEnabledToolsOfAgent(agentId));
    }

    /**
     * 获取Agent启用的技能包
     */
    @SkAccess
    @ChatKeyAccess
    @GetMapping("/{agentId}/enabled/skills")
    public R<List<SkillPackage>> getEnabledSkillsOfAgent(@PathVariable("agentId") Long agentId) {
        return R.data(agentDefinitionService.getEnabledSkillsOfAgent(agentId));
    }

    /**
     * 获取Agent启用的工作流
     */
    @SkAccess
    @ChatKeyAccess
    @GetMapping("/{agentId}/enabled/workflows")
    public R<List<Workflow>> getEnabledWorkflowsOfAgent(@PathVariable("agentId") Long agentId) {
        return R.data(agentDefinitionService.getEnabledWorkflowsOfAgent(agentId));
    }
}
