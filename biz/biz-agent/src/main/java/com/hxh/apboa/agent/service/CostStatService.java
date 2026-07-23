package com.hxh.apboa.agent.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hxh.apboa.common.vo.CostModelPricingVO;
import com.hxh.apboa.common.vo.CostOverviewVO;
import com.hxh.apboa.common.vo.CostSessionDetailVO;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 成本中心统计 Service
 *
 * @author huxuehao
 */
public interface CostStatService {

    /**
     * 概览看板聚合：汇总卡 + 日趋势 + 模型/场景分布 + 智能体 TopN + 未配价告警。
     *
     * @param startDate 起始日（含），null 取 endDate-29 天
     * @param endDate   截止日（含），null 取今天
     * @param agentId   可选，只看某个智能体
     */
    CostOverviewVO overview(LocalDate startDate, LocalDate endDate, Long agentId);

    /**
     * 会话账单分页：按会话聚合成本/token/轮次/模型分布，附会话标题、智能体与用户显示名。
     *
     * @param orderByCost true 按成本降序，false 按最后活跃降序
     */
    IPage<Map<String, Object>> pageSessionBills(IPage<Map<String, Object>> page,
                                                LocalDate startDate, LocalDate endDate,
                                                Long agentId, boolean orderByCost);

    /**
     * 单会话逐轮明细：按「实际发生」口径列出全部 run（含废弃分支，onCurrentPath=false 标记），
     * 附用户问题/回复摘要与汇总卡。会话不存在（或不属当前租户）返回 null。
     */
    CostSessionDetailVO sessionDetail(Long sessionId);

    /**
     * 重算历史成本：区间内流水按模型「当前」单价刷新（补配/改错价后修正历史账）。
     *
     * @param modelConfigId 可选，只重算某个模型
     * @return 更新的流水行数
     */
    int recalculate(LocalDate startDate, LocalDate endDate, Long modelConfigId);

    /**
     * 模型配价列表：全部 LLM 模型（含禁用）的价格 + 近 30 天用量合并视图，
     * 按「未配价在前、用量降序」排列，供配价页批量改价。
     */
    List<CostModelPricingVO> modelPricingList();

    /**
     * 轻量改价：只更新指定 LLM 模型的输入/输出单价两列（不动其他配置）。
     * 模型不存在/非 LLM 抛异常。
     */
    void updateModelPricing(Long modelConfigId, java.math.BigDecimal inputPrice, java.math.BigDecimal outputPrice);

    /**
     * 存量消息回填流水：扫主表与全部归档表的 assistant 消息 meta，幂等去重后
     * 补写流水（旧数据按模型当前价估算，created_at 沿用消息时间）。
     *
     * @return 各表插入行数，key=表名
     */
    Map<String, Integer> backfill();
}
