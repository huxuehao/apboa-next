package com.hxh.apboa.dashboard.dataset.model;

import com.hxh.apboa.common.enums.dashboard.DatasetType;
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
     * 数据集类型（SQL / HTTP），为空按 SQL 处理
     */
    private DatasetType type;
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
    /**
     * HTTP 数据集配置（type=HTTP 时）
     */
    private Object httpConfig;
    /**
     * 调用方浏览器 Origin（由 Controller 从请求头回填）
     */
    private String callerOrigin;
    /**
     * 调用方平台 Authorization（由 Controller 从请求头回填，仅同源转发）
     */
    private String callerToken;
}
