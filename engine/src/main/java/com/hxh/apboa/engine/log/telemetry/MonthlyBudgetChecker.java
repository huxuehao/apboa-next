package com.hxh.apboa.engine.log.telemetry;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

/**
 * 智能体月度预算判定（对话 run 与定时任务共用）：智能体配置了 monthly_budget 且当月
 * 已计价成本（chat_usage_record 聚合，含对话/子智能体/定时等全部场景）达到预算时返回
 * 超支明细，由调用方各自包装拒绝形式（对话链路发拒绝事件流、定时链路记执行失败）。
 *
 * <p>未配预算或未超额返回 empty 放行；未配价模型的用量 cost 为 NULL 不计入——
 * 启用预算前应先为相关模型配价。检查自身故障不拦业务：放行并告警。
 *
 * @author huxuehao
 */
@Slf4j
public final class MonthlyBudgetChecker {

    private MonthlyBudgetChecker() {}

    /** 超支明细：已用金额与预算额度，供调用方组装提示文案 */
    public record BudgetExceeded(BigDecimal spent, BigDecimal budget) {}

    /**
     * 按智能体判定当月预算是否已超支。
     *
     * @return 超支时返回明细；未配预算/未超额/检查异常返回 empty（放行）
     */
    public static Optional<BudgetExceeded> check(Long agentId, JdbcTemplate jdbcTemplate) {
        try {
            if (agentId == null) {
                return Optional.empty();
            }
            BigDecimal budget = jdbcTemplate.query(
                    "SELECT monthly_budget FROM agent_definition WHERE id = ?",
                    rs -> rs.next() ? rs.getBigDecimal("monthly_budget") : null,
                    agentId
            );
            if (budget == null) {
                return Optional.empty();
            }
            // 月初基准必须应用侧生成：流水 created_at 是应用时区（北京），mysql NOW() 在
            // 容器里常为 UTC，用 DATE_FORMAT(NOW(),..) 会在每月 1 日 0~8 点错切上月账
            String monthStart = LocalDate.now().withDayOfMonth(1).toString();
            BigDecimal spent = jdbcTemplate.queryForObject(
                    "SELECT IFNULL(SUM(cost), 0) FROM chat_usage_record "
                            + "WHERE agent_id = ? AND created_at >= ?",
                    BigDecimal.class,
                    agentId, monthStart
            );
            if (spent == null || spent.compareTo(budget) < 0) {
                return Optional.empty();
            }
            return Optional.of(new BudgetExceeded(spent, budget));
        } catch (Exception e) {
            log.error("月度预算检查失败 agentId={}: {}", agentId, e.getMessage());
            return Optional.empty();
        }
    }
}
