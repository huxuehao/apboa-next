package com.hxh.apboa.dashboard.dataset.guard;

import com.hxh.apboa.dashboard.config.DashboardDatasetProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 描述：可查询对象白名单校验，防止手写 SQL 跨租户越权访问未授权表。
 * 白名单为空时跳过（仅建议开发期），生产应显式配置带租户列的安全视图。
 *
 * @author huxuehao
 **/
@Slf4j
@Component
public class TableWhitelistGuard implements SqlGuard {
    private final DashboardDatasetProperties properties;

    public TableWhitelistGuard(DashboardDatasetProperties properties) {
        this.properties = properties;
    }

    @Override
    public void check(SqlGuardContext context) {
        List<String> allowed = properties.getAllowedTables();
        if (allowed == null || allowed.isEmpty()) {
            log.warn("Dashboard 数据集未配置可查询对象白名单，跳过白名单校验，存在跨租户越权风险，请在生产环境配置 apboa.dashboard.dataset.allowed-tables");
            return;
        }
        Set<String> allowedLower = allowed.stream()
                .map(t -> t.toLowerCase().trim())
                .collect(Collectors.toSet());
        for (String table : context.getTables()) {
            if (!allowedLower.contains(table.toLowerCase())) {
                throw new DatasetSecurityException("数据集引用了不在白名单中的对象: " + table);
            }
        }
    }

    @Override
    public int getOrder() {
        return 20;
    }
}
