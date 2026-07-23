package com.hxh.apboa.agent.mapper;

import com.hxh.apboa.common.entity.ChatUsageRecord;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * LLM 用量成本流水 Mapper。统计 SQL 说明：金额均为「已计价」口径（cost 非 NULL 行），
 * 未配价用量以 unpricedTokens 单独返回；时间区间左闭右开 [start, endExclusive)。
 *
 * @author huxuehao
 */
@Mapper
public interface ChatUsageRecordMapper extends BaseMapper<ChatUsageRecord> {

    /**
     * 汇总卡：总成本/输入输出成本/token/调用次数/会话数/未计价 token
     */
    Map<String, Object> costSummary(@Param("start") String start,
                                    @Param("endExclusive") String endExclusive,
                                    @Param("agentId") Long agentId);

    /**
     * 日趋势：date / inputCost / outputCost / cost
     */
    List<Map<String, Object>> costTrendByDay(@Param("start") String start,
                                             @Param("endExclusive") String endExclusive,
                                             @Param("agentId") Long agentId);

    /**
     * 按模型分布（同模型改名后取最新 label；含未计价 token 量），按成本降序
     */
    List<Map<String, Object>> costGroupByModel(@Param("start") String start,
                                               @Param("endExclusive") String endExclusive,
                                               @Param("agentId") Long agentId);

    /**
     * 按场景分布（CHAT/WORKFLOW/SCHEDULED_JOB/SUB_AGENT）
     */
    List<Map<String, Object>> costGroupByBizType(@Param("start") String start,
                                                 @Param("endExclusive") String endExclusive,
                                                 @Param("agentId") Long agentId);

    /**
     * 按渠道分布（WEB/CHAT_KEY/SK_API；NULL=渠道标记上线前的历史流水）
     */
    List<Map<String, Object>> costGroupByChannel(@Param("start") String start,
                                                 @Param("endExclusive") String endExclusive,
                                                 @Param("agentId") Long agentId);

    /**
     * 智能体 TopN（名称由 service 层批查组装，避免 LEFT JOIN 与租户插件的语义纠缠）
     */
    List<Map<String, Object>> costTopAgents(@Param("start") String start,
                                            @Param("endExclusive") String endExclusive,
                                            @Param("limit") int limit);

    /**
     * 会话账单分页：按 session 聚合（会话标题/用户名由 service 批查组装）。
     * orderByCost=true 按成本降序，否则按最后活跃降序
     */
    com.baomidou.mybatisplus.core.metadata.IPage<Map<String, Object>> pageSessionBills(
            com.baomidou.mybatisplus.core.metadata.IPage<Map<String, Object>> page,
            @Param("start") String start,
            @Param("endExclusive") String endExclusive,
            @Param("agentId") Long agentId,
            @Param("orderByCost") boolean orderByCost);

    /**
     * 重算历史成本：区间内流水按模型「当前」单价刷新价格快照与成本
     * （模型已删或仍未配价的行 JOIN 过滤不动）。返回更新行数
     */
    int recalculateCost(@Param("start") String start,
                        @Param("endExclusive") String endExclusive,
                        @Param("modelConfigId") Long modelConfigId);

    /**
     * 存量消息回填流水：从指定消息表（主表或归档表，表名由调用方白名单校验）
     * 抽 assistant 消息 meta 里的 token/模型写流水，NOT EXISTS 幂等去重；
     * 老消息 meta 无 modelConfigId 记 0/unknown；价格按模型当前价（估算口径），
     * created_at 沿用消息时间使趋势可回溯。返回插入行数
     */
    int backfillFromMessages(@Param("table") String table);
}
