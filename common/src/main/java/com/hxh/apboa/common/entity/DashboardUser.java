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
 * 描述：Dashboard 个人覆盖（用户级快照）
 * 以 (dashboard_id, created_by) 唯一，恢复默认即删除该行回退到模板。
 *
 * @author huxuehao
 **/
@Getter
@Setter
@TableName(value = TableConst.DASHBOARD_USER, autoResultMap = true)
public class DashboardUser extends BaseTenantEntity {
    /**
     * 关联的模板 ID
     */
    @QueryDefine(condition = QueryCondition.EQ)
    private Long dashboardId;
    /**
     * 个人副本 DSL 快照
     */
    @TableField(typeHandler = JsonNodeTypeHandler.class)
    private Object config;
    /**
     * 克隆时所基于的模板版本，用于判断模板是否已升级
     */
    private String basedVersion;
}
