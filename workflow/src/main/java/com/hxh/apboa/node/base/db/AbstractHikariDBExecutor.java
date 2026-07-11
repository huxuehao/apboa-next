package com.hxh.apboa.node.base.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 描述：基于 HikariCP 连接池的抽象数据库执行器
 * 提供通用的 JDBC 操作实现，子类只需提供驱动类名和连接 URL 构建逻辑。
 *
 * @author huxuehao
 **/
public abstract class AbstractHikariDBExecutor implements DBExecutor {

    protected final HikariDataSource dataSource;

    /**
     * 构造执行器并初始化 HikariCP 连接池
     *
     * @param host     数据库主机地址
     * @param port     数据库端口
     * @param db       数据库名称
     * @param username 用户名
     * @param password 密码
     * @param config   扩展配置（JSON 字符串，可包含连接池参数）
     */
    public AbstractHikariDBExecutor(String host, String port, String db,
                                     String username, String password, String config) {
        HikariConfig hikariConfig = new HikariConfig();

        // 设置 JDBC URL
        hikariConfig.setJdbcUrl(buildJdbcUrl(host, port, db));
        hikariConfig.setUsername(username);
        hikariConfig.setPassword(password);
        hikariConfig.setDriverClassName(getDriverClassName());

        // 连接池默认配置
        hikariConfig.setMaximumPoolSize(10);
        hikariConfig.setMinimumIdle(2);
        hikariConfig.setConnectionTimeout(30000);
        hikariConfig.setIdleTimeout(600000);
        hikariConfig.setMaxLifetime(1800000);
        hikariConfig.setConnectionTestQuery(getConnectionTestQuery());

        // 允许从扩展配置中覆盖连接池参数
        applyExtraConfig(hikariConfig, config);

        this.dataSource = new HikariDataSource(hikariConfig);
    }

    /**
     * 构建 JDBC URL
     */
    protected abstract String buildJdbcUrl(String host, String port, String db);

    /**
     * 获取驱动类名
     */
    protected abstract String getDriverClassName();

    /**
     * 获取连接测试 SQL
     */
    protected abstract String getConnectionTestQuery();

    /**
     * 从扩展配置中解析并应用额外参数
     */
    protected void applyExtraConfig(HikariConfig hikariConfig, String config) {
        // 默认空实现，子类可覆写
    }

    @Override
    public List<Map<String, Object>> select(String sql, List<Object> params) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            setParameters(ps, params);

            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, Object>> results = new ArrayList<>();
                ResultSetMetaData metaData = rs.getMetaData();
                int columnCount = metaData.getColumnCount();

                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= columnCount; i++) {
                        row.put(metaData.getColumnLabel(i), rs.getObject(i));
                    }
                    results.add(row);
                }
                return results;
            }
        } catch (SQLException e) {
            throw new RuntimeException("数据库查询失败: " + e.getMessage(), e);
        }
    }

    @Override
    public int insert(String sql, List<Object> params) {
        return executeUpdate(sql, params);
    }

    @Override
    public int update(String sql, List<Object> params) {
        return executeUpdate(sql, params);
    }

    @Override
    public int delete(String sql, List<Object> params) {
        return executeUpdate(sql, params);
    }

    /**
     * 执行写操作（INSERT / UPDATE / DELETE）
     */
    protected int executeUpdate(String sql, List<Object> params) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            setParameters(ps, params);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("数据库写操作失败: " + e.getMessage(), e);
        }
    }

    /**
     * 设置 PreparedStatement 参数
     */
    protected void setParameters(PreparedStatement ps, List<Object> params) throws SQLException {
        if (params != null) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
        }
    }

    @Override
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
