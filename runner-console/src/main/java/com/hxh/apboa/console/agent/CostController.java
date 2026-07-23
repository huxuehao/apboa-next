package com.hxh.apboa.console.agent;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hxh.apboa.agent.service.CostStatService;
import com.hxh.apboa.common.config.auth.RoleNeed;
import com.hxh.apboa.common.enums.TenantRole;
import com.hxh.apboa.common.mp.support.MP;
import com.hxh.apboa.common.mp.support.PageParams;
import com.hxh.apboa.common.r.R;
import com.hxh.apboa.common.vo.CostOverviewVO;
import com.hxh.apboa.common.vo.CostSessionDetailVO;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

/**
 * 成本中心：概览统计（管理员/编辑角色可见；金额人民币、已计价口径，
 * 未配价用量单独提醒）
 *
 * @author huxuehao
 */
@RestController
@RequestMapping("/agent/cost")
@RequiredArgsConstructor
public class CostController {

    private final CostStatService costStatService;

    /**
     * 概览看板聚合（汇总卡/日趋势/模型与场景分布/智能体TopN/未配价告警）。
     * 日期均为含边界的自然日；缺省近 30 天。
     */
    @GetMapping("/overview")
    @RoleNeed({TenantRole.TENANT_ADMIN, TenantRole.TENANT_EDITOR})
    public R<CostOverviewVO> overview(
            @RequestParam(value = "startDate", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(value = "endDate", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
            @RequestParam(value = "agentId", required = false) Long agentId) {
        return R.data(costStatService.overview(startDate, endDate, agentId));
    }

    /**
     * 会话账单分页（按会话聚合，orderBy=cost 按成本降序 / time 按最后活跃降序）
     */
    @GetMapping("/sessions")
    @RoleNeed({TenantRole.TENANT_ADMIN, TenantRole.TENANT_EDITOR})
    public R<IPage<Map<String, Object>>> sessions(
            PageParams pageParams,
            @RequestParam(value = "startDate", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(value = "endDate", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
            @RequestParam(value = "agentId", required = false) Long agentId,
            @RequestParam(value = "orderBy", required = false, defaultValue = "cost") String orderBy) {
        return R.data(costStatService.pageSessionBills(
                MP.getPage(pageParams), startDate, endDate, agentId, !"time".equals(orderBy)));
    }

    /**
     * 单会话逐轮成本明细（实际发生口径：含废弃分支，onCurrentPath=false 标记）
     */
    @GetMapping("/session/{sessionId}")
    @RoleNeed({TenantRole.TENANT_ADMIN, TenantRole.TENANT_EDITOR})
    public R<CostSessionDetailVO> sessionDetail(@PathVariable("sessionId") Long sessionId) {
        return R.data(costStatService.sessionDetail(sessionId));
    }

    /**
     * 重算历史成本：区间流水按模型当前单价刷新（补配/改错价后修正，仅管理员）
     */
    @PostMapping("/recalculate")
    @RoleNeed({TenantRole.TENANT_ADMIN})
    public R<Integer> recalculate(
            @RequestParam(value = "startDate", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(value = "endDate", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
            @RequestParam(value = "modelConfigId", required = false) Long modelConfigId) {
        return R.data(costStatService.recalculate(startDate, endDate, modelConfigId));
    }

    /**
     * 存量消息回填流水：扫主表+归档表 assistant 消息 meta 补写（幂等，仅管理员）
     */
    @PostMapping("/backfill")
    @RoleNeed({TenantRole.TENANT_ADMIN})
    public R<Map<String, Integer>> backfill() {
        return R.data(costStatService.backfill());
    }
}
