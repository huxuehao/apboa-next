package com.hxh.apboa.agent.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.hxh.apboa.common.subagent.SubAgentTraceEventType;
import com.hxh.apboa.common.util.JsonUtils;
import com.hxh.apboa.common.vo.SubAgentRunVO;
import com.hxh.apboa.common.vo.SubAgentTraceEventVO;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 描述：子智能体追踪历史查询服务，批量加载子智能体卡片详情用于聊天记录时间线锚点渲染。
 *
 * @author huxuehao
 */
@Service
public class SubAgentTraceHistoryService {

    private final JdbcTemplate jdbcTemplate;

    public SubAgentTraceHistoryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, SubAgentRunVO> loadByInvocations(Long sessionId, Collection<String> invocationIds) {
        if (sessionId == null || invocationIds == null || invocationIds.isEmpty()) return Map.of();
        List<String> ids = invocationIds.stream().filter(id -> id != null && !id.isBlank()).distinct().toList();
        if (ids.isEmpty()) return Map.of();
        String placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        List<Object> args = new ArrayList<>();
        args.add(sessionId);
        args.addAll(ids);

        Map<String, SubAgentRunVO> runs = new LinkedHashMap<>();
        jdbcTemplate.query("SELECT invocation_id, parent_invocation_id, root_run_id, agent_code, agent_title, "
                        + "subagent_session_id, status, task, summary, started_at, ended_at "
                        + "FROM chat_subagent_run WHERE session_id = ? AND invocation_id IN (" + placeholders + ")",
                rs -> {
                    SubAgentRunVO run = new SubAgentRunVO();
                    run.setInvocationId(rs.getString("invocation_id"));
                    run.setParentInvocationId(rs.getString("parent_invocation_id"));
                    run.setRootRunId(rs.getString("root_run_id"));
                    run.setAgentCode(rs.getString("agent_code"));
                    run.setAgentTitle(rs.getString("agent_title"));
                    run.setSubagentSessionId(rs.getString("subagent_session_id"));
                    run.setStatus(rs.getString("status"));
                    run.setTask(rs.getString("task"));
                    run.setSummary(rs.getString("summary"));
                    Timestamp startedAt = rs.getTimestamp("started_at");
                    Timestamp endedAt = rs.getTimestamp("ended_at");
                    run.setStartedAt(startedAt == null ? null : startedAt.toLocalDateTime());
                    run.setEndedAt(endedAt == null ? null : endedAt.toLocalDateTime());
                    run.setEvents(new ArrayList<>());
                    runs.put(run.getInvocationId(), run);
                }, args.toArray());
        if (runs.isEmpty()) return runs;

        jdbcTemplate.query("SELECT invocation_id, sequence, event_id, event_type, payload, occurred_at "
                        + "FROM chat_subagent_event WHERE session_id = ? AND invocation_id IN (" + placeholders + ") "
                        + "ORDER BY invocation_id, sequence",
                rs -> {
                    SubAgentRunVO run = runs.get(rs.getString("invocation_id"));
                    if (run == null) return;
                    SubAgentTraceEventVO event = new SubAgentTraceEventVO();
                    event.setInvocationId(run.getInvocationId());
                    event.setSequence(rs.getLong("sequence"));
                    event.setEventId(rs.getString("event_id"));
                    event.setEventType(SubAgentTraceEventType.valueOf(rs.getString("event_type")));
                    event.setPayload(JsonUtils.parse(rs.getString("payload"), new TypeReference<Map<String, Object>>() {}));
                    Timestamp occurredAt = rs.getTimestamp("occurred_at");
                    event.setOccurredAt(occurredAt == null ? null : occurredAt.toLocalDateTime());
                    run.getEvents().add(event);
                }, args.toArray());
        return runs;
    }
}
