package com.hxh.apboa.engine.log;

import com.hxh.apboa.common.subagent.SubAgentTraceEvent;
import com.hxh.apboa.common.subagent.SubAgentTraceEventType;
import com.hxh.apboa.common.util.JsonUtils;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 描述：子智能体卡片的持久化存储服务。卡片锚点写入 chat_message 后不再变动，
 *      密集的追踪事件独立存储于此，历史查询时批量加载。
 *
 * @author huxuehao
 */
@Service
public class SubAgentTracePersistenceService {

    private final JdbcTemplate jdbcTemplate;

    public SubAgentTracePersistenceService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void record(Long tenantId, Long sessionId, SubAgentTraceEvent event) {
        if (tenantId == null || sessionId == null || event == null || event.getInvocationId() == null) {
            return;
        }
        LocalDateTime occurredAt = event.getOccurredAt() == null ? LocalDateTime.now() : event.getOccurredAt();
        String status = statusFor(event.getEventType());
        String task = stringValue(event, "task");
        String summary = stringValue(event, "summary");

        jdbcTemplate.update("""
                INSERT INTO chat_subagent_run
                (tenant_id, session_id, invocation_id, parent_invocation_id, root_run_id,
                 agent_code, agent_title, subagent_session_id, status, task, summary, started_at, ended_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                  status = CASE WHEN VALUES(status) = 'RUNNING' THEN status ELSE VALUES(status) END,
                  task = COALESCE(task, VALUES(task)),
                  summary = COALESCE(VALUES(summary), summary),
                  ended_at = COALESCE(VALUES(ended_at), ended_at),
                  agent_title = COALESCE(NULLIF(VALUES(agent_title), ''), agent_title)
                """,
                tenantId,
                sessionId,
                event.getInvocationId(),
                event.getParentInvocationId(),
                event.getRootRunId(),
                event.getAgent() == null ? null : event.getAgent().getCode(),
                event.getAgent() == null ? null : event.getAgent().getTitle(),
                event.getAgent() == null ? null : event.getAgent().getSubagentSessionId(),
                status,
                task,
                summary,
                Timestamp.valueOf(occurredAt),
                isTerminal(event.getEventType()) ? Timestamp.valueOf(occurredAt) : null);

        jdbcTemplate.update("""
                INSERT IGNORE INTO chat_subagent_event
                (tenant_id, session_id, invocation_id, sequence, event_id, event_type, payload, occurred_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                tenantId,
                sessionId,
                event.getInvocationId(),
                event.getSequence(),
                event.getEventId(),
                event.getEventType().name(),
                JsonUtils.toJsonStr(event.getPayload()),
                Timestamp.valueOf(occurredAt));
    }

    private static boolean isTerminal(SubAgentTraceEventType type) {
        return type == SubAgentTraceEventType.FINISHED
                || type == SubAgentTraceEventType.BLOCKED
                || type == SubAgentTraceEventType.FAILED
                || type == SubAgentTraceEventType.CANCELLED;
    }

    private static String statusFor(SubAgentTraceEventType type) {
        return switch (type) {
            case FINISHED -> "SUCCESS";
            case BLOCKED -> "BLOCKED";
            case FAILED -> "FAILED";
            case CANCELLED -> "CANCELLED";
            default -> "RUNNING";
        };
    }

    private static String stringValue(SubAgentTraceEvent event, String key) {
        Object value = event.getPayload() == null ? null : event.getPayload().get(key);
        return value == null ? null : String.valueOf(value);
    }
}
