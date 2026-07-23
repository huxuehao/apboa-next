package com.hxh.apboa.longterm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hxh.apboa.common.cluster.core.MessagePublisher;
import com.hxh.apboa.common.consts.RedisChannelTopic;
import com.hxh.apboa.common.consts.TableConst;
import com.hxh.apboa.common.entity.AgentDefinition;
import com.hxh.apboa.common.entity.AgentLongTermMemory;
import com.hxh.apboa.common.entity.LongTermMemoryConfig;
import com.hxh.apboa.common.util.TenantUtils;
import com.hxh.apboa.longterm.mapper.LongTermMemoryConfigMapper;
import com.hxh.apboa.longterm.service.AgentLongTermMemoryService;
import com.hxh.apboa.longterm.service.LongTermMemoryConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 描述：LongTermMemoryConfigServiceImpl
 *
 * @author huxuehao
 **/
@Service
@RequiredArgsConstructor
public class LongTermMemoryConfigServiceImpl extends ServiceImpl<LongTermMemoryConfigMapper, LongTermMemoryConfig> implements LongTermMemoryConfigService {
    private final JdbcTemplate jdbcTemplate;
    private final AgentLongTermMemoryService agentLongTermMemoryService;
    private final MessagePublisher messagePublisher;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteByIds(List<Long> ids) {
        // 删除前先获取关联的智能体ID,以便后续触发重新注册
        List<Long> agentIds = agentLongTermMemoryService.getAgentIds(ids);
        boolean result = removeByIds(ids);
        // 级联清理关联(此前 controller 裸删漏了这步,agent_long_term_memory 留悬空引用)
        agentLongTermMemoryService.remove(new LambdaQueryWrapper<AgentLongTermMemory>().in(AgentLongTermMemory::getLongTermMemoryConfigId, ids));
        publishAgentReregister(agentIds);
        return result;
    }

    private void publishAgentReregister(List<Long> agentIds) {
        agentIds.forEach(agentId ->
                messagePublisher.publishAfterCommit(RedisChannelTopic.AGENT_REREGISTER_CHANNEL, String.valueOf(agentId)));
    }

    @Override
    public List<Object> usedWithAgent(List<Long> ids) {
        List<Object> names = new ArrayList<>();
        getAgentDefinitions(agentLongTermMemoryService.getAgentIds(ids)).forEach(agentDefinition -> {
            names.add(agentDefinition.getName());
        });

        return names;
    }

    private List<AgentDefinition> getAgentDefinitions(List<Long> agentIds) {
        if (agentIds == null || agentIds.isEmpty()) {
            return new ArrayList<>();
        }

        String subSql = agentIds.stream().map(String::valueOf).collect(Collectors.joining(","));

        String sql = String.format("SELECT * FROM %s WHERE id IN (%s)", TableConst.AGENT, subSql);
        List<Object> params = new ArrayList<>();
        // 添加租户过滤（JdbcTemplate 绕过 MyBatis-Plus 拦截器）
        if (TenantUtils.getCurrentTenantId() != null) {
            sql += " AND tenant_id = ?";
            params.add(TenantUtils.getCurrentTenantId());
        }
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            AgentDefinition agent = new AgentDefinition();
            agent.setId(rs.getLong("id"));
            agent.setName(rs.getString("name"));
            agent.setDescription(rs.getString("description"));
            return agent;
        }, params.toArray());
    }
}
