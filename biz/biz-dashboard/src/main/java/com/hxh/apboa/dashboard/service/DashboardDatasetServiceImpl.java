package com.hxh.apboa.dashboard.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hxh.apboa.common.entity.DashboardDataset;
import com.hxh.apboa.common.enums.dashboard.DatasetType;
import com.hxh.apboa.common.util.JsonUtils;
import com.hxh.apboa.common.util.UserUtils;
import com.hxh.apboa.dashboard.dataset.DatasetExecutionService;
import com.hxh.apboa.dashboard.dataset.guard.SqlSecurityValidator;
import com.hxh.apboa.dashboard.dataset.guard.TenantPredicateRewriter;
import com.hxh.apboa.dashboard.dataset.model.DatasetExecuteResult;
import com.hxh.apboa.dashboard.dataset.model.DatasetPreviewRequest;
import com.hxh.apboa.dashboard.dataset.model.DatasetQueryRequest;
import com.hxh.apboa.dashboard.dataset.model.HttpDatasetConfig;
import com.hxh.apboa.dashboard.mapper.DashboardDatasetMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * 描述：Dashboard 数据集服务实现。数据集归属创建人，可选租户内共享；
 * 非创建人对共享数据集仅可查看/使用/预览，不可修改、删除、启停。
 *
 * @author huxuehao
 **/
@Service
public class DashboardDatasetServiceImpl extends ServiceImpl<DashboardDatasetMapper, DashboardDataset>
        implements DashboardDatasetService {
    private final DatasetExecutionService executionService;
    private final SqlSecurityValidator sqlValidator;
    private final TenantPredicateRewriter tenantRewriter;

    public DashboardDatasetServiceImpl(DatasetExecutionService executionService,
                                       SqlSecurityValidator sqlValidator,
                                       TenantPredicateRewriter tenantRewriter) {
        this.executionService = executionService;
        this.sqlValidator = sqlValidator;
        this.tenantRewriter = tenantRewriter;
    }

    @Override
    public DashboardDataset saveDataset(DashboardDataset dataset) {
        validateDataset(dataset);
        // 防越权：租户与归属一律由后端填充，忽略前端传入值
        dataset.setId(null);
        dataset.setTenantId(null);
        dataset.setCreatedBy(null);
        save(dataset);
        return dataset;
    }

    @Override
    public boolean updateDataset(DashboardDataset dataset) {
        requireOwned(dataset.getId());
        validateDataset(dataset);
        // 防越权：禁止篡改归属与租户
        dataset.setTenantId(null);
        dataset.setCreatedBy(null);
        return updateById(dataset);
    }

    /**
     * 基本校验 + SQL 安全预检：HTTP 型需 url 必填；SQL 型需查询语句必填，
     * 并提前执行安全校验与租户改写，让越权/黑名单/解析错误在保存时即暴露。
     */
    private void validateDataset(DashboardDataset dataset) {
        if (dataset.getType() == DatasetType.HTTP) {
            HttpDatasetConfig config = JsonUtils.objectToBean(dataset.getHttpConfig(), HttpDatasetConfig.class);
            if (config == null || config.getUrl() == null || config.getUrl().isBlank()) {
                throw new RuntimeException("HTTP 数据集需填写请求地址");
            }
            return;
        }
        if (dataset.getSqlText() == null || dataset.getSqlText().isBlank()) {
            throw new RuntimeException("SQL 数据集需填写查询语句");
        }
        // 保存时安全预检：安全校验 + 租户改写（异常原样抛出）
        tenantRewriter.rewrite(sqlValidator.validate(dataset.getSqlText()));
    }

    @Override
    public boolean removeDatasets(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return true;
        }
        ids.forEach(this::requireOwned);
        return removeByIds(ids);
    }

    @Override
    public boolean updateEnable(Long datasetId, Integer enable) {
        requireOwned(datasetId);
        return lambdaUpdate()
                .set(DashboardDataset::getEnabled, enable != null && enable == 1)
                .eq(DashboardDataset::getId, datasetId)
                .update();
    }

    /** 取数据集并断言当前用户为创建人，否则拒绝（getById 已受租户拦截器隔离） */
    private DashboardDataset requireOwned(Long id) {
        DashboardDataset dataset = getById(id);
        if (dataset == null) {
            throw new RuntimeException("数据集不存在");
        }
        if (!Objects.equals(dataset.getCreatedBy(), UserUtils.getId())) {
            throw new RuntimeException("只有创建人可以操作该数据集");
        }
        return dataset;
    }

    @Override
    public IPage<DashboardDataset> pageVisible(IPage<DashboardDataset> page, QueryWrapper<DashboardDataset> query) {
        applyVisibility(query);
        return page(page, query);
    }

    @Override
    public List<DashboardDataset> listVisible(QueryWrapper<DashboardDataset> query) {
        applyVisibility(query);
        return list(query);
    }

    /** 可见性过滤：自建 或 已共享 */
    private void applyVisibility(QueryWrapper<DashboardDataset> wrapper) {
        Long uid = UserUtils.getId();
        wrapper.and(w -> w.eq("created_by", uid).or().eq("shared", true));
    }

    @Override
    public DatasetExecuteResult preview(DatasetPreviewRequest request) {
        return executionService.preview(request);
    }

    @Override
    public DatasetExecuteResult queryById(Long datasetId, DatasetQueryRequest request) {
        DashboardDataset dataset = getById(datasetId);
        if (dataset == null) {
            throw new RuntimeException("数据集不存在");
        }
        // 可用性校验：仅创建人自己或已共享的数据集可被取数
        boolean shared = Boolean.TRUE.equals(dataset.getShared());
        if (!shared && !Objects.equals(dataset.getCreatedBy(), UserUtils.getId())) {
            throw new RuntimeException("无权使用该数据集");
        }
        return executionService.query(dataset, request);
    }
}
