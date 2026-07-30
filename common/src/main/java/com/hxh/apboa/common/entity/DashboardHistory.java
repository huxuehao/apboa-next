package com.hxh.apboa.common.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hxh.apboa.common.config.mybatis.JsonNodeTypeHandler;
import com.hxh.apboa.common.consts.TableConst;
import com.hxh.apboa.common.mp.annotation.QueryDefine;
import com.hxh.apboa.common.mp.support.QueryCondition;
import lombok.Getter;
import lombok.Setter;

/**
 * 描述：Dashboard 个人历史版本（用户级快照，用于版本回滚）
 * 以 (dashboard_id, created_by) 归属用户，保留最近若干条。
 *
 * @author huxuehao
 **/
@Getter
@Setter
@TableName(value = TableConst.DASHBOARD_HISTORY, autoResultMap = true)
public class DashboardHistory extends BaseTenantEntity {
    /**
     * 关联的模板 ID
     */
    @QueryDefine(condition = QueryCondition.EQ)
    private Long dashboardId;
    /**
     * 版本 DSL 快照
     */
    @TableField(typeHandler = JsonNodeTypeHandler.class)
    private Object config;
    /**
     * 版本说明
     */
    private String note;
}
