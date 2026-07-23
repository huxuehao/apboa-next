package com.hxh.apboa.engine.log.telemetry;

import com.fasterxml.jackson.databind.JsonNode;
import com.hxh.apboa.common.entity.ChatMessage;
import com.hxh.apboa.common.entity.ChatUsageRecord;
import com.hxh.apboa.common.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 成本流水落库：assistant 正文消息入库后，解析其 run 级 meta（token 用量与模型标识，
 * 字段契约见 {@link RunStatAccumulator} 与 ChatLogHook#buildMeta），按模型当时单价
 * 算成本快照写 chat_usage_record 一条（价格/模型名冗余快照，后续调价删模型不影响历史账）。
 *
 * <p>未配价（单价任一为 NULL）只记 token、cost 置 NULL，统计侧提示补配后可重算；
 * meta 缺 modelConfigId（异常路径）跳过并告警。与消息落库同在日志消费线程串行执行，
 * 写入失败只告警不影响消息本身。
 *
 * @author huxuehao
 */
@Slf4j
@Component
public class UsageRecordWriter {

    private static final BigDecimal TOKENS_PER_PRICE_UNIT = BigDecimal.valueOf(1_000_000);

    private final JdbcTemplate jdbcTemplate;

    public UsageRecordWriter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 对话 run 记账入口：非 assistant / 无 meta 的消息静默跳过。
     *
     * @param message   刚入库的消息（含 sessionId/tenantId/meta）
     * @param messageId 消息自增ID（审计钻取回链）
     */
    public void writeChatRun(ChatMessage message, int messageId) {
        try {
            if (!"assistant".equals(message.getRole()) || message.getMeta() == null) {
                return;
            }
            JsonNode meta = JsonUtils.parse(message.getMeta());
            if (meta == null || !meta.hasNonNull("modelConfigId")) {
                log.warn("成本流水跳过：消息 {} 的 meta 缺 modelConfigId", messageId);
                return;
            }
            long modelConfigId = meta.get("modelConfigId").asLong();
            String modelLabel = meta.hasNonNull("modelLabel") ? meta.get("modelLabel").asText() : "unknown";
            long inputTokens = meta.path("inputTokens").asLong(0);
            long outputTokens = meta.path("outputTokens").asLong(0);
            int iterationCount = meta.path("iterationCount").asInt(1);
            Long durationMs = meta.hasNonNull("durationMs") ? meta.get("durationMs").asLong() : null;
            String channel = meta.hasNonNull("channel") ? meta.get("channel").asText() : null;

            SessionOwner owner = jdbcTemplate.query(
                    "SELECT s.agent_id, s.user_id, a.name AS agent_name FROM chat_session s "
                            + "LEFT JOIN agent_definition a ON a.id = s.agent_id WHERE s.id = ?",
                    rs -> rs.next() ? new SessionOwner(rs.getLong("agent_id"), rs.getLong("user_id"), rs.getString("agent_name")) : null,
                    message.getSessionId()
            );
            if (owner == null) {
                return;
            }

            // 模型价格快照（模型刚好被删则整体为空：token 照记、cost 为 NULL）
            PriceInfo price = queryPrice(modelConfigId);

            BigDecimal cost = computeCost(inputTokens, outputTokens, price.inputPrice(), price.outputPrice());

            jdbcTemplate.update(
                    "INSERT INTO chat_usage_record (tenant_id, session_id, message_id, agent_id, agent_label, user_id, "
                            + "model_config_id, model_label, provider_type, biz_type, channel, "
                            + "input_tokens, output_tokens, iteration_count, duration_ms, "
                            + "input_price, output_price, cost, created_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    message.getTenantId(), message.getSessionId(), messageId, owner.agentId(), owner.agentName(), owner.userId(),
                    modelConfigId, modelLabel, price.providerType(), "CHAT", channel,
                    inputTokens, outputTokens, iterationCount, durationMs,
                    // 时间取应用侧（与 chat_message.created_at 同源同时区）：容器 mysql 常为 UTC，
                    // 用 SQL NOW() 会与消息时间差 8 小时，按天统计错位
                    price.inputPrice(), price.outputPrice(), cost, java.time.LocalDateTime.now()
            );
        } catch (Exception ex) {
            log.error("成本流水写入失败（消息 {} 已正常落库）: {}", messageId, ex.getMessage());
        }
    }

