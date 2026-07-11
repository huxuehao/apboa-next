package com.hxh.apboa.node.base.cache;

import java.util.concurrent.TimeUnit;

/**
 * 描述：Redis 操作器接口
 * 定义统一的 Redis 操作抽象，通过策略模式支持不同 Redis 部署方式的扩展。
 *
 * @author huxuehao
 **/
public interface RedisOperator {

    /**
     * 获取缓存值（字符串）
     *
     * @param key 键
     * @return 字符串值，不存在则返回 null
     */
    String get(String key);

    /**
     * 获取缓存值并反序列化为指定类型
     *
     * @param key   键
     * @param clazz 目标类型
     * @param <T>   泛型
     * @return 反序列化后的对象，不存在则返回 null
     */
    <T> T get(String key, Class<T> clazz);

    /**
     * 设置缓存（无过期时间）
     *
     * @param key   键
     * @param value 值
     */
    void set(String key, Object value);

    /**
     * 设置缓存（带过期时间）
     *
     * @param key     键
     * @param value   值
     * @param timeout 过期时间数值
     * @param unit    时间单位
     */
    void setEx(String key, Object value, long timeout, TimeUnit unit);

    /**
     * 删除缓存
     *
     * @param key 键
     */
    void delete(String key);

    /**
     * 设置键的过期时间
     *
     * @param key     键
     * @param timeout 过期时间数值
     * @param unit    时间单位
     * @return 设置成功返回 true
     */
    boolean expire(String key, long timeout, TimeUnit unit);

    /**
     * 判断键是否存在
     *
     * @param key 键
     * @return 存在返回 true
     */
    boolean hasKey(String key);

    /**
     * 关闭操作器，释放连接资源
     */
    void close();
}
