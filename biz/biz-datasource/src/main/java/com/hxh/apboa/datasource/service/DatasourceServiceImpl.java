package com.hxh.apboa.datasource.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hxh.apboa.common.entity.Datasource;
import com.hxh.apboa.common.enums.datasource.DatasourceType;
import com.hxh.apboa.datasource.mapper.DatasourceMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.StringJoiner;

@Service
public class DatasourceServiceImpl extends ServiceImpl<DatasourceMapper, Datasource> implements DatasourceService {
    private final JdbcTemplate jdbcTemplate;

    public DatasourceServiceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteDatasource(Integer force, List<String> datasourceIds) {
        if (datasourceIds == null || datasourceIds.isEmpty()) {
            return true;
        }
        List<String> used = usedWorkflowNames(datasourceIds);
        if (!Integer.valueOf(1).equals(force) && !used.isEmpty()) {
            throw new RuntimeException("Datasource is used by workflow: " + String.join(",", used));
        }
        boolean removed = removeByIds(datasourceIds);
        if (removed) {
            jdbcTemplate.update("delete from workflow_datasource where datasource_id in (" + placeholders(datasourceIds.size()) + ")", datasourceIds.toArray());
        }
        return removed;
    }

    @Override
    public boolean updateEnable(String datasourceId, Integer enable) {
        return lambdaUpdate().set(Datasource::getEnabled, toBoolean(enable)).eq(Datasource::getId, datasourceId).update();
    }

    @Override
    public boolean checkConnect(Datasource datasource) {
        try {
            DriverManager.setLoginTimeout(5);
            try (Connection connection = DriverManager.getConnection(jdbcUrl(datasource), datasource.getUsername(), datasource.getPassword());
                 Statement statement = connection.createStatement()) {
                statement.execute(testSql(datasource.getType()));
                return true;
            }
        } catch (Exception e) {
            throw new RuntimeException("Datasource connection failed: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Datasource> listByEnabled(Integer enabled) {
        if (enabled == null) {
            return list();
        }
        return lambdaQuery().eq(Datasource::getEnabled, toBoolean(enabled)).list();
    }

    private Boolean toBoolean(Integer enabled) {
        return enabled != null && enabled == 1;
    }

    private String jdbcUrl(Datasource datasource) {
        DatasourceType type = datasource.getType();
        String host = datasource.getIp();
        String port = datasource.getPort();
        String db = datasource.getDb();
        return switch (type) {
            case MYSQL -> "jdbc:mysql://" + host + ":" + port + "/" + db + "?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai";
            case POSTGRESQL -> "jdbc:postgresql://" + host + ":" + port + "/" + db;
            case ORACLE -> "jdbc:oracle:thin:@" + host + ":" + port + ":" + db;
        };
    }

    private String testSql(DatasourceType type) {
        return type == DatasourceType.ORACLE ? "select 1 from dual" : "select 1";
    }

    private List<String> usedWorkflowNames(List<String> ids) {
        return jdbcTemplate.queryForList(
                "select distinct w.name from workflow_datasource wd join workflow w on w.id = wd.workflow_id where wd.datasource_id in (" + placeholders(ids.size()) + ")",
                String.class,
                ids.toArray()
        );
    }

    private String placeholders(int size) {
        StringJoiner joiner = new StringJoiner(",");
        for (int i = 0; i < size; i++) {
            joiner.add("?");
        }
        return joiner.toString();
    }
}
