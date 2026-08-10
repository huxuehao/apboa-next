package com.hxh.apboa.common.util;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * 描述：Redis 工具类
 *
 * @author huxuehao
 **/
@Component
public class RedisUtils {

    private final StringRedisTemplate stringRedisTemplate;

    public RedisUtils(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 设置缓存（无过期时间）
     * @param key 键
     * @param value 值
     */
    public void set(String key, Object value) {
        stringRedisTemplate.opsForValue().set(key, JsonUtils.toJsonStr(value));
    }

    /**
     * 设置缓存（无过期时间）
     * @param key 键
     * @param value 值
     */
    public void set(String key, String value) {
        stringRedisTemplate.opsForValue().set(key, value);
    }

    /**
     * 设置缓存（带过期时间）
     * @param key 键
     * @param value 值
     * @param timeout 过期时间
     * @param unit 时间单位
     */
    public void setEx(String key, Object value, long timeout, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JsonUtils.toJsonStr(value), timeout, unit);
    }

    /**
     * 缓存字符串
     * @param key 键
     * @param value 值
     * @param timeout 过期时间
     * @param unit 时间单位
     */
    public void setEx(String key, String value, long timeout, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, value, timeout, unit);
    }

    /**
     * 获取缓存
     * @param key 键
     * @return 字符串值
     */
    public String get(String key) {
        return stringRedisTemplate.opsForValue().get(key);
    }

    /**
     * 获取缓存并反序列化为指定对象
     * @param key 键
     * @param clazz 目标对象的类
     * @param <T> 泛型
     * @return 反序列化后的对象
     */
    public <T> T get(String key, Class<T> clazz) {
        String valueStr = get(key);
        if (valueStr == null) {
            return null;
        }
        return JsonUtils.parse(valueStr, clazz);
    }

    /**
     * 删除缓存
     * @param key 键
     */
    public void delete(String key) {
        stringRedisTemplate.delete(key);
    }

    /**
     * 判断缓存是否存在
     * @param key 键
     */
    public boolean hasKey(String key) {
        return stringRedisTemplate.hasKey(key);
    }

    /**
     * 原子递增数值缓存。
     *
     * @param key 键
     * @return 递增后的值
     */
    public long increment(String key) {
        Long value = stringRedisTemplate.opsForValue().increment(key);
        return value == null ? 0L : value;
    }

    /**
     * 尝试获取分布式锁（基于 SETNX + 过期时间）
     * @param key 锁的 Key
     * @param value 锁持有者标识（建议使用 UUID，用于释放时校验）
     * @param timeout 锁超时时间
     * @param unit 时间单位
     * @return true=获取锁成功，false=锁已被其他节点持有
     */
    public boolean tryLock(String key, String value, long timeout, TimeUnit unit) {
        Boolean success = stringRedisTemplate.opsForValue()
                .setIfAbsent(key, value, timeout, unit);
        return Boolean.TRUE.equals(success);
    }

    /**
     * 释放分布式锁（Lua 脚本原子校验 value，防止误删他人锁）
     * @param key 锁的 Key
     * @param value 锁持有者标识（须与 tryLock 时的 value 一致）
     * @return true=释放成功，false=锁已过期或被其他节点持有
     */
    public boolean unlock(String key, String value) {
        String script = "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                        "return redis.call('del', KEYS[1]) " +
                        "else return 0 end";
        DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>(script, Long.class);
        Long result = stringRedisTemplate.execute(redisScript, Collections.singletonList(key), value);
        return Long.valueOf(1).equals(result);
    }
}
