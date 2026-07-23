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
import com.hxh.apboa.common.entity.ModelConfig;
import com.hxh.apboa.common.entity.ModelProvider;
import com.hxh.apboa.common.enums.ModelCategory;
import com.hxh.apboa.common.router.MessageTableRouter;
import com.hxh.apboa.common.util.JsonUtils;
import com.hxh.apboa.common.util.UserUtils;
import com.hxh.apboa.common.vo.CostModelPricingVO;
import com.hxh.apboa.common.vo.CostOverviewVO;
import com.hxh.apboa.common.vo.CostRunItemVO;
import com.hxh.apboa.common.vo.CostSessionDetailVO;
import com.hxh.apboa.common.vo.CostWorkflowDetailVO;
import com.hxh.apboa.common.vo.CostWorkflowNodeVO;
import com.hxh.apboa.common.vo.CostWorkflowUsageVO;
import com.hxh.apboa.model.service.ModelConfigService;
import com.hxh.apboa.model.service.ModelProviderService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
    private final ModelConfigService modelConfigService;
    private final ModelProviderService modelProviderService;

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
        List<Map<String, Object>> byBizChannel = chatUsageRecordMapper.costGroupByBizTypeAndChannel(
                startStr, endExclusive, agentId);
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
                .byBizChannel(byBizChannel)
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
                sessionMap.values().stream().map(ChatSession::getAgentId)
                        .filter(Objects::nonNull).distinct().collect(Collectors.toList()));
        Map<Long, String> userNames = batchUserNames(
                rows.stream().map(r -> asLong(r.get("userId"))).distinct().collect(Collectors.toList()));
        Map<Long, Integer> discardedMap = countDiscardedRuns(sessionMap);
        for (Map<String, Object> row : rows) {
            Long sessionId = asLong(row.get("sessionId"));
            ChatSession session = sessionMap.get(sessionId);
            Long ownerAgentId = session != null ? session.getAgentId() : asLong(row.get("agentId"));
            row.put("title", session != null ? session.getTitle() : "（会话已删除）");
            row.put("agentId", ownerAgentId);
            row.put("agentName", resolveAgentName(
                    agentNames.get(ownerAgentId), row.get("agentLabel"), ownerAgentId));
            row.put("userName", userNames.getOrDefault(asLong(row.get("userId")), String.valueOf(row.get("userId"))));
            row.put("discardedRuns", discardedMap.getOrDefault(sessionId, 0));
        }
        return result;
    }

    @Override
    public IPage<Map<String, Object>> pageExecutionBills(IPage<Map<String, Object>> page,
                                                         LocalDate startDate, LocalDate endDate,
                                                         Long agentId, boolean orderByCost) {
        LocalDate end = endDate != null ? endDate : LocalDate.now();
        LocalDate start = startDate != null ? startDate : end.minusDays(29);
        IPage<Map<String, Object>> result = chatUsageRecordMapper.pageExecutionBills(
                page, start.format(DATE_FMT), end.plusDays(1).format(DATE_FMT), agentId, orderByCost);
        enrichExecutionBills(result.getRecords());
        return result;
    }

    /**
     * 统一账单只在这里补展示元数据，流水聚合仍由一条 SQL 完成。工作流运行信息按页批查，
     * 避免列表为每行额外查询；历史工作流没有 run 关联时保留快照并标记 legacy。
     */
    private void enrichExecutionBills(List<Map<String, Object>> rows) {
        if (rows.isEmpty()) {
            return;
        }
        // 定时任务单按执行体复用两类组装：智能体型（有 sessionId）走会话补全，
        // 工作流型（referenceId=run id）走运行快照补全
        List<Map<String, Object>> chatRows = rows.stream()
                .filter(r -> "CHAT".equals(String.valueOf(r.get("billType")))
                        || ("SCHEDULED_JOB".equals(String.valueOf(r.get("billType"))) && asLong(r.get("sessionId")) > 0))
                .collect(Collectors.toList());
        List<Map<String, Object>> workflowRows = rows.stream()
                .filter(r -> "WORKFLOW".equals(String.valueOf(r.get("billType")))
                        || ("SCHEDULED_JOB".equals(String.valueOf(r.get("billType"))) && asLong(r.get("sessionId")) == 0))
                .collect(Collectors.toList());

        Map<Long, String> userNames = batchUserNames(
                rows.stream().map(r -> asLong(r.get("userId"))).collect(Collectors.toList()));
        for (Map<String, Object> row : rows) {
            Long userId = asLong(row.get("userId"));
            row.put("userName", userNames.getOrDefault(userId, userId > 0 ? String.valueOf(userId) : "未记录"));
        }

        if (!chatRows.isEmpty()) {
            List<Long> sessionIds = chatRows.stream().map(r -> asLong(r.get("sessionId"))).collect(Collectors.toList());
            Map<Long, ChatSession> sessionMap = chatSessionMapper.selectBatchIds(sessionIds).stream()
                    .collect(Collectors.toMap(ChatSession::getId, Function.identity(), (a, b) -> a));
            Map<Long, String> agentNames = batchAgentNames(
                    sessionMap.values().stream().map(ChatSession::getAgentId)
                            .filter(Objects::nonNull).distinct().collect(Collectors.toList()));
            Map<Long, Integer> discardedMap = countDiscardedRuns(sessionMap);
            for (Map<String, Object> row : chatRows) {
                Long sessionId = asLong(row.get("sessionId"));
                ChatSession session = sessionMap.get(sessionId);
                Long ownerAgentId = session != null ? session.getAgentId() : asLong(row.get("agentId"));
                row.put("title", session != null ? session.getTitle() : "（会话已删除）");
                row.put("agentId", ownerAgentId);
                row.put("agentName", resolveAgentName(
                        agentNames.get(ownerAgentId), row.get("agentLabel"), ownerAgentId));
                row.put("discardedRuns", discardedMap.getOrDefault(sessionId, 0));
                row.put("status", "COMPLETED");
            }
        }

        if (!workflowRows.isEmpty()) {
            List<String> runIds = workflowRows.stream()
                    .filter(r -> asLong(r.get("legacy")) == 0)
                    .map(r -> String.valueOf(r.get("referenceId")))
                    .distinct()
                    .collect(Collectors.toList());
            Map<String, WorkflowRunSnapshot> snapshots = batchWorkflowRunSnapshots(runIds);
            for (Map<String, Object> row : workflowRows) {
                String runId = String.valueOf(row.get("referenceId"));
                boolean legacy = asLong(row.get("legacy")) > 0;
                WorkflowRunSnapshot snapshot = snapshots.get(runId);
                row.put("runId", runId);
                row.put("legacy", legacy);
                row.put("status", snapshot != null ? snapshot.status() : (legacy ? "HISTORICAL" : "UNKNOWN"));
                row.put("workflowId", snapshot != null ? snapshot.workflowId() : row.get("workflowId"));
                row.put("title", snapshot != null && snapshot.workflowName() != null
                        ? snapshot.workflowName() : workflowTitle(row.get("titleSnapshot")));
                row.put("nodeCount", snapshot != null ? snapshot.nodeCount() : null);
                if (snapshot != null) {
                    row.put("durationMs", duration(snapshot.startTime(), snapshot.endTime()));
                    row.put("version", snapshot.version());
                }
            }
        }
    }

    private Map<String, WorkflowRunSnapshot> batchWorkflowRunSnapshots(List<String> runIds) {
        Long tenantId = UserUtils.getTenantId();
        if (tenantId == null || runIds.isEmpty()) {
            return Collections.emptyMap();
        }
        String placeholders = String.join(",", Collections.nCopies(runIds.size(), "?"));
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        args.addAll(runIds);
        Map<String, WorkflowRunSnapshot> result = new HashMap<>();
        jdbcTemplate.query(
                "SELECT r.id, r.workflow_id, r.version, r.status, r.inputs, r.outputs, r.error, "
                        + "r.start_time, r.end_time, w.name AS workflow_name, "
                        + "(SELECT COUNT(*) FROM workflow_node_execution n WHERE n.tenant_id = r.tenant_id "
                        + "AND CAST(n.workflow_run_id AS UNSIGNED) = r.id) AS node_count "
                        + "FROM workflow_run r LEFT JOIN workflow w ON w.id = CAST(r.workflow_id AS UNSIGNED) "
                        + "AND w.tenant_id = r.tenant_id WHERE r.tenant_id = ? AND r.id IN (" + placeholders + ")",
                rs -> {
                    WorkflowRunSnapshot snapshot = workflowRunSnapshot(rs);
                    result.put(snapshot.runId(), snapshot);
                },
                args.toArray());
        return result;
    }

    /**
     * 页内会话的真实废弃回复数：有 assistant 消息载体、但 message_id 不在
     * current_message_id 物化路径上的条数。message_id 为空的子智能体等内部调用
     * 不属于聊天分支，不能算作重新生成废弃。页大小 10~50，
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
                        .in(ChatUsageRecord::getBizType,
                                ChatUsageRecord.BIZ_CHAT, ChatUsageRecord.BIZ_SUB_AGENT)
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
        int visibleReplyCount = 0;
        int internalRunCount = 0;
        int llmCallCount = 0;
        int unpricedRuns = 0;
        Map<String, Map<String, Object>> byModel = new LinkedHashMap<>();

        for (ChatUsageRecord r : records) {
            String pathStatus = classifyPathStatus(r.getMessageId(), currentPathIds);
            boolean discarded = CostRunItemVO.PATH_DISCARDED.equals(pathStatus);
            ChatMessage assistantMsg = r.getMessageId() != null ? briefMap.get(r.getMessageId()) : null;

            if (r.getCost() != null) {
                totalCost = totalCost.add(r.getCost());
                if (discarded) {
                    discardedCost = discardedCost.add(r.getCost());
                }
            } else {
                unpricedRuns++;
            }
            if (discarded) {
                discardedRuns++;
            }
            if (CostRunItemVO.PATH_INTERNAL.equals(pathStatus)) {
                internalRunCount++;
            } else {
                visibleReplyCount++;
            }
            llmCallCount += r.getIterationCount() != null ? r.getIterationCount() : 1;
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
                    .agentId(r.getAgentId())
                    .agentName(r.getAgentLabel())
                    .bizType(r.getBizType())
                    .modelConfigId(r.getModelConfigId())
                    .modelLabel(r.getModelLabel())
                    .iterationCount(r.getIterationCount())
                    .inputTokens(r.getInputTokens())
                    .outputTokens(r.getOutputTokens())
                    .durationMs(r.getDurationMs())
                    .cost(r.getCost())
                    .pathStatus(pathStatus)
                    .onCurrentPath(CostRunItemVO.PATH_INTERNAL.equals(pathStatus)
                            ? null : CostRunItemVO.PATH_CURRENT.equals(pathStatus))
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
                .visibleReplyCount(visibleReplyCount)
                .internalRunCount(internalRunCount)
                .llmCallCount(llmCallCount)
                .discardedRunCount(discardedRuns)
                .discardedCost(discardedCost)
                .unpricedRunCount(unpricedRuns)
                .byModel(new ArrayList<>(byModel.values()))
                .runs(runs)
                .build();
    }

    @Override
    public CostWorkflowDetailVO workflowDetail(String runId) {
        Long tenantId = UserUtils.getTenantId();
        if (tenantId == null || runId == null || runId.isBlank()) {
            return null;
        }
        boolean legacy = runId.startsWith("legacy-");
        WorkflowRunSnapshot snapshot = legacy ? null
                : batchWorkflowRunSnapshots(List.of(runId)).get(runId);

        List<ChatUsageRecord> records;
        if (legacy) {
            Long recordId = parseLegacyRecordId(runId);
            if (recordId == null) {
                return null;
            }
            records = chatUsageRecordMapper.selectList(Wrappers.<ChatUsageRecord>lambdaQuery()
                    .eq(ChatUsageRecord::getId, recordId)
                    .eq(ChatUsageRecord::getBizType, ChatUsageRecord.BIZ_WORKFLOW));
        } else {
            records = chatUsageRecordMapper.selectList(Wrappers.<ChatUsageRecord>lambdaQuery()
                    .eq(ChatUsageRecord::getBizType, ChatUsageRecord.BIZ_WORKFLOW)
                    .eq(ChatUsageRecord::getBizRunId, runId)
                    .orderByAsc(ChatUsageRecord::getId));
        }
        if (snapshot == null && records.isEmpty()) {
            return null;
        }

        BigDecimal totalCost = BigDecimal.ZERO;
        long inputTokens = 0;
        long outputTokens = 0;
        int llmCallCount = 0;
        int unpricedRunCount = 0;
        long usageDurationMs = 0;
        Map<String, Map<String, Object>> byModel = new LinkedHashMap<>();
        List<CostWorkflowUsageVO> usages = new ArrayList<>();
        Map<String, List<ChatUsageRecord>> usageByNode = new LinkedHashMap<>();

        for (ChatUsageRecord record : records) {
            inputTokens += value(record.getInputTokens());
            outputTokens += value(record.getOutputTokens());
            llmCallCount += record.getIterationCount() != null ? record.getIterationCount() : 1;
            usageDurationMs += value(record.getDurationMs());
            if (record.getCost() == null) {
                unpricedRunCount++;
            } else {
                totalCost = totalCost.add(record.getCost());
            }

            Map<String, Object> model = byModel.computeIfAbsent(record.getModelLabel(), key -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("modelLabel", key);
                item.put("runCount", 0);
                item.put("llmCallCount", 0);
                item.put("inputTokens", 0L);
                item.put("outputTokens", 0L);
                item.put("cost", BigDecimal.ZERO);
                return item;
            });
            model.put("runCount", (int) model.get("runCount") + 1);
            model.put("llmCallCount", (int) model.get("llmCallCount")
                    + (record.getIterationCount() != null ? record.getIterationCount() : 1));
            model.put("inputTokens", (long) model.get("inputTokens") + value(record.getInputTokens()));
            model.put("outputTokens", (long) model.get("outputTokens") + value(record.getOutputTokens()));
            if (record.getCost() != null) {
                model.put("cost", ((BigDecimal) model.get("cost")).add(record.getCost()));
            }

            usages.add(CostWorkflowUsageVO.builder()
                    .recordId(record.getId())
                    .createdAt(record.getCreatedAt())
                    .nodeId(record.getStepId())
                    .nodeName(record.getStepLabel())
                    .modelConfigId(record.getModelConfigId())
                    .modelLabel(record.getModelLabel())
                    .iterationCount(record.getIterationCount())
                    .inputTokens(record.getInputTokens())
                    .outputTokens(record.getOutputTokens())
                    .durationMs(record.getDurationMs())
                    .cost(record.getCost())
                    .build());
            String nodeKey = record.getStepId() != null ? record.getStepId() : "usage-" + record.getId();
            usageByNode.computeIfAbsent(nodeKey, key -> new ArrayList<>()).add(record);
        }

        List<CostWorkflowNodeVO> nodes = new ArrayList<>();
        if (snapshot != null) {
            for (WorkflowNodeSnapshot node : loadWorkflowNodes(runId, tenantId)) {
                List<ChatUsageRecord> nodeUsages = usageByNode.remove(node.nodeId());
                nodes.add(toWorkflowNode(node, nodeUsages != null ? nodeUsages : List.of()));
            }
        }
        // 极端情况下节点执行日志缺失或历史流水无法关联，仍保留用量，不让成本从详情消失。
        for (Map.Entry<String, List<ChatUsageRecord>> entry : usageByNode.entrySet()) {
            ChatUsageRecord first = entry.getValue().get(0);
            WorkflowNodeSnapshot usageOnly = new WorkflowNodeSnapshot(
                    first.getStepId(), first.getStepLabel() != null ? first.getStepLabel() : "未关联智能体节点",
                    "AGENT", legacy ? "HISTORICAL" : "USAGE_ONLY", null,
                    null, null, null, null);
            nodes.add(toWorkflowNode(usageOnly, entry.getValue()));
        }

        ChatUsageRecord latest = records.isEmpty() ? null : records.get(records.size() - 1);
        Long userId = latest != null ? latest.getUserId() : null;
        String workflowName = snapshot != null ? snapshot.workflowName() : null;
        if ((workflowName == null || workflowName.isBlank()) && latest != null) {
            workflowName = latest.getBizLabel() != null
                    ? latest.getBizLabel() : workflowTitle(latest.getAgentLabel());
        }
        Long startTime = snapshot != null ? snapshot.startTime() : null;
        Long endTime = snapshot != null ? snapshot.endTime() : null;
        Long runDuration = duration(startTime, endTime);
        return CostWorkflowDetailVO.builder()
                .runId(runId)
                .workflowId(snapshot != null ? snapshot.workflowId() : latest != null ? latest.getBizId() : null)
                .workflowName(workflowName != null ? workflowName : "历史工作流")
                .version(snapshot != null ? snapshot.version() : null)
                .status(snapshot != null ? snapshot.status() : "HISTORICAL")
                .inputs(snapshot != null ? snapshot.inputs() : null)
                .outputs(snapshot != null ? snapshot.outputs() : null)
                .error(snapshot != null ? snapshot.error() : null)
                .startTime(startTime)
                .endTime(endTime)
                .durationMs(runDuration != null ? runDuration : usageDurationMs)
                .userId(userId)
                .userName(userId != null
                        ? batchUserNames(List.of(userId)).getOrDefault(userId, String.valueOf(userId)) : "未记录")
                .channel(latest != null ? latest.getChannel() : null)
                .sourceSessionId(latest != null ? latest.getSessionId() : null)
                .legacy(legacy)
                .totalCost(totalCost)
                .inputTokens(inputTokens)
                .outputTokens(outputTokens)
                .usageRunCount(records.size())
                .llmCallCount(llmCallCount)
                .unpricedRunCount(unpricedRunCount)
                .byModel(new ArrayList<>(byModel.values()))
                .nodes(nodes)
                .usages(usages)
                .build();
    }

    private List<WorkflowNodeSnapshot> loadWorkflowNodes(String runId, Long tenantId) {
        List<WorkflowNodeSnapshot> nodes = new ArrayList<>();
        jdbcTemplate.query(
                "SELECT node_id, node_title, node_type, status, error, inputs, outputs, start_time, end_time "
                        + "FROM workflow_node_execution WHERE tenant_id = ? "
                        + "AND CAST(workflow_run_id AS UNSIGNED) = ? "
                        + "ORDER BY start_time, id",
                rs -> {
                    nodes.add(new WorkflowNodeSnapshot(
                            rs.getString("node_id"), rs.getString("node_title"), rs.getString("node_type"),
                            rs.getString("status"), rs.getString("error"), rs.getString("inputs"),
                            rs.getString("outputs"), nullableLong(rs, "start_time"), nullableLong(rs, "end_time")));
                },
                tenantId, runId);
        return nodes;
    }

    private CostWorkflowNodeVO toWorkflowNode(WorkflowNodeSnapshot node, List<ChatUsageRecord> usages) {
        BigDecimal cost = BigDecimal.ZERO;
        long inputTokens = 0;
        long outputTokens = 0;
        long usageDuration = 0;
        int llmCalls = 0;
        int unpriced = 0;
        Set<String> models = new LinkedHashSet<>();
        for (ChatUsageRecord usage : usages) {
            inputTokens += value(usage.getInputTokens());
            outputTokens += value(usage.getOutputTokens());
            usageDuration += value(usage.getDurationMs());
            llmCalls += usage.getIterationCount() != null ? usage.getIterationCount() : 1;
            if (usage.getCost() == null) {
                unpriced++;
            } else {
                cost = cost.add(usage.getCost());
            }
            if (usage.getModelLabel() != null) {
                models.add(usage.getModelLabel());
            }
        }
        Long nodeDuration = duration(node.startTime(), node.endTime());
        return CostWorkflowNodeVO.builder()
                .nodeId(node.nodeId())
                .nodeName(node.nodeName())
                .nodeType(node.nodeType())
                .status(node.status())
                .error(node.error())
                .inputs(node.inputs())
                .outputs(node.outputs())
                .startTime(node.startTime())
                .endTime(node.endTime())
                .durationMs(nodeDuration != null ? nodeDuration : usageDuration)
                .inputTokens(inputTokens)
                .outputTokens(outputTokens)
                .usageRunCount(usages.size())
                .llmCallCount(llmCalls)
                .unpricedRunCount(unpriced)
                .cost(cost)
                .models(new ArrayList<>(models))
                .build();
    }

    private WorkflowRunSnapshot workflowRunSnapshot(ResultSet rs) throws SQLException {
        return new WorkflowRunSnapshot(
                rs.getString("id"), rs.getString("workflow_id"), rs.getString("workflow_name"),
                rs.getString("version"), rs.getString("status"), parseJsonValue(rs.getString("inputs")),
                parseJsonValue(rs.getString("outputs")), rs.getString("error"),
                nullableLong(rs, "start_time"), nullableLong(rs, "end_time"), rs.getInt("node_count"));
    }

    private Object parseJsonValue(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return JsonUtils.parse(json);
        } catch (RuntimeException ignored) {
            return json;
        }
    }

    private Long parseLegacyRecordId(String runId) {
        try {
            return Long.valueOf(runId.substring("legacy-".length()));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String workflowTitle(Object value) {
        String title = value != null ? String.valueOf(value) : null;
        if (title == null || title.isBlank()) {
            return "历史工作流";
        }
        return title.startsWith("工作流：") ? title.substring("工作流：".length()) : title;
    }

    private Long duration(Long startTime, Long endTime) {
        return startTime != null && endTime != null ? Math.max(0, endTime - startTime) : null;
    }

    private Long nullableLong(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        return value instanceof Number number ? number.longValue() : null;
    }

    private long value(Long number) {
        return number != null ? number : 0L;
    }

    private record WorkflowRunSnapshot(
            String runId, String workflowId, String workflowName, String version, String status,
            Object inputs, Object outputs, String error, Long startTime, Long endTime, int nodeCount) {
    }

    private record WorkflowNodeSnapshot(
            String nodeId, String nodeName, String nodeType, String status, String error,
            String inputs, String outputs, Long startTime, Long endTime) {
    }

    /**
     * 成本流水与聊天消息链的三态关系。内部调用没有独立 assistant 消息，
     * messageId 为空是正常数据形态，不代表被重新生成顶替。
     */
    static String classifyPathStatus(Integer messageId, Set<Integer> currentPathIds) {
        if (messageId == null) {
            return CostRunItemVO.PATH_INTERNAL;
        }
        return currentPathIds.contains(messageId)
                ? CostRunItemVO.PATH_CURRENT
                : CostRunItemVO.PATH_DISCARDED;
    }

    @Override
    public int recalculate(LocalDate startDate, LocalDate endDate, Long modelConfigId) {
        LocalDate end = endDate != null ? endDate : LocalDate.now();
        LocalDate start = startDate != null ? startDate : end.minusDays(29);
        return chatUsageRecordMapper.recalculateCost(
                start.format(DATE_FMT), end.plusDays(1).format(DATE_FMT), modelConfigId);
    }

    @Override
    public List<CostModelPricingVO> modelPricingList() {
        List<ModelConfig> models = modelConfigService.lambdaQuery()
                .select(ModelConfig::getId, ModelConfig::getProviderId, ModelConfig::getName,
                        ModelConfig::getModelId, ModelConfig::getEnabled,
                        ModelConfig::getInputPrice, ModelConfig::getOutputPrice)
                .eq(ModelConfig::getCategory, ModelCategory.LLM)
                .list();
        if (models.isEmpty()) {
            return Collections.emptyList();
        }

        // 供应商名批查
        List<Long> providerIds = models.stream().map(ModelConfig::getProviderId)
                .filter(Objects::nonNull).distinct().collect(Collectors.toList());
        Map<Long, ModelProvider> providerMap = providerIds.isEmpty() ? Map.of()
                : modelProviderService.listByIds(providerIds).stream()
                        .collect(Collectors.toMap(ModelProvider::getId, Function.identity(), (a, b) -> a));

        // 近 30 天用量（复用概览的按模型聚合）
        LocalDate end = LocalDate.now();
        Map<Long, Map<String, Object>> usageMap = chatUsageRecordMapper
                .costGroupByModel(end.minusDays(29).format(DATE_FMT), end.plusDays(1).format(DATE_FMT), null)
                .stream()
                .collect(Collectors.toMap(m -> asLong(m.get("modelConfigId")), Function.identity(), (a, b) -> a));

        List<CostModelPricingVO> result = new ArrayList<>();
        for (ModelConfig mc : models) {
            ModelProvider provider = mc.getProviderId() != null ? providerMap.get(mc.getProviderId()) : null;
            Map<String, Object> usage = usageMap.getOrDefault(mc.getId(), Map.of());
            result.add(CostModelPricingVO.builder()
                    .modelConfigId(mc.getId())
                    .name(mc.getName())
                    .modelId(mc.getModelId())
                    .providerName(provider != null ? provider.getName() : null)
                    .providerType(provider != null && provider.getType() != null ? provider.getType().name() : null)
                    .enabled(mc.getEnabled())
                    .inputPrice(mc.getInputPrice())
                    .outputPrice(mc.getOutputPrice())
                    .tokens30d(asLong(usage.get("inputTokens")) + asLong(usage.get("outputTokens")))
                    .cost30d(usage.get("cost") instanceof BigDecimal b ? b : BigDecimal.ZERO)
                    .unpricedTokens30d(asLong(usage.get("unpricedTokens")))
                    .runCount30d(asLong(usage.get("runCount")))
                    .build());
        }
        // 未配价在前（提醒补配），组内按近 30 天用量降序（量大的优先看到）
        result.sort(java.util.Comparator
                .comparing((CostModelPricingVO v) -> v.getInputPrice() != null && v.getOutputPrice() != null)
                .thenComparing(CostModelPricingVO::getTokens30d, java.util.Comparator.reverseOrder()));
        return result;
    }

    @Override
    public void updateModelPricing(Long modelConfigId, BigDecimal inputPrice, BigDecimal outputPrice) {
        ModelConfig model = modelConfigService.getById(modelConfigId);
        if (model == null || model.getCategory() != ModelCategory.LLM) {
            throw new RuntimeException("模型不存在或不是对话生成用途");
        }
        // 只动价格两列（配价页轻量入口，不回传整个 entity 防误覆盖其他配置）
        modelConfigService.lambdaUpdate()
                .eq(ModelConfig::getId, modelConfigId)
                .set(ModelConfig::getInputPrice, inputPrice)
                .set(ModelConfig::getOutputPrice, outputPrice)
                .update();
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
     * 智能体显示名回落链：现存最新名 > 流水快照名 > 「已删除智能体(#id尾4位)」。
     * agent_id=0 是无 agent 归属的哨兵（工作流独立运行/定时任务等），本就查不到
     * 现存名，直接用快照 label，不能标「已删除」。
     */
    private String resolveAgentName(String currentName, Object snapshotLabel, Long agentId) {
        if (agentId != null && agentId == 0L) {
            return snapshotLabel instanceof String s && !s.isBlank() ? s : "独立运行";
        }
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
