package com.hxh.apboa.cache.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hxh.apboa.cache.mapper.CacheMapper;
import com.hxh.apboa.common.entity.Cache;
import com.hxh.apboa.common.enums.HealthStatus;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.StringJoiner;

@Service
public class CacheServiceImpl extends ServiceImpl<CacheMapper, Cache> implements CacheService {
    private final JdbcTemplate jdbcTemplate;

    public CacheServiceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteCache(Integer force, List<String> cacheIds) {
        if (cacheIds == null || cacheIds.isEmpty()) {
            return true;
        }
        List<String> used = usedWorkflowNames(cacheIds);
        if (!Integer.valueOf(1).equals(force) && !used.isEmpty()) {
            throw new RuntimeException("Cache is used by workflow: " + String.join(",", used));
        }
        boolean removed = removeByIds(cacheIds);
        if (removed) {
            jdbcTemplate.update("delete from workflow_cache where cache_id in (" + placeholders(cacheIds.size()) + ")", cacheIds.toArray());
        }
        return removed;
    }

    @Override
    public boolean updateCache(Cache cache) {
        keepPasswordWhenBlank(cache);
        return updateById(cache);
    }

    @Override
    public boolean updateEnable(String cacheId, Integer enable) {
        return lambdaUpdate().set(Cache::getEnabled, toBoolean(enable)).eq(Cache::getId, cacheId).update();
    }

    @Override
    public boolean checkConnect(Cache cache) {
        LettuceConnectionFactory factory = null;
        try {
            RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
            config.setHostName(cache.getIp());
            config.setPort(cache.getPort() == null ? 6379 : cache.getPort());
            config.setDatabase(cache.getDb() == null ? 0 : cache.getDb());
            if (cache.getUsername() != null && !cache.getUsername().isBlank()) {
                config.setUsername(cache.getUsername());
            }
            if (cache.getPassword() != null && !cache.getPassword().isBlank()) {
                config.setPassword(cache.getPassword());
            }
            factory = new LettuceConnectionFactory(config);
            factory.afterPropertiesSet();
            String pong = factory.getConnection().ping();
            return pong != null && !pong.isBlank();
        } finally {
            if (factory != null) {
                factory.destroy();
            }
        }
    }

    @Override
    public boolean checkSavedConnect(String cacheId) {
        Cache cache = getById(cacheId);
        if (cache == null) {
            throw new RuntimeException("Cache not found");
        }
        try {
            boolean connected = checkConnect(cache);
            markHealth(cacheId, HealthStatus.HEALTHY, null);
            return connected;
        } catch (Exception e) {
            markHealth(cacheId, HealthStatus.UNHEALTHY, e.getMessage());
            throw e;
        }
    }

    @Override
    public List<Cache> listByEnabled(Integer enabled) {
        if (enabled == null) {
            return list();
        }
        return lambdaQuery().eq(Cache::getEnabled, toBoolean(enabled)).list();
    }

    private void keepPasswordWhenBlank(Cache cache) {
        if (cache == null || cache.getId() == null) {
            return;
        }
        if (cache.getPassword() != null && !cache.getPassword().isBlank()) {
            return;
        }
        Cache old = getById(cache.getId());
        if (old != null) {
            cache.setPassword(old.getPassword());
        }
    }

    private void markHealth(String cacheId, HealthStatus status, String message) {
        lambdaUpdate()
                .set(Cache::getHealthStatus, status)
                .set(Cache::getLastHealthCheck, LocalDateTime.now())
                .set(Cache::getLastCheckMessage, trimMessage(message))
                .eq(Cache::getId, cacheId)
                .update();
    }

    private String trimMessage(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= 500 ? message : message.substring(0, 500);
    }

    private Boolean toBoolean(Integer enabled) {
        return enabled != null && enabled == 1;
    }

    private List<String> usedWorkflowNames(List<String> ids) {
        return jdbcTemplate.queryForList(
                "select distinct w.name from workflow_cache wc join workflow w on w.id = wc.workflow_id where wc.cache_id in (" + placeholders(ids.size()) + ")",
                String.class,
                ids.toArray()
        );
    }

    private String placeholders(int size) {
        StringJoiner joiner = new StringJoiner(",");
        for (int i = 0; i < size; i++) {
            joiner.add("?");
        }
        return joiner.toString();
    }
}
