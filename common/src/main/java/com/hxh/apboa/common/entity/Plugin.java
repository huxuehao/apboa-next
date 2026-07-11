package com.hxh.apboa.common.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hxh.apboa.common.consts.TableConst;
import com.hxh.apboa.common.mp.annotation.QueryDefine;
import com.hxh.apboa.common.mp.support.QueryCondition;
import lombok.Getter;
import lombok.Setter;

/**
 * 描述：插件表
 *
 * @author huxuehao
 **/
@Getter
@Setter
@TableName(TableConst.PLUGIN)
public class Plugin extends BaseTenantEntity {
    /**
     * 插件名称
     */
    @QueryDefine(condition = QueryCondition.LIKE)
    private String name;
    /**
     * 插件描述
     */
    private String remark;
    /**
     * 插件类型
     * 1:系统插件 2:用户插件
     */
    @QueryDefine(condition = QueryCondition.EQ)
    private Integer type;
    /**
     * 插件内容
     */
    private String content;
    /**
     * 插件版本
     */
    @QueryDefine(condition = QueryCondition.EQ)
    private Integer version;
}
