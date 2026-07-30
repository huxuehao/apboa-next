package com.hxh.apboa.dashboard.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hxh.apboa.common.entity.DashboardDataset;
import com.hxh.apboa.common.enums.dashboard.DatasetType;
import com.hxh.apboa.common.util.JsonUtils;
import com.hxh.apboa.dashboard.dataset.DatasetExecutionService;
import com.hxh.apboa.dashboard.dataset.model.DatasetExecuteResult;
import com.hxh.apboa.dashboard.dataset.model.DatasetPreviewRequest;
import com.hxh.apboa.dashboard.dataset.model.DatasetQueryRequest;
import com.hxh.apboa.dashboard.dataset.model.HttpDatasetConfig;
import com.hxh.apboa.dashboard.mapper.DashboardDatasetMapper;
import com.hxh.apboa.dashboard.support.DashboardPermission;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 描述：Dashboard 数据集服务实现
 *
 * @author huxuehao
 **/
@Service
public class DashboardDatasetServiceImpl extends ServiceImpl<DashboardDatasetMapper, DashboardDataset>
        implements DashboardDatasetService {
    private final DatasetExecutionService executionService;

    public DashboardDatasetServiceImpl(DatasetExecutionService executionService) {
        this.executionService = executionService;
    }

    @Override
    public DashboardDataset saveDataset(DashboardDataset dataset) {
        DashboardPermission.requireAdmin();
        validateDataset(dataset);
        save(dataset);
        return dataset;
    }

    @Override
    public boolean updateDataset(DashboardDataset dataset) {
        DashboardPermission.requireAdmin();
        validateDataset(dataset);
        return updateById(dataset);
    }

    /** 基本校验：HTTP 型需 url 必填；SQL 型需查询语句必填。SQL 安全校验在执行时进行。 */
    private void validateDataset(DashboardDataset dataset) {
        if (dataset.getType() == DatasetType.HTTP) {
            HttpDatasetConfig config = JsonUtils.objectToBean(dataset.getHttpConfig(), HttpDatasetConfig.class);
            if (config == null || config.getUrl() == null || config.getUrl().isBlank()) {
                throw new RuntimeException("HTTP 数据集需填写请求地址");
            }
        } else if (dataset.getSqlText() == null || dataset.getSqlText().isBlank()) {
            throw new RuntimeException("SQL 数据集需填写查询语句");
        }
    }

    @Override
    public boolean removeDatasets(List<Long> ids) {
        DashboardPermission.requireAdmin();
        if (ids == null || ids.isEmpty()) {
            return true;
        }
        return removeByIds(ids);
    }

    @Override
    public boolean updateEnable(Long datasetId, Integer enable) {
        DashboardPermission.requireAdmin();
        return lambdaUpdate()
                .set(DashboardDataset::getEnabled, enable != null && enable == 1)
                .eq(DashboardDataset::getId, datasetId)
                .update();
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
        return executionService.query(dataset, request);
    }
}
