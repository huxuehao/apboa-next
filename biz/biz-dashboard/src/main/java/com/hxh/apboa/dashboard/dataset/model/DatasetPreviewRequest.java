package com.hxh.apboa.dashboard.dataset.model;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

/**
 * 描述：数据集预览执行请求（设计器/数据集编辑页即席预览）
 *
 * @author huxuehao
 **/
@Getter
@Setter
public class DatasetPreviewRequest {
    /**
     * 查询语句（仅允许 SELECT）
     */
    private String sql;
    /**
     * 命名参数
     */
    private Map<String, Object> params;
    /**
     * 行数上限，可空则使用默认预览上限
     */
    private Integer limit;
    /**
     * 绑定的外部数据源 ID，为空表示主库
     */
    private Long datasourceId;
}
