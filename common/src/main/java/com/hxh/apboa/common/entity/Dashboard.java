package com.hxh.apboa.common.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hxh.apboa.common.config.mybatis.JsonNodeTypeHandler;
import com.hxh.apboa.common.consts.TableConst;
import com.hxh.apboa.common.enums.dashboard.DashboardStatus;
import com.hxh.apboa.common.mp.annotation.QueryDefine;
import com.hxh.apboa.common.mp.support.QueryCondition;
import lombok.Getter;
import lombok.Setter;

/**
 * 描述：Dashboard 模板（租户级定义）
 *
 * @author huxuehao
 **/
@Getter
@Setter
@TableName(value = TableConst.DASHBOARD, autoResultMap = true)
public class Dashboard extends BaseTenantEntity {
    /**
     * 模板名称
     */
    @QueryDefine(condition = QueryCondition.LIKE)
    private String name;
    /**
     * 模板描述
     */
    private String remark;
    /**
     * 模板状态
     */
    @QueryDefine(condition = QueryCondition.EQ)
    private DashboardStatus status;
    /**
     * 是否为租户默认模板
     */
    @TableField("is_default")
    private Boolean isDefault;
    /**
     * 版本号，模板每次发布递增，用于个人副本判断是否落后
     */
    @QueryDefine(condition = QueryCondition.EQ)
    private String version;
    /**
     * Dashboard DSL 配置（布局、面板、全局刷新等）
     */
    @TableField(typeHandler = JsonNodeTypeHandler.class)
    private Object config;
}
