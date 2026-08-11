package com.hxh.apboa.skill.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hxh.apboa.common.cluster.core.MessagePublisher;
import com.hxh.apboa.common.consts.RedisChannelTopic;
import com.hxh.apboa.common.consts.TableConst;
import com.hxh.apboa.common.entity.AgentDefinition;
import com.hxh.apboa.common.entity.AgentSkillPackage;
import com.hxh.apboa.common.entity.SkillPackage;
import com.hxh.apboa.common.util.TenantUtils;
import com.hxh.apboa.common.vo.SkillPackageVO;
import com.hxh.apboa.skill.mapper.SkillPackageMapper;
import com.hxh.apboa.skill.service.AgentSkillPackageService;
import com.hxh.apboa.skill.service.SkillPackageService;
import com.hxh.apboa.skill.service.SkillToolService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 技能包Service实现
 *
 * @author huxuehao
 */
@Service
@RequiredArgsConstructor
public class SkillPackageServiceImpl extends ServiceImpl<SkillPackageMapper, SkillPackage> implements SkillPackageService {
    private final JdbcTemplate jdbcTemplate;
    private final AgentSkillPackageService agentSkillPackageService;
    private final SkillToolService skillToolService;
    private final MessagePublisher messagePublisher;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public List<Object> usedWithAgent(List<Long> ids) {
        List<Object> names = new ArrayList<>();
        getAgentDefinitions(agentSkillPackageService.getAgentIds(ids)).forEach(agentDefinition -> {
            names.add(agentDefinition.getName());
        });

        return names;
    }

    @Override
    public List<String> listCategories() {
        return this.lambdaQuery()
                .select(SkillPackage::getCategory)
                .isNotNull(SkillPackage::getCategory)
                .groupBy(SkillPackage::getCategory)
                .list()
                .stream()
                .map(SkillPackage::getCategory)
                .filter(category -> category != null && !category.isEmpty())
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteByIds(List<Long> ids) {
        // 删除前查询技能包信息，用于发布文件删除事件
        List<SkillPackage> skills = listByIds(ids);
        // 删除前先获取关联的智能体ID，以便后续触发重新注册
        List<Long> agentIds = agentSkillPackageService.getAgentIds(ids);
        removeByIds(ids);
        // 删除技能包与智能体的关联
        agentSkillPackageService.remove(new LambdaQueryWrapper<AgentSkillPackage>().in(AgentSkillPackage::getSkillPackageId, ids));
        // 删除技能包与工具的关联
        skillToolService.deleteSkillTool(ids);
        publishAgentReregister(agentIds);
        // 发布文件删除同步事件
        skills.forEach(skill -> publishSkillFileDelete(skill.getName(), skill.getTenantId()));
        return true;
    }

    @Override
    public boolean doUpdate(SkillPackage entity) {
        boolean result = updateById(entity);
        publishAgentReregister(agentSkillPackageService.getAgentIds(List.of(entity.getId())));
        return result;
    }

    @Override
    public boolean updateAlias(Long id, String alias) {
        // 仅更新别名，不触发智能体重新注册
        return update(new LambdaUpdateWrapper<SkillPackage>()
                .eq(SkillPackage::getId, id)
                .set(SkillPackage::getAlias, alias));
    }

    @Override
    public SkillPackageVO getDetail(Long id) {
        SkillPackage entity = getById(id);
        if (entity == null) {
            return null;
        }
        SkillPackageVO vo = new SkillPackageVO();
        vo.setId(entity.getId());
        vo.setName(entity.getName());
        vo.setAlias(entity.getAlias());
        vo.setDescription(entity.getDescription());
        vo.setCategory(entity.getCategory());
        vo.setEnabled(entity.getEnabled());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        vo.setCreatedBy(entity.getCreatedBy());
        vo.setUpdatedBy(entity.getUpdatedBy());
        // 查询关联的工具ID列表
        vo.setTools(skillToolService.getToolIds(id));
        return vo;
    }

    private void publishAgentReregister(List<Long> agentIds) {
        agentIds.forEach(agentId ->
                messagePublisher.publishAfterCommit(RedisChannelTopic.AGENT_REREGISTER_CHANNEL, String.valueOf(agentId)));
    }

    /**
     * 发布技能文件删除事件（action=delete）
     */
    private void publishSkillFileDelete(String skillName, Long tenantId) {
        String tenantCode = queryTenantCode(tenantId);
        if (tenantCode == null) {
            return;
        }
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("action", "delete");
        payload.put("skillName", skillName);
        payload.put("tenantCode", tenantCode);
        try {
            String json = objectMapper.writeValueAsString(payload);
            messagePublisher.publishAfterCommit(RedisChannelTopic.SKILL_FILE_SYNC_CHANNEL, json);
        } catch (Exception e) {
            // JSON 序列化失败（理论上不会发生）
        }
    }

    /**
     * 根据租户ID查询租户编码
     */
    private String queryTenantCode(Long tenantId) {
        if (tenantId == null) {
            return null;
        }
        List<String> codes = jdbcTemplate.queryForList(
                "SELECT code FROM tenant WHERE id = ?", String.class, tenantId);
        return codes.isEmpty() ? null : codes.get(0);
    }

    private List<AgentDefinition> getAgentDefinitions(List<Long> agentIds) {
        if (agentIds == null || agentIds.isEmpty()) {
            return new ArrayList<>();
        }

        String subSql = agentIds.stream().map(String::valueOf).collect(Collectors.joining(","));

        String sql = String.format("SELECT * FROM %s WHERE id IN (%s)", TableConst.AGENT, subSql);
        // 添加租户过滤（JdbcTemplate 绕过 MyBatis-Plus 拦截器）
        if (TenantUtils.getCurrentTenantId() != null) {
            sql += " AND tenant_id = " + TenantUtils.getCurrentTenantId();
        }
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            AgentDefinition agent = new AgentDefinition();
            // 手动映射字段
            agent.setId(rs.getLong("id"));
            agent.setName(rs.getString("name"));
            agent.setDescription(rs.getString("description"));
            return agent;
        });
    }
}
