package com.hxh.apboa.node.base.db;

import java.util.List;
import java.util.Map;

/**
 * 描述：数据库执行器接口
 * 定义统一的数据库操作抽象，不同数据库类型（MySQL、Oracle、PostgreSQL等）
 * 分别实现此接口，通过策略模式支持多数据源类型的扩展。
 *
 * @author huxuehao
 **/
public interface DBExecutor {

    /**
     * 执行查询操作
     *
     * @param sql    SQL 语句（支持参数占位符 ?）
     * @param params 参数列表，按顺序对应 SQL 中的占位符
     * @return 查询结果集，每行为一个 Map（列名 -> 值）
     */
    List<Map<String, Object>> select(String sql, List<Object> params);

    /**
     * 执行插入操作
     *
     * @param sql    SQL 语句（支持参数占位符 ?）
     * @param params 参数列表，按顺序对应 SQL 中的占位符
     * @return 受影响的行数
     */
    int insert(String sql, List<Object> params);

    /**
     * 执行更新操作
     *
     * @param sql    SQL 语句（支持参数占位符 ?）
     * @param params 参数列表，按顺序对应 SQL 中的占位符
     * @return 受影响的行数
     */
    int update(String sql, List<Object> params);

    /**
     * 执行删除操作
     *
     * @param sql    SQL 语句（支持参数占位符 ?）
     * @param params 参数列表，按顺序对应 SQL 中的占位符
     * @return 受影响的行数
     */
    int delete(String sql, List<Object> params);

    /**
     * 关闭执行器，释放连接池资源
     */
    void close();
}
