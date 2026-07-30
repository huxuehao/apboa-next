package com.hxh.apboa.dashboard.dataset.executor;

import com.hxh.apboa.common.enums.dashboard.DatasetType;
import com.hxh.apboa.dashboard.config.DashboardDatasetProperties;
import com.hxh.apboa.dashboard.dataset.guard.SqlSecurityValidator;
import com.hxh.apboa.dashboard.dataset.model.ColumnMeta;
import com.hxh.apboa.dashboard.dataset.model.DatasetExecuteResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 描述：主库 SQL 数据集执行器（MVP 默认）。只读、限行、超时执行。
 *
 * @author huxuehao
 **/
@Component
public class MainDbSqlExecutor extends AbstractDatasetExecutor {
    private final NamedParameterJdbcTemplate namedJdbcTemplate;
    private final DashboardDatasetProperties properties;

    public MainDbSqlExecutor(SqlSecurityValidator validator, DataSource dataSource, DashboardDatasetProperties properties) {
        super(validator);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.setQueryTimeout(properties.getQueryTimeoutSeconds());
        this.namedJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
        this.properties = properties;
    }

    @Override
    public boolean supports(DatasetType type) {
        return type == null || type == DatasetType.SQL;
    }

    @Override
    protected DatasetExecuteResult doExecute(String sql, Map<String, Object> params, int limit) {
        int effectiveLimit = Math.min(limit <= 0 ? properties.getQueryLimit() : limit, properties.getMaxRows());
        return namedJdbcTemplate.query(sql, params, rs -> {
            ResultSetMetaData md = rs.getMetaData();
            int colCount = md.getColumnCount();
            List<ColumnMeta> columns = new ArrayList<>(colCount);
            for (int i = 1; i <= colCount; i++) {
                columns.add(new ColumnMeta(md.getColumnLabel(i), md.getColumnTypeName(i)));
            }
            List<Map<String, Object>> rows = new ArrayList<>();
            boolean truncated = false;
            while (rs.next()) {
                if (rows.size() >= effectiveLimit) {
                    truncated = true;
                    break;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= colCount; i++) {
                    row.put(md.getColumnLabel(i), rs.getObject(i));
                }
                rows.add(row);
            }
            DatasetExecuteResult result = new DatasetExecuteResult();
            result.setColumns(columns);
            result.setRows(rows);
            result.setRowCount(rows.size());
            result.setTruncated(truncated);
            return result;
        });
    }
}
