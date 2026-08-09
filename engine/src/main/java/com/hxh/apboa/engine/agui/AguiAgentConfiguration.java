package com.hxh.apboa.engine.agui;

import com.hxh.apboa.engine.agent.IAgentFactory;
import io.agentscope.core.agui.registry.AguiAgentRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 描述：AGUI智能体注册
 *
 * @author huxuehao
 **/
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "runtime", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AguiAgentConfiguration implements ApplicationRunner {
    private final IAgentFactory iAgentFactory;
    private final AguiAgentRegistry registry;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        log.info("Re-registering all agents...");
        reRegisterAgent(null);
        log.info("Re-registering all agents completed.");
    }

    /**
     * 重新注册智能体
     * @param agentId 智能体定义，为空则重新注册所有智能体
     */
    public void reRegisterAgent(Long agentId) {
        if (registry == null) return;

        try {
            // 使用 jdbcTemplate 绕过租户拦截器（启动时无租户上下文）
            String sql = "SELECT id, agent_code, tenant_id FROM agent_definition WHERE enabled = true";
            if (agentId != null) {
                sql += " AND id = " + agentId;
            }
            jdbcTemplate.query(sql, (rs) -> {
                Long id = rs.getLong("id");
                String agentCode = rs.getString("agent_code");
                Long tenantId = rs.getLong("tenant_id");
                // 注销已注册的智能体
                unregisterAgent(agentCode);
                // 注册智能体
                registry.registerFactory(agentCode,
                        () -> iAgentFactory.getAgent(id, tenantId));
            });
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    /**
     * 注销单个智能体注册
     *
     * @param agentCode 智能体Code
     */
    public void unregisterAgent(String agentCode) {
        try {
            registry.unregister(agentCode);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }
}
