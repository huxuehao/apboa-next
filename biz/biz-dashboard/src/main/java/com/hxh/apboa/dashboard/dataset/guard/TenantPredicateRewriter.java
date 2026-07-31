package com.hxh.apboa.dashboard.dataset.guard;

import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.hxh.apboa.common.util.TenantUtils;
import com.hxh.apboa.dashboard.config.DashboardDatasetProperties;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * 描述：数据集 SQL 租户谓词自动注入。复用 MyBatis-Plus 租户改写引擎，
 * 对 SELECT 全量改写（JOIN/子查询/UNION 各分支自动追加 tenant_id = 当前租户），
 * 将"手写租户条件"的约定升级为编译期强制；改写失败一律拒绝（fail-closed）。
 *
 * @author huxuehao
 **/
@Slf4j
@Component
public class TenantPredicateRewriter {
    private final DashboardDatasetProperties properties;
    private final TenantLineInnerInterceptor rewriteEngine;

    public TenantPredicateRewriter(DashboardDatasetProperties properties) {
        this.properties = properties;
        this.rewriteEngine = new TenantLineInnerInterceptor(new TenantLineHandler() {
            @Override
            public Expression getTenantId() {
                Long tenantId = TenantUtils.getCurrentTenantId();
                if (tenantId == null) {
                    throw new DatasetSecurityException("缺少租户上下文，拒绝执行数据集查询");
                }
                return new LongValue(tenantId);
            }

            @Override
            public String getTenantIdColumn() {
                return "tenant_id";
            }

            @Override
            public boolean ignoreTable(String tableName) {
                return isExempt(tableName);
            }
        });
    }

    /** 全局字典表豁免（配置 tenant-exempt-tables，小写比对） */
    private boolean isExempt(String tableName) {
        if (tableName == null) {
            return false;
        }
        String lower = tableName.toLowerCase(Locale.ROOT).replace("`", "").trim();
        return properties.getTenantExemptTables().stream()
                .anyMatch(t -> lower.equals(t.toLowerCase(Locale.ROOT).trim()));
    }

    /**
     * 改写 SQL：为所有非豁免表注入当前租户谓词。改写异常抛 DatasetSecurityException。
     */
    public String rewrite(String sql) {
        try {
            return rewriteEngine.parserSingle(sql, null);
        } catch (DatasetSecurityException e) {
            throw e;
        } catch (Exception e) {
            log.warn("数据集 SQL 租户改写失败，已拒绝执行: {}", e.getMessage());
            throw new DatasetSecurityException("租户隔离改写失败，拒绝执行: " + e.getMessage());
        }
    }
}
