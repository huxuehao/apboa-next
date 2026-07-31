package com.hxh.apboa.dashboard.dataset.support;

import com.hxh.apboa.common.util.TenantUtils;
import com.hxh.apboa.common.util.UserUtils;
import com.hxh.apboa.dashboard.dataset.model.DatasetExecuteResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 描述：数据集执行审计。结构化输出成功与拒绝记录，便于安全排查与告警接入。
 *
 * @author huxuehao
 **/
@Slf4j
@Component
public class DatasetAuditLogger {
    /** 成功执行：记录租户/用户/来源/最终SQL/参数key/行数/耗时 */
    public void logSuccess(String source, Map<String, Object> params, DatasetExecuteResult result) {
        log.info("[dataset-audit] ok tenant={} user={} source={} rows={} elapsedMs={} paramKeys={} finalSql={}",
                TenantUtils.getCurrentTenantId(), UserUtils.getId(), source,
                result == null ? 0 : result.getRowCount(),
                result == null ? 0 : result.getElapsedMs(),
                params == null ? "[]" : params.keySet(),
                result == null ? "" : result.getExecutedSql());
    }

    /** 执行被拒或失败：记录原因与原始 SQL 摘要 */
    public void logRejected(String source, String rawSql, String reason) {
        log.warn("[dataset-audit] rejected tenant={} user={} source={} reason={} sql={}",
                TenantUtils.getCurrentTenantId(), UserUtils.getId(), source, reason, abbreviate(rawSql));
    }

    private String abbreviate(String sql) {
        if (sql == null) {
            return "";
        }
        String oneLine = sql.replaceAll("\\s+", " ").trim();
        return oneLine.length() > 300 ? oneLine.substring(0, 300) + "..." : oneLine;
    }
}
