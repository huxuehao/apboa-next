package com.hxh.apboa.common.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hxh.apboa.common.consts.TableConst;
import com.hxh.apboa.common.mp.annotation.QueryDefine;
import com.hxh.apboa.common.mp.support.QueryCondition;
import lombok.Getter;
import lombok.Setter;

/**
 * 描述：quartz执行状态实体类
 * @author huxuehao
 **/
@Getter
@Setter
@TableName(TableConst.JOB_INFO)
public class JobInfo extends BaseTenantEntity {
    /*关联业务ID*/
    @QueryDefine(value = "类型", condition = QueryCondition.EQ)
    private String type;
    /*关联业务ID*/
    private String bizId;
    /*corn*/
    private String cron;
    /*执行类*/
    private String jobClass;
    /*执行参数*/
    private String dataMap;
}
