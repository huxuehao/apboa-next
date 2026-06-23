package com.hxh.apboa.node.base.cache;

import com.hxh.apboa.common.util.JsonUtils;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * 描述：基于 Spring Data Redis + Lettuce 的 Redis 操作器实现
 * 每个实例维护独立的 LettuceConnectionFactory，支持连接不同的 Redis 实例。
 *
 * @author huxuehao
 **/
public class RedisTemplateOperator implements RedisOperator {

    private final StringRedisTemplate redisTemplate;
    private final LettuceConnectionFactory connectionFactory;

    /**
     * 构造 Redis 操作器
     *
     * @param host     Redis 主机地址
     * @param port     Redis 端口
     * @param db       Redis 数据库索引
     * @param username Redis 用户名
     * @param password Redis 密码
     * @param config   扩展配置（JSON 字符串）
     */
    public RedisTemplateOperator(String host, int port, int db,
                                  String username, String password, String config) {
        RedisStandaloneConfiguration redisConfig = new RedisStandaloneConfiguration();
        redisConfig.setHostName(host);
        redisConfig.setPort(port);
        redisConfig.setDatabase(db);
        if (password != null && !password.isEmpty()) {
            redisConfig.setPassword(password);
        }
        if (username != null && !username.isEmpty()) {
            redisConfig.setUsername(username);
        }

        this.connectionFactory = new LettuceConnectionFactory(redisConfig);
        this.connectionFactory.afterPropertiesSet();

        this.redisTemplate = new StringRedisTemplate();
        this.redisTemplate.setConnectionFactory(connectionFactory);
        this.redisTemplate.setKeySerializer(StringRedisSerializer.UTF_8);
        this.redisTemplate.setValueSerializer(StringRedisSerializer.UTF_8);
        this.redisTemplate.afterPropertiesSet();
    }

    @Override
    public String get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    @Override
    public <T> T get(String key, Class<T> clazz) {
        String value = get(key);
        if (value == null) {
            return null;
        }
        return JsonUtils.parse(value, clazz);
    }

    @Override
    public void set(String key, Object value) {
        redisTemplate.opsForValue().set(key, Objects.requireNonNull(JsonUtils.toJsonStr(value)));
    }

    @Override
    public void setEx(String key, Object value, long timeout, TimeUnit unit) {
        redisTemplate.opsForValue().set(key, Objects.requireNonNull(JsonUtils.toJsonStr(value)), timeout, unit);
    }

    @Override
    public void delete(String key) {
        redisTemplate.delete(key);
    }

    @Override
    public boolean expire(String key, long timeout, TimeUnit unit) {
        return redisTemplate.expire(key, timeout, unit);
    }

    @Override
    public boolean hasKey(String key) {
        return redisTemplate.hasKey(key);
    }

    @Override
    public void close() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }
}
