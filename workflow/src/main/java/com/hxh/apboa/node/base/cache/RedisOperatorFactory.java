package com.hxh.apboa.node.base.cache;

import com.hxh.apboa.common.entity.Cache;
import com.hxh.apboa.common.enums.cache.CacheType;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 描述：Redis 操作器工厂
 * 根据缓存配置创建并缓存 RedisOperator 实例，每个 cacheId 对应一个独立的 Redis 连接。
 * 通过策略模式，未来可方便地扩展其他缓存类型。
 *
 * @author huxuehao
 **/
public class RedisOperatorFactory {

    /**
     * 操作器缓存：cacheId -> RedisOperator
     */
    private static final Map<Long, RedisOperator> operatorCache = new ConcurrentHashMap<>();

    /**
     * 根据缓存实体获取或创建 RedisOperator
     *
     * @param cache 缓存实体
     * @return RedisOperator 实例
     */
    public static RedisOperator getOperator(Cache cache) {
        if (cache == null) {
            throw new RuntimeException("缓存配置不能为空");
        }
        if (cache.getId() == null) {
            throw new RuntimeException("缓存ID不能为空");
        }

        return operatorCache.computeIfAbsent(cache.getId(), id -> createOperator(cache));
    }

    /**
     * 根据缓存类型创建对应的操作器
     */
    private static RedisOperator createOperator(Cache cache) {
        CacheType type = cache.getType();
        if (type == null) {
            throw new RuntimeException("缓存类型不能为空，缓存ID: " + cache.getId());
        }

        String host = cache.getIp();
        int port = cache.getPort() != null ? cache.getPort() : 6379;
        int db = cache.getDb() != null ? cache.getDb() : 0;
        String username = cache.getUsername();
        String password = cache.getPassword();
        String config = cache.getConfig();

        return switch (type) {
            case REDIS -> new RedisTemplateOperator(host, port, db, username, password, config);
            default -> throw new RuntimeException("不支持的缓存类型: " + type);
        };
    }

    /**
     * 清除指定缓存的操作器缓存（关闭连接）
     *
     * @param cacheId 缓存ID
     */
    public static void evictCache(Long cacheId) {
        RedisOperator operator = operatorCache.remove(cacheId);
        if (operator != null) {
            operator.close();
        }
    }

    /**
     * 清除所有操作器缓存
     */
    public static void evictAll() {
        operatorCache.forEach((id, operator) -> operator.close());
        operatorCache.clear();
    }
}
