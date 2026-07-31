package com.hxh.apboa.dashboard.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.hxh.apboa.common.entity.DashboardDataset;
import com.hxh.apboa.dashboard.dataset.model.DatasetExecuteResult;
import com.hxh.apboa.dashboard.dataset.model.DatasetPreviewRequest;
import com.hxh.apboa.dashboard.dataset.model.DatasetQueryRequest;

import java.util.List;

/**
 * 描述：Dashboard 数据集服务
 *
 * @author huxuehao
 **/
public interface DashboardDatasetService extends IService<DashboardDataset> {
    /**
     * 新增数据集（登录即可，归属当前用户）
     */
    DashboardDataset saveDataset(DashboardDataset dataset);

    /**
     * 更新数据集（仅创建人）
     */
    boolean updateDataset(DashboardDataset dataset);

    /**
     * 删除数据集（仅创建人）
     */
    boolean removeDatasets(List<Long> ids);

    /**
     * 启停数据集（仅创建人）
     */
    boolean updateEnable(Long datasetId, Integer enable);

    /**
     * 分页查询（仅返回自建或已共享的数据集）
     */
    IPage<DashboardDataset> pageVisible(IPage<DashboardDataset> page, QueryWrapper<DashboardDataset> query);

    /**
     * 列表查询（仅返回自建或已共享的数据集）
     */
    List<DashboardDataset> listVisible(QueryWrapper<DashboardDataset> query);

    /**
     * 即席预览执行
     */
    DatasetExecuteResult preview(DatasetPreviewRequest request);

    /**
     * 按已保存数据集取数
     */
    DatasetExecuteResult queryById(Long datasetId, DatasetQueryRequest request);
}
