package com.hxh.apboa.agent.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hxh.apboa.agent.mapper.AgentDefinitionMapper;
import com.hxh.apboa.agent.mapper.ChatSessionMapper;
import com.hxh.apboa.agent.mapper.ChatUsageRecordMapper;
import com.hxh.apboa.agent.service.CostStatService;
import com.hxh.apboa.common.entity.AgentDefinition;
import com.hxh.apboa.common.entity.ChatMessage;
import com.hxh.apboa.common.entity.ChatSession;
import com.hxh.apboa.common.entity.ChatUsageRecord;
import com.hxh.apboa.common.router.MessageTableRouter;
import com.hxh.apboa.common.vo.CostOverviewVO;
import com.hxh.apboa.common.vo.CostRunItemVO;
import com.hxh.apboa.common.vo.CostSessionDetailVO;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 成本中心统计实现：全部从 chat_usage_record 聚合（独立流水，不受消息月度归档影响）。
 * 金额为已计价口径，未配价用量单列提醒；趋势按日期骨架补零保证曲线连续。
 *
 * @author huxuehao
 */
@Service
@RequiredArgsConstructor
public class CostStatServiceImpl implements CostStatService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final int TOP_AGENT_LIMIT = 10;

    private final ChatUsageRecordMapper chatUsageRecordMapper;
    private final AgentDefinitionMapper agentDefinitionMapper;
    private final ChatSessionMapper chatSessionMapper;
    private final MessageTableRouter messageTableRouter;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public CostOverviewVO overview(LocalDate startDate, LocalDate endDate, Long agentId) {
        LocalDate end = endDate != null ? endDate : LocalDate.now();
        LocalDate start = startDate != null ? startDate : end.minusDays(29);
        String startStr = start.format(DATE_FMT);
        String endExclusive = end.plusDays(1).format(DATE_FMT);

        Map<String, Object> summary = chatUsageRecordMapper.costSummary(startStr, endExclusive, agentId);
        List<Map<String, Object>> byModel = chatUsageRecordMapper.costGroupByModel(startStr, endExclusive, agentId);
        List<Map<String, Object>> byBizType = chatUsageRecordMapper.costGroupByBizType(startStr, endExclusive, agentId);
        List<Map<String, Object>> byChannel = chatUsageRecordMapper.costGroupByChannel(startStr, endExclusive, agentId);
        List<Map<String, Object>> trend = fillTrendSkeleton(start, end,
                chatUsageRecordMapper.costTrendByDay(startStr, endExclusive, agentId));
        List<Map<String, Object>> topAgents = attachAgentNames(
                chatUsageRecordMapper.costTopAgents(startStr, endExclusive, TOP_AGENT_LIMIT));

        List<Map<String, Object>> unpricedModels = byModel.stream()
                .filter(m -> asLong(m.get("unpricedTokens")) > 0)
                .collect(Collectors.toList());

        return CostOverviewVO.builder()
                .totalCost((BigDecimal) summary.get("totalCost"))
                .inputCost((BigDecimal) summary.get("inputCost"))
                .outputCost((BigDecimal) summary.get("outputCost"))
                .inputTokens(asLong(summary.get("inputTokens")))
                .outputTokens(asLong(summary.get("outputTokens")))
                .runCount(asLong(summary.get("runCount")))
                .sessionCount(asLong(summary.get("sessionCount")))
                .unpricedTokens(asLong(summary.get("unpricedTokens")))
                .trend(trend)
                .byModel(byModel)
                .byBizType(byBizType)
                .byChannel(byChannel)
                .topAgents(topAgents)
                .unpricedModels(unpricedModels)
                .build();
    }

    @Override
    public IPage<Map<String, Object>> pageSessionBills(IPage<Map<String, Object>> page,
                                                       LocalDate startDate, LocalDate endDate,
                                                       Long agentId, boolean orderByCost) {
        LocalDate end = endDate != null ? endDate : LocalDate.now();
        LocalDate start = startDate != null ? startDate : end.minusDays(29);
        IPage<Map<String, Object>> result = chatUsageRecordMapper.pageSessionBills(
                page, start.format(DATE_FMT), end.plusDays(1).format(DATE_FMT), agentId, orderByCost);

        List<Map<String, Object>> rows = result.getRecords();
        if (rows.isEmpty()) {
            return result;
        }
        // 批查会话标题、智能体名、用户显示名（会话被删仍展示流水聚合行）
        List<Long> sessionIds = rows.stream().map(r -> asLong(r.get("sessionId"))).collect(Collectors.toList());
        Map<Long, ChatSession> sessionMap = chatSessionMapper.selectBatchIds(sessionIds).stream()
                .collect(Collectors.toMap(ChatSession::getId, Function.identity(), (a, b) -> a));
        Map<Long, String> agentNames = batchAgentNames(
                rows.stream().map(r -> asLong(r.get("agentId"))).distinct().collect(Collectors.toList()));
        Map<Long, String> userNames = batchUserNames(
                rows.stream().map(r -> asLong(r.get("userId"))).distinct().collect(Collectors.toList()));
        Map<Long, Integer> discardedMap = countDiscardedRuns(sessionMap);
        for (Map<String, Object> row : rows) {
            Long sessionId = asLong(row.get("sessionId"));
            ChatSession session = sessionMap.get(sessionId);
            row.put("title", session != null ? session.getTitle() : "（会话已删除）");
            row.put("agentName", resolveAgentName(
                    agentNames.get(asLong(row.get("agentId"))), row.get("agentLabel"), asLong(row.get("agentId"))));
            row.put("userName", userNames.getOrDefault(asLong(row.get("userId")), String.valueOf(row.get("userId"))));
            row.put("discardedRuns", discardedMap.getOrDefault(sessionId, 0));
        }
        return result;
    }

    /**
     * 页内会话的废弃分支轮次数：会话流水的 message_id 不在 current_message_id
     * 物化路径上的条数（与明细页 onCurrentPath 同口径）。页大小 10~50，
     * 一次流水批查 + 每会话一次消息主键查，开销可控
     */
    private Map<Long, Integer> countDiscardedRuns(Map<Long, ChatSession> sessionMap) {
        Map<Long, Integer> result = new HashMap<>();
        if (sessionMap.isEmpty()) {
            return result;
        }
        List<ChatUsageRecord> records = chatUsageRecordMapper.selectList(
                Wrappers.<ChatUsageRecord>lambdaQuery()
                        .select(ChatUsageRecord::getSessionId, ChatUsageRecord::getMessageId)
                        .in(ChatUsageRecord::getSessionId, sessionMap.keySet())
                        .isNotNull(ChatUsageRecord::getMessageId));
        Map<Long, List<Integer>> messagesBySession = records.stream()
                .collect(Collectors.groupingBy(ChatUsageRecord::getSessionId,
                        Collectors.mapping(ChatUsageRecord::getMessageId, Collectors.toList())));
        messagesBySession.forEach((sessionId, messageIds) -> {
            ChatSession session = sessionMap.get(sessionId);
            if (session == null) {
                return;
            }
            Set<Integer> pathIds = resolveCurrentPathIds(session);
            int discarded = (int) messageIds.stream().filter(id -> !pathIds.contains(id)).count();
            if (discarded > 0) {
                result.put(sessionId, discarded);
            }
        });
        return result;
    }

    @Override
    public CostSessionDetailVO sessionDetail(Long sessionId) {
        // MP 查询走租户拦截器：跨租户的 sessionId 查不到，天然防越权
        ChatSession session = chatSessionMapper.selectById(sessionId);
        if (session == null) {
            return null;
        }
        List<ChatUsageRecord> records = chatUsageRecordMapper.selectList(
                Wrappers.<ChatUsageRecord>lambdaQuery()
                        .eq(ChatUsageRecord::getSessionId, sessionId)
                        .orderByAsc(ChatUsageRecord::getId));

        // 当前链：current_message_id 的 path 上的所有消息 id
        Set<Integer> currentPathIds = resolveCurrentPathIds(session);

        // 轻量批查 assistant 消息（拿 path/摘要），再批查其 path 全链（回溯用户问题）
        List<Integer> messageIds = records.stream().map(ChatUsageRecord::getMessageId)
                .filter(Objects::nonNull).collect(Collectors.toList());
        Map<Integer, ChatMessage> briefMap = new HashMap<>();
        messageTableRouter.listBriefByIds(messageIds, session.getMessageTable())
                .forEach(m -> briefMap.put(m.getId(), m));
        Set<Integer> chainIds = new HashSet<>();
        briefMap.values().forEach(m -> chainIds.addAll(parsePathIds(m.getPath())));
        chainIds.removeAll(briefMap.keySet());
        messageTableRouter.listBriefByIds(new ArrayList<>(chainIds), session.getMessageTable())
                .forEach(m -> briefMap.put(m.getId(), m));

        List<CostRunItemVO> runs = new ArrayList<>();
        BigDecimal totalCost = BigDecimal.ZERO;
        BigDecimal discardedCost = BigDecimal.ZERO;
        long inputTokens = 0;
        long outputTokens = 0;
        int discardedRuns = 0;
        int unpricedRuns = 0;
        Map<String, Map<String, Object>> byModel = new LinkedHashMap<>();

        for (ChatUsageRecord r : records) {
            boolean onPath = r.getMessageId() != null && currentPathIds.contains(r.getMessageId());
            ChatMessage assistantMsg = r.getMessageId() != null ? briefMap.get(r.getMessageId()) : null;

            if (r.getCost() != null) {
                totalCost = totalCost.add(r.getCost());
                if (!onPath) {
                    discardedCost = discardedCost.add(r.getCost());
                }
            } else {
                unpricedRuns++;
            }
            if (!onPath) {
                discardedRuns++;
            }
            inputTokens += r.getInputTokens() != null ? r.getInputTokens() : 0;
            outputTokens += r.getOutputTokens() != null ? r.getOutputTokens() : 0;

            Map<String, Object> modelAgg = byModel.computeIfAbsent(r.getModelLabel(), k -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("modelLabel", k);
                m.put("runCount", 0);
                m.put("cost", BigDecimal.ZERO);
                return m;
            });
            modelAgg.put("runCount", (int) modelAgg.get("runCount") + 1);
            if (r.getCost() != null) {
                modelAgg.put("cost", ((BigDecimal) modelAgg.get("cost")).add(r.getCost()));
            }

            runs.add(CostRunItemVO.builder()
                    .recordId(r.getId())
                    .messageId(r.getMessageId())
                    .createdAt(r.getCreatedAt())
                    .modelConfigId(r.getModelConfigId())
                    .modelLabel(r.getModelLabel())
                    .iterationCount(r.getIterationCount())
                    .inputTokens(r.getInputTokens())
                    .outputTokens(r.getOutputTokens())
                    .durationMs(r.getDurationMs())
                    .cost(r.getCost())
                    .onCurrentPath(onPath)
                    .userQuestion(traceUserQuestion(assistantMsg, briefMap))
                    .assistantSummary(assistantMsg != null ? assistantMsg.getContent() : null)
                    .build());
        }

        String agentLabelSnapshot = records.stream()
                .filter(r -> Objects.equals(r.getAgentId(), session.getAgentId()))
                .map(ChatUsageRecord::getAgentLabel)
                .filter(Objects::nonNull)
                .reduce((a, b) -> b)
                .orElse(null);
        return CostSessionDetailVO.builder()
                .sessionId(sessionId)
                .title(session.getTitle())
                .agentId(session.getAgentId())
                .agentName(resolveAgentName(
                        batchAgentNames(List.of(session.getAgentId())).get(session.getAgentId()),
                        agentLabelSnapshot, session.getAgentId()))
                .userId(session.getUserId())
                .userName(batchUserNames(List.of(session.getUserId()))
                        .getOrDefault(session.getUserId(), String.valueOf(session.getUserId())))
                .totalCost(totalCost)
                .inputTokens(inputTokens)
                .outputTokens(outputTokens)
                .runCount(runs.size())
                .discardedRunCount(discardedRuns)
                .discardedCost(discardedCost)
                .unpricedRunCount(unpricedRuns)
                .byModel(new ArrayList<>(byModel.values()))
                .runs(runs)
                .build();
    }

    @Override
    public int recalculate(LocalDate startDate, LocalDate endDate, Long modelConfigId) {
        LocalDate end = endDate != null ? endDate : LocalDate.now();
        LocalDate start = startDate != null ? startDate : end.minusDays(29);
        return chatUsageRecordMapper.recalculateCost(
                start.format(DATE_FMT), end.plusDays(1).format(DATE_FMT), modelConfigId);
    }

    /** 归档表名白名单格式（与 MessageTableRouter 一致），防 ${table} 注入 */
    private static final java.util.regex.Pattern ARCHIVE_TABLE = java.util.regex.Pattern.compile("chat_message_\\d{6}");

    @Override
    public Map<String, Integer> backfill() {
        Map<String, Integer> result = new LinkedHashMap<>();
        result.put("chat_message", chatUsageRecordMapper.backfillFromMessages("chat_message"));
        List<String> archives = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables "
                        + "WHERE table_schema = DATABASE() AND table_name LIKE 'chat\\_message\\_%' ORDER BY table_name",
                String.class);
        for (String table : archives) {
            if (ARCHIVE_TABLE.matcher(table).matches()) {
                result.put(table, chatUsageRecordMapper.backfillFromMessages(table));
            }
        }
        return result;
    }

    /**
     * 当前链消息 id 集合：current_message_id 的物化路径 path（如 "1930/1931/1933"）拆出
     */
    private Set<Integer> resolveCurrentPathIds(ChatSession session) {
        if (session.getCurrentMessageId() == null) {
            return Collections.emptySet();
        }
        ChatMessage current = messageTableRouter.getById(session.getCurrentMessageId(), session.getMessageTable());
        return current == null ? Collections.emptySet() : parsePathIds(current.getPath());
    }

    private Set<Integer> parsePathIds(String path) {
        if (path == null || path.isBlank()) {
            return Collections.emptySet();
        }
        Set<Integer> ids = new HashSet<>();
        for (String part : path.split("/")) {
            if (!part.isBlank()) {
                try {
                    ids.add(Integer.parseInt(part.trim()));
                } catch (NumberFormatException ignore) {
                    // 路径片段异常直接跳过，不影响其余
                }
            }
        }
        return ids;
    }

    /**
     * 沿 assistant 消息自己的 path 逆序回溯最近的 user 消息内容（工具/思考消息隔在中间也能跨过）
     */
    private String traceUserQuestion(ChatMessage assistantMsg, Map<Integer, ChatMessage> briefMap) {
        if (assistantMsg == null || assistantMsg.getPath() == null) {
            return null;
        }
        String[] parts = assistantMsg.getPath().split("/");
        for (int i = parts.length - 1; i >= 0; i--) {
            try {
                ChatMessage m = briefMap.get(Integer.parseInt(parts[i].trim()));
                if (m != null && "user".equals(m.getRole()) && m.getId() < assistantMsg.getId()) {
                    return m.getContent();
                }
            } catch (NumberFormatException ignore) {
                // 跳过异常片段
            }
        }
        return null;
    }

    /**
     * 批查用户显示名（account 为全局表，跨模块直查；nickname 优先，回落 username）
     */
    private Map<Long, String> batchUserNames(List<Long> userIds) {
        List<Long> ids = userIds.stream().filter(Objects::nonNull).filter(id -> id > 0).distinct().collect(Collectors.toList());
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        Map<Long, String> result = new HashMap<>();
        jdbcTemplate.query(
                "SELECT id, nickname, username FROM account WHERE id IN (" + placeholders + ")",
                rs -> {
                    String nickname = rs.getString("nickname");
                    result.put(rs.getLong("id"), nickname != null && !nickname.isBlank() ? nickname : rs.getString("username"));
                },
                ids.toArray()
        );
        return result;
    }

    private Map<Long, String> batchAgentNames(List<Long> agentIds) {
        List<Long> ids = agentIds.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return agentDefinitionMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(AgentDefinition::getId, AgentDefinition::getName, (a, b) -> a));
    }

    /**
     * 日期骨架补零：无流水的日期补 0 值点，保证前端曲线连续
     */
    private List<Map<String, Object>> fillTrendSkeleton(LocalDate start, LocalDate end, List<Map<String, Object>> rows) {
        Map<String, Map<String, Object>> byDate = rows.stream()
                .collect(Collectors.toMap(r -> String.valueOf(r.get("date")), Function.identity(), (a, b) -> a));
        List<Map<String, Object>> result = new ArrayList<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            String key = d.format(DATE_FMT);
            Map<String, Object> row = byDate.get(key);
            if (row == null) {
                row = new LinkedHashMap<>();
                row.put("date", key);
                row.put("inputCost", BigDecimal.ZERO);
                row.put("outputCost", BigDecimal.ZERO);
                row.put("cost", BigDecimal.ZERO);
            } else {
                // DATE() 返回 java.sql.Date，统一为字符串方便前端直用
                row.put("date", key);
            }
            result.add(row);
        }
        return result;
    }

    /**
     * TopN 行补智能体名称（批查一次），回落链：现存最新名 > 流水 agent_label 快照 >
     * 「已删除智能体(#尾号)」
     */
    private List<Map<String, Object>> attachAgentNames(List<Map<String, Object>> rows) {
        if (rows.isEmpty()) {
            return rows;
        }
        Map<Long, String> nameMap = batchAgentNames(
                rows.stream().map(r -> asLong(r.get("agentId"))).collect(Collectors.toList()));
        for (Map<String, Object> row : rows) {
            Long id = asLong(row.get("agentId"));
            row.put("agentName", resolveAgentName(nameMap.get(id), row.get("agentLabel"), id));
        }
        return rows;
    }

    /**
     * 智能体显示名回落链：现存最新名 > 流水快照名 > 「已删除智能体(#id尾4位)」
     */
    private String resolveAgentName(String currentName, Object snapshotLabel, Long agentId) {
        if (currentName != null && !currentName.isBlank()) {
            return currentName;
        }
        if (snapshotLabel instanceof String s && !s.isBlank()) {
            return s + "（已删除）";
        }
        String idStr = String.valueOf(agentId);
        return "已删除智能体(#" + idStr.substring(Math.max(0, idStr.length() - 4)) + ")";
    }

    private long asLong(Object o) {
        return o instanceof Number n ? n.longValue() : 0L;
    }
}
