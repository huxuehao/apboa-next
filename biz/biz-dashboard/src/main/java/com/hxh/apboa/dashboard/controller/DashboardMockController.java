package com.hxh.apboa.dashboard.controller;

import com.hxh.apboa.common.r.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 描述：HTTP 数据集联调用的模拟数据接口（仅 GET，数据全部内存构造）。
 * 用于验证 HTTP 数据集的 query 绑定、默认值、同源 token 与 dataPath 映射。
 *
 * @author huxuehao
 **/
@RestController
@RequestMapping("/dashboard/mock")
public class DashboardMockController {
    private static final String[] DEPTS = {"销售部", "研发部", "市场部", "客服部", "财务部"};
    private static final String[] STATUSES = {"done", "doing", "pending"};
    private static final String[] STATUS_NAMES = {"已完成", "进行中", "待处理"};

    /**
     * 模拟订单列表。dataPath 配 data.list 即可映射为行。
     * 支持 query：status(可绑筛选)、kw(关键字，匹配部门/单号)、limit(默认 20)
     */
    @GetMapping("/orders")
    public R<Map<String, Object>> orders(@RequestParam(value = "status", required = false) String status,
                                         @RequestParam(value = "kw", required = false) String kw,
                                         @RequestParam(value = "limit", required = false) Integer limit) {
        int max = limit == null || limit <= 0 ? 20 : Math.min(limit, 100);
        List<Map<String, Object>> list = new ArrayList<>();
        for (int i = 1; list.size() < max && i <= 100; i++) {
            String rowStatus = STATUSES[i % STATUSES.length];
            String dept = DEPTS[i % DEPTS.length];
            String orderNo = "SO2026" + String.format("%04d", i);
            if (status != null && !status.isBlank() && !rowStatus.equals(status)) {
                continue;
            }
            if (kw != null && !kw.isBlank() && !dept.contains(kw) && !orderNo.contains(kw)) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("orderNo", orderNo);
            row.put("dept", dept);
            row.put("status", rowStatus);
            row.put("statusName", STATUS_NAMES[i % STATUS_NAMES.length]);
            row.put("amount", 100 + (i * 137) % 9000);
            row.put("createdAt", LocalDate.now().minusDays(i % 30).toString());
            list.add(row);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", list);
        data.put("total", list.size());
        return R.data(data);
    }

    /**
     * 模拟汇总对象（非数组）。dataPath 配 data 即可映射为单行，
     * 适合数据卡片/进度环等单值面板联调。
     */
    @GetMapping("/summary")
    public R<Map<String, Object>> summary() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("totalOrders", 1280);
        data.put("doneOrders", 964);
        data.put("doneRate", 75.3);
        data.put("amount", 386500);
        data.put("statDate", LocalDate.now().toString());
        return R.data(data);
    }
}
