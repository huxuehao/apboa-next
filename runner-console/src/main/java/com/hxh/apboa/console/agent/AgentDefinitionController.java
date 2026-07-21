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

        return R.data(vo);
    }

    /**
     * 新增
     */
    @PostMapping
    @RoleNeed({TenantRole.TENANT_ADMIN, TenantRole.TENANT_EDITOR})
    public R<Boolean> save(@RequestBody AgentDefinitionVO vo) {
        agentDefinitionService.saveAgentDefinition(vo);
        messagePublisher.publishAfterCommit(RedisChannelTopic.AGENT_REREGISTER_CHANNEL, String.valueOf(vo.getId()));
        return R.data(true);
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
