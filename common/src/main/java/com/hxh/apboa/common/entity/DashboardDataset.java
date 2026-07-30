package com.hxh.apboa.common.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hxh.apboa.common.config.mybatis.JsonNodeTypeHandler;
import com.hxh.apboa.common.consts.TableConst;
import com.hxh.apboa.common.enums.dashboard.DatasetType;
import com.hxh.apboa.common.mp.annotation.QueryDefine;
import com.hxh.apboa.common.mp.support.QueryCondition;
import lombok.Getter;
import lombok.Setter;

/**
 * 描述：Dashboard 数据集（租户级共享，仅查询语句）
 *
 * @author huxuehao
 **/
@Getter
@Setter
@TableName(value = TableConst.DASHBOARD_DATASET, autoResultMap = true)
public class DashboardDataset extends BaseTenantEntity {
    /**
     * 数据集名称
     */
    @QueryDefine(condition = QueryCondition.LIKE)
    private String name;
    /**
     * 数据集描述
     */
    private String remark;
    /**
     * 数据集类型（SQL / HTTP），为空时按 SQL 处理
     */
    @QueryDefine(condition = QueryCondition.EQ)
    private DatasetType type;
    /**
     * 查询语句（仅允许 SELECT），列名 sql 为保留字故映射为 sql_text
     */
    @TableField("sql_text")
    private String sqlText;
    /**
     * 参数声明（名称、类型、默认值等）
     */
    @TableField(typeHandler = JsonNodeTypeHandler.class)
    private Object params;
    /**
     * 缓存的结果列信息（列名、类型），供字段映射使用
     */
    @TableField(value = "result_schema", typeHandler = JsonNodeTypeHandler.class)
    private Object resultSchema;
    /**
     * 结果缓存时长（秒），为空或 0 表示不缓存
     */
    private Integer cacheTtl;
    /**
     * 绑定的外部数据源 ID，为空表示查询应用主库（MVP 默认）
     */
    @QueryDefine(condition = QueryCondition.EQ)
    private Long datasourceId;
    /**
     * HTTP 数据集配置（url、queries、headers、dataPath），仅 type=HTTP 时使用
     */
    @TableField(value = "http_config", typeHandler = JsonNodeTypeHandler.class)
    private Object httpConfig;
}
