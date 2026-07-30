package com.hxh.apboa.dashboard.dataset.model;

import com.hxh.apboa.common.enums.dashboard.DatasetType;
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
     * 数据集类型（null 按 SQL 处理）
     */
    private DatasetType type;
    /**
     * 查询语句（SQL 类型）
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
    /**
     * HTTP 数据集配置（HTTP 类型）
     */
    private Object httpConfig;
    /**
     * 调用方浏览器 Origin（用于同源 token 判定）
     */
    private String callerOrigin;
    /**
     * 调用方携带的平台 Authorization（仅同源时转发）
     */
    private String callerToken;
}