    /**
     * 通用记账（子智能体/workflow 等挂在会话下的非主链消耗）：tenant/user 从会话查，
     * 异步执行不阻塞调用方（Hook 事件线程）；无消息可挂（message_id NULL），
     * 模型标识缺失时记 0/unknown 保证 token 不丢。
     *
     * @param agentDbId 记账主体智能体（子智能体记子的 id）；null 回落会话的主智能体
     */
    public void writeSessionRun(String bizType, Long sessionId, Long agentDbId,
                                Long modelConfigId, String modelLabel, String channel,
                                long inputTokens, long outputTokens, int iterationCount, Long durationMs) {
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                SessionInfo session = jdbcTemplate.query(
                        "SELECT tenant_id, agent_id, user_id FROM chat_session WHERE id = ?",
                        rs -> rs.next()
                                ? new SessionInfo(rs.getLong("tenant_id"), rs.getLong("agent_id"), rs.getLong("user_id"))
                                : null,
                        sessionId
                );
                if (session == null) {
                    return;
                }
                long effectiveModelId = modelConfigId != null ? modelConfigId : 0L;
                long effectiveAgentId = agentDbId != null ? agentDbId : session.agentId();
                String agentLabel = jdbcTemplate.query(
                        "SELECT name FROM agent_definition WHERE id = ?",
                        rs -> rs.next() ? rs.getString("name") : null,
                        effectiveAgentId
                );
                PriceInfo price = effectiveModelId > 0 ? queryPrice(effectiveModelId) : new PriceInfo(null, null, null);
                BigDecimal cost = computeCost(inputTokens, outputTokens, price.inputPrice(), price.outputPrice());

                jdbcTemplate.update(
                        "INSERT INTO chat_usage_record (tenant_id, session_id, message_id, agent_id, agent_label, user_id, "
                                + "model_config_id, model_label, provider_type, biz_type, channel, "
                                + "input_tokens, output_tokens, iteration_count, duration_ms, "
                                + "input_price, output_price, cost, created_at) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        session.tenantId(), sessionId, null,
                        effectiveAgentId, agentLabel, session.userId(),
                        effectiveModelId, modelLabel != null ? modelLabel : "unknown", price.providerType(),
                        bizType, channel,
                        inputTokens, outputTokens, iterationCount, durationMs,
                        price.inputPrice(), price.outputPrice(), cost, java.time.LocalDateTime.now()
                );
            } catch (Exception ex) {
                log.error("成本流水写入失败 bizType={} sessionId={}: {}", bizType, sessionId, ex.getMessage());
            }
        });
    }

    /**
     * 工作流智能体节点记账（biz_type=WORKFLOW）：workflow 可独立运行、无会话可查租户归属，
     * tenant 由执行上下文显式传入，session_id/message_id 置 NULL。异步执行不阻塞节点返回。
     *
     * <p>workflowInstanceId 是 workflow_run 表主键（不是 chat_session id，绝不能写 session_id 列），
     * 仅用于日志定位；工作流名由调用方经变量上下文取得后组装进 agentLabel 传入——run 行在
     * 外层事务内未提交，此处按 instanceId 反查 workflow_run 是读不到的。
     *
     * @param agentId    对话内触发时的主智能体 DB id（消耗计入其名下，随之受其月预算管控）；
     *                   独立/调试运行为 null → agent_id=0
     * @param agentLabel 归属快照：对话内为主智能体名，独立运行为「工作流：xxx」
     * @param userId     发起人（独立运行为登录用户；对话内匿名会话合法为 null）
     */
    public void writeWorkflowRun(Long tenantId, String workflowInstanceId,
                                 Long agentId, String agentLabel, Long userId,
                                 Long modelConfigId, String channel,
                                 long inputTokens, long outputTokens, int iterationCount, Long durationMs) {
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                if (tenantId == null) {
                    log.warn("工作流成本流水跳过：实例 {} 无租户上下文", workflowInstanceId);
                    return;
                }
                long effectiveModelId = modelConfigId != null ? modelConfigId : 0L;
                // 模型名快照与主链 activeModelLabel 同源（model_config.name）
                String modelLabel = effectiveModelId > 0
                        ? jdbcTemplate.query("SELECT name FROM model_config WHERE id = ?",
                                rs -> rs.next() ? rs.getString("name") : null, effectiveModelId)
                        : null;
                long effectiveAgentId = agentId != null ? agentId : 0L;
                PriceInfo price = effectiveModelId > 0 ? queryPrice(effectiveModelId) : new PriceInfo(null, null, null);
                BigDecimal cost = computeCost(inputTokens, outputTokens, price.inputPrice(), price.outputPrice());

                jdbcTemplate.update(
                        "INSERT INTO chat_usage_record (tenant_id, session_id, message_id, agent_id, agent_label, user_id, "
                                + "model_config_id, model_label, provider_type, biz_type, channel, "
                                + "input_tokens, output_tokens, iteration_count, duration_ms, "
                                + "input_price, output_price, cost, created_at) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        tenantId, null, null, effectiveAgentId, agentLabel, userId,
                        effectiveModelId, modelLabel != null ? modelLabel : "unknown", price.providerType(),
                        ChatUsageRecord.BIZ_WORKFLOW, channel,
                        inputTokens, outputTokens, iterationCount, durationMs,
                        price.inputPrice(), price.outputPrice(), cost, java.time.LocalDateTime.now()
                );
            } catch (Exception ex) {
                log.error("工作流成本流水写入失败 instanceId={}: {}", workflowInstanceId, ex.getMessage());
            }
        });
    }

    /** 模型价格/供应商快照查询（模型已删则整体为空：token 照记、cost 为 NULL） */
    private PriceInfo queryPrice(long modelConfigId) {
        return jdbcTemplate.query(
                "SELECT mc.input_price, mc.output_price, mp.type AS provider_type "
                        + "FROM model_config mc LEFT JOIN model_provider mp ON mc.provider_id = mp.id "
                        + "WHERE mc.id = ?",
                rs -> rs.next()
                        ? new PriceInfo(rs.getBigDecimal("input_price"), rs.getBigDecimal("output_price"), rs.getString("provider_type"))
                        : new PriceInfo(null, null, null),
                modelConfigId
        );
    }

    /**
     * 成本 = (输入token×输入单价 + 输出token×输出单价) / 100万，元，8 位小数；
     * 任一单价未配返回 NULL（未计价）。
     */
    static BigDecimal computeCost(long inputTokens, long outputTokens, BigDecimal inputPrice, BigDecimal outputPrice) {
        if (inputPrice == null || outputPrice == null) {
            return null;
        }
        return BigDecimal.valueOf(inputTokens).multiply(inputPrice)
                .add(BigDecimal.valueOf(outputTokens).multiply(outputPrice))
                .divide(TOKENS_PER_PRICE_UNIT, 8, RoundingMode.HALF_UP);
    }

    private record SessionOwner(Long agentId, Long userId, String agentName) {}

    private record SessionInfo(Long tenantId, Long agentId, Long userId) {}

    private record PriceInfo(BigDecimal inputPrice, BigDecimal outputPrice, String providerType) {}
}
