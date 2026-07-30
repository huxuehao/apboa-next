package com.hxh.apboa.dashboard.dataset;

import com.hxh.apboa.common.entity.DashboardDataset;
import com.hxh.apboa.common.util.JsonUtils;
import com.hxh.apboa.common.util.TenantUtils;
import com.hxh.apboa.dashboard.config.DashboardDatasetProperties;
import com.hxh.apboa.dashboard.dataset.executor.DatasetExecutorFactory;
import com.hxh.apboa.dashboard.dataset.model.DatasetExecuteCommand;
import com.hxh.apboa.dashboard.dataset.model.DatasetExecuteResult;
import com.hxh.apboa.dashboard.dataset.model.DatasetPreviewRequest;
import com.hxh.apboa.dashboard.dataset.model.DatasetQueryRequest;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 描述：数据集执行门面。统一收口"预览执行"与"面板取数"，提供缓存、单飞与单租户并发治理。
 *
 * @author huxuehao
 **/
@Service
public class DatasetExecutionService {
    private final DatasetExecutorFactory executorFactory;
    private final DashboardDatasetProperties properties;
    private final DatasetQueryCache cache;

    private final Map<Long, Semaphore> tenantSemaphores = new ConcurrentHashMap<>();
    private final Map<String, Object> keyLocks = new ConcurrentHashMap<>();

    public DatasetExecutionService(DatasetExecutorFactory executorFactory,
                                   DashboardDatasetProperties properties,
                                   DatasetQueryCache cache) {
        this.executorFactory = executorFactory;
        this.properties = properties;
        this.cache = cache;
    }

    /**
     * 即席预览执行，不走缓存
     */
    public DatasetExecuteResult preview(DatasetPreviewRequest request) {
        int limit = request.getLimit() == null ? properties.getPreviewLimit() : request.getLimit();
        DatasetExecuteCommand command = new DatasetExecuteCommand();
        command.setType(request.getType());
        command.setSql(request.getSql());
        command.setParams(request.getParams());
        command.setLimit(limit);
        command.setDatasourceId(request.getDatasourceId());
        command.setHttpConfig(request.getHttpConfig());
        command.setCallerOrigin(request.getCallerOrigin());
        command.setCallerToken(request.getCallerToken());
        return withConcurrency(() -> executorFactory.resolve(request.getType()).execute(command));
    }

    /**
     * 按已保存数据集执行，带短 TTL 缓存与单飞
     */
    public DatasetExecuteResult query(DashboardDataset dataset, DatasetQueryRequest request) {
        int limit = request.getLimit() == null ? properties.getQueryLimit() : request.getLimit();
        int ttl = dataset.getCacheTtl() == null ? 0 : dataset.getCacheTtl();
        String key = buildCacheKey(dataset.getId(), request.getParams(), limit);

        if (ttl > 0) {
            DatasetExecuteResult cached = cache.get(key);
            if (cached != null) {
                return cached;
            }
        }
        Object lock = keyLocks.computeIfAbsent(key, k -> new Object());
        try {
            synchronized (lock) {
                if (ttl > 0) {
                    DatasetExecuteResult cached = cache.get(key);
                    if (cached != null) {
                        return cached;
                    }
                }
                DatasetExecuteCommand command = new DatasetExecuteCommand();
                command.setType(dataset.getType());
                command.setSql(dataset.getSqlText());
                command.setParams(request.getParams());
                command.setLimit(limit);
                command.setDatasourceId(dataset.getDatasourceId());
                command.setHttpConfig(dataset.getHttpConfig());
                command.setCallerOrigin(request.getCallerOrigin());
                command.setCallerToken(request.getCallerToken());
                DatasetExecuteResult result = withConcurrency(
                        () -> executorFactory.resolve(dataset.getType()).execute(command));
                cache.put(key, result, ttl);
                return result;
            }
        } finally {
            keyLocks.remove(key);
        }
    }

    private String buildCacheKey(Long datasetId, Map<String, Object> params, int limit) {
        Long tenantId = TenantUtils.getCurrentTenantId();
        return tenantId + ":" + datasetId + ":" + limit + ":" + JsonUtils.toJsonStr(params);
    }

    private <T> T withConcurrency(Supplier<T> action) {
        Long tenantId = TenantUtils.getCurrentTenantId();
        Semaphore semaphore = tenantSemaphores.computeIfAbsent(
                tenantId == null ? 0L : tenantId,
                k -> new Semaphore(properties.getMaxConcurrentPerTenant()));
        boolean acquired = false;
        try {
            acquired = semaphore.tryAcquire(properties.getQueryTimeoutSeconds(), TimeUnit.SECONDS);
            if (!acquired) {
                throw new RuntimeException("数据集查询繁忙，请稍后重试");
            }
            return action.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("数据集查询被中断");
        } finally {
            if (acquired) {
                semaphore.release();
            }
        }
    }
}
