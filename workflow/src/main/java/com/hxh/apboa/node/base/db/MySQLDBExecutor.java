package com.hxh.apboa.node.base.db;

/**
 * 描述：MySQL 数据库执行器
 *
 * @author huxuehao
 **/
public class MySQLDBExecutor extends AbstractHikariDBExecutor {

    public MySQLDBExecutor(String host, String port, String db,
                           String username, String password, String config) {
        super(host, port, db, username, password, config);
    }

    @Override
    protected String buildJdbcUrl(String host, String port, String db) {
        return String.format("jdbc:mysql://%s:%s/%s?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true",
                host, port, db);
    }

    @Override
    protected String getDriverClassName() {
        return "com.mysql.cj.jdbc.Driver";
    }

    @Override
    protected String getConnectionTestQuery() {
        return "SELECT 1";
    }
}
