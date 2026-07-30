package com.hxh.apboa.dashboard.dataset.guard;

import net.sf.jsqlparser.statement.Statement;

import java.util.Set;

/**
 * 描述：SQL 安全校验上下文，SQL 仅解析一次并在责任链中复用
 *
 * @author huxuehao
 **/
public class SqlGuardContext {
    /**
     * 清洗后的 SQL（已去除结尾分号）
     */
    private final String sql;
    /**
     * 解析后的语句
     */
    private final Statement statement;
    /**
     * SQL 引用的全部表/视图名（小写）
     */
    private final Set<String> tables;

    public SqlGuardContext(String sql, Statement statement, Set<String> tables) {
        this.sql = sql;
        this.statement = statement;
        this.tables = tables;
    }

    public String getSql() {
        return sql;
    }

    public Statement getStatement() {
        return statement;
    }

    public Set<String> getTables() {
        return tables;
    }
}
