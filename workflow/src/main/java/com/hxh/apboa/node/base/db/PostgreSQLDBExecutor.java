package com.hxh.apboa.node.base.db;

/**
 * 描述：PostgreSQL 数据库执行器
 *
 * @author huxuehao
 **/
public class PostgreSQLDBExecutor extends AbstractHikariDBExecutor {

    public PostgreSQLDBExecutor(String host, String port, String db,
                                String username, String password, String config) {
        super(host, port, db, username, password, config);
    }

    @Override
    protected String buildJdbcUrl(String host, String port, String db) {
        return String.format("jdbc:postgresql://%s:%s/%s", host, port, db);
    }

    @Override
    protected String getDriverClassName() {
        return "org.postgresql.Driver";
    }

    @Override
    protected String getConnectionTestQuery() {
        return "SELECT 1";
    }
}
