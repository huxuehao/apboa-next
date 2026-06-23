package com.hxh.apboa.node.base.db;

import com.hxh.apboa.common.entity.Datasource;
import com.hxh.apboa.common.enums.datasource.DatasourceType;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 描述：数据库执行器工厂
 * 根据数据源配置创建并缓存 DBExecutor 实例，每个 datasourceId 对应一个连接池。
 * 通过策略模式，未来可方便地扩展更多数据库类型。
 *
 * @author huxuehao
 **/
public class DBExecutorFactory {

    /**
     * 执行器缓存：datasourceId -> DBExecutor
     */
    private static final Map<Long, DBExecutor> executorCache = new ConcurrentHashMap<>();

    /**
     * 根据数据源实体获取或创建 DBExecutor
     *
     * @param datasource 数据源实体
     * @return DBExecutor 实例
     */
    public static DBExecutor getExecutor(Datasource datasource) {
        if (datasource == null) {
            throw new RuntimeException("数据源配置不能为空");
        }
        if (datasource.getId() == null) {
            throw new RuntimeException("数据源ID不能为空");
        }

        return executorCache.computeIfAbsent(datasource.getId(), id -> createExecutor(datasource));
    }

    /**
     * 根据数据源类型创建对应的执行器
     */
    private static DBExecutor createExecutor(Datasource datasource) {
        DatasourceType type = datasource.getType();
        if (type == null) {
            throw new RuntimeException("数据源类型不能为空，数据源ID: " + datasource.getId());
        }

        String host = datasource.getIp();
        String port = datasource.getPort();
        String db = datasource.getDb();
        String username = datasource.getUsername();
        String password = datasource.getPassword();
        String config = datasource.getConfig();

        return switch (type) {
            case MYSQL -> new MySQLDBExecutor(host, port, db, username, password, config);
            case ORACLE -> new OracleDBExecutor(host, port, db, username, password, config);
            case POSTGRESQL -> new PostgreSQLDBExecutor(host, port, db, username, password, config);
            default -> throw new RuntimeException("不支持的数据源类型: " + type);
        };
    }

    /**
     * 清除指定数据源的执行器缓存（关闭连接池）
     *
     * @param datasourceId 数据源ID
     */
    public static void evictCache(String datasourceId) {
        DBExecutor executor = executorCache.remove(datasourceId);
        if (executor != null) {
            executor.close();
        }
    }

    /**
     * 清除所有执行器缓存
     */
    public static void evictAll() {
        executorCache.forEach((id, executor) -> executor.close());
        executorCache.clear();
    }
}
