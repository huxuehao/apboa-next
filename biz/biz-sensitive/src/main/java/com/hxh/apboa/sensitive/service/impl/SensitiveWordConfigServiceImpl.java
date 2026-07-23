package com.hxh.apboa.sensitive.service.impl;

import com.hxh.apboa.common.cluster.core.MessagePublisher;
import com.hxh.apboa.common.consts.RedisChannelTopic;
import com.hxh.apboa.common.consts.TableConst;
import com.hxh.apboa.common.entity.AgentDefinition;
import com.hxh.apboa.common.entity.SensitiveWordConfig;
import com.hxh.apboa.common.util.TenantUtils;
import com.hxh.apboa.sensitive.mapper.SensitiveWordConfigMapper;
import com.hxh.apboa.sensitive.service.SensitiveWordConfigService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 敏感词配置Service实现
 *
 * @author huxuehao
 */
@Service
@RequiredArgsConstructor
public class SensitiveWordConfigServiceImpl extends ServiceImpl<SensitiveWordConfigMapper, SensitiveWordConfig> implements SensitiveWordConfigService {
    private final JdbcTemplate jdbcTemplate;
    private final MessagePublisher messagePublisher;

    @Override
    public List<Object> usedWithAgent(List<Long> ids) {
        ArrayList<Object> names = new ArrayList<>();
        getAgentDefinitions(ids).forEach(agentDefinition -> {
            names.add(agentDefinition.getName());
        });

        return names;
    }

    @Override
    public List<String> listCategories() {
        return this.lambdaQuery()
                .select(SensitiveWordConfig::getCategory)
                .isNotNull(SensitiveWordConfig::getCategory)
                .groupBy(SensitiveWordConfig::getCategory)
                .list()
                .stream()
                .map(SensitiveWordConfig::getCategory)
                .filter(category -> category != null && !category.isEmpty())
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteByIds(List<Long> ids) {
        // 删除前先获取关联的智能体ID，以便后续触发重新注册
        List<Long> agentIds = getAgentDefinitions(ids).stream().map(AgentDefinition::getId).toList();
        boolean result = removeByIds(ids);
        // 置空 agent_definition 直连引用列,否则列值悬空,detail 拿已删 id 查详情报"不存在"
        // (前端拉取敏感词详情的条件是 enabled && configId,置空即不再触发;Long 列表拼接无注入风险)
        String idList = ids.stream().map(String::valueOf).collect(Collectors.joining(","));
        jdbcTemplate.update("UPDATE " + TableConst.AGENT
                + " SET sensitive_word_config_id = NULL WHERE sensitive_word_config_id IN (" + idList + ")");
        publishAgentReregister(agentIds);
        return result;
    }

    @Override
    public boolean doUpdate(SensitiveWordConfig entity) {
        boolean result = updateById(entity);
        List<Long> agentIds = getAgentDefinitions(List.of(entity.getId())).stream().map(AgentDefinition::getId).toList();
        publishAgentReregister(agentIds);
        return result;
    }

    private void publishAgentReregister(List<Long> agentIds) {
        agentIds.forEach(agentId ->
                messagePublisher.publishAfterCommit(RedisChannelTopic.AGENT_REREGISTER_CHANNEL, String.valueOf(agentId)));
    }

    private List<AgentDefinition> getAgentDefinitions(List<Long> systemPromptId) {
        if (systemPromptId == null || systemPromptId.isEmpty()) {
            return new ArrayList<>();
        }

        String subSql = systemPromptId.stream().map(String::valueOf).collect(Collectors.joining(","));

        String sql = String.format("SELECT * FROM %s WHERE sensitive_word_config_id IN (%s)", TableConst.AGENT, subSql);
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
