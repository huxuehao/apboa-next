package com.hxh.apboa.dashboard.dataset.model;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

/**
 * 描述：数据集执行命令（内部统一入参）
 *
 * @author huxuehao
 **/
@Getter
@Setter
public class DatasetExecuteCommand {
    /**
     * 查询语句
     */
    private String sql;
    /**
     * 用户提供的命名参数
     */
    private Map<String, Object> params;
    /**
     * 行数上限
     */
    private int limit;
    /**
     * 绑定的外部数据源 ID，为空表示主库
     */
    private Long datasourceId;
}
