package com.hxh.apboa.dashboard.dataset;

import com.hxh.apboa.dashboard.dataset.model.DatasetExecuteResult;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 描述：数据集结果短 TTL 缓存（进程内）。key 由 (租户, 数据集, 参数, 行数) 归一化得到。
 *
 * @author huxuehao
 **/
@Component
public class DatasetQueryCache {
    private record Entry(DatasetExecuteResult result, long expireAt) {
    }

    private final Map<String, Entry> cache = new ConcurrentHashMap<>();

    public DatasetExecuteResult get(String key) {
        Entry entry = cache.get(key);
        if (entry == null) {
            return null;
        }
        if (System.currentTimeMillis() > entry.expireAt()) {
            cache.remove(key);
            return null;
        }
        return entry.result();
    }

    public void put(String key, DatasetExecuteResult result, int ttlSeconds) {
        if (ttlSeconds <= 0) {
            return;
        }
        cache.put(key, new Entry(result, System.currentTimeMillis() + ttlSeconds * 1000L));
    }
}
