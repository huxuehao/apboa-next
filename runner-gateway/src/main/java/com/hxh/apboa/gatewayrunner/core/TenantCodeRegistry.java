package com.hxh.apboa.gatewayrunner.core;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 描述：租户编码缓存
 * 数据面执行工作流时需要租户编码构造执行上下文，按租户ID懒加载缓存
 *
 * @author huxuehao
 **/
@Component
@RequiredArgsConstructor
public class TenantCodeRegistry {
    private final JdbcTemplate jdbcTemplate;
    private final Map<Long, String> cache = new ConcurrentHashMap<>();

    /**
     * 获取租户编码
     */
    public String codeOf(Long tenantId) {
        if (tenantId == null) {
            return null;
        }
        return cache.computeIfAbsent(tenantId, id -> {
            List<String> codes = jdbcTemplate.queryForList(
                    "select code from tenant where id = ?", String.class, id);
            return codes.isEmpty() ? null : codes.getFirst();
        });
    }
}
