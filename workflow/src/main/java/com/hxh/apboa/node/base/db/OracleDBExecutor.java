package com.hxh.apboa.node.base.db;

/**
 * 描述：Oracle 数据库执行器
 *
 * @author huxuehao
 **/
public class OracleDBExecutor extends AbstractHikariDBExecutor {

    public OracleDBExecutor(String host, String port, String db,
                            String username, String password, String config) {
        super(host, port, db, username, password, config);
    }

    @Override
    protected String buildJdbcUrl(String host, String port, String db) {
        return String.format("jdbc:oracle:thin:@//%s:%s/%s", host, port, db);
    }

    @Override
    protected String getDriverClassName() {
        return "oracle.jdbc.OracleDriver";
    }

    @Override
    protected String getConnectionTestQuery() {
        return "SELECT 1 FROM DUAL";
    }
}
