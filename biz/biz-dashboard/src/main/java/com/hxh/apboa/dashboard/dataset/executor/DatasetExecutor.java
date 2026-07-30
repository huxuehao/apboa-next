package com.hxh.apboa.dashboard.dataset.executor;

import com.hxh.apboa.dashboard.dataset.model.DatasetExecuteCommand;
import com.hxh.apboa.dashboard.dataset.model.DatasetExecuteResult;

/**
 * 描述：数据集执行器策略。按数据源类型选择实现，为后续接入外部数据源/服务型数据集预留扩展点。
 *
 * @author huxuehao
 **/
public interface DatasetExecutor {
    /**
     * 是否支持给定的数据源（datasourceId 为空表示主库）
     */
    boolean supports(Long datasourceId);

    /**
     * 执行数据集查询
     */
    DatasetExecuteResult execute(DatasetExecuteCommand command);
}
