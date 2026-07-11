package com.hxh.apboa.common.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hxh.apboa.common.consts.TableConst;
import com.hxh.apboa.common.enums.HealthStatus;
import com.hxh.apboa.common.enums.datasource.DatasourceType;
import com.hxh.apboa.common.mp.annotation.QueryDefine;
import com.hxh.apboa.common.mp.support.QueryCondition;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 描述：数据源表
 *
 * @author huxuehao
 **/
@Getter
@Setter
@TableName(TableConst.DATASOURCE)
public class Datasource extends BaseTenantEntity {
    /**
     * 数据源名称
     */
    @QueryDefine(condition = QueryCondition.LIKE)
    private String name;
    /**
     * 数据源描述
     */
    private String remark;
    /**
     * 数据源类型
     */
    @QueryDefine(condition = QueryCondition.EQ)
    private DatasourceType type;
    /**
     * 数据源IP
     */
    @QueryDefine(condition = QueryCondition.LIKE)
    private String ip;
    /**
     * 数据源端口
     */
    @QueryDefine(condition = QueryCondition.EQ)
    private String port;
    /**
     * 数据库名称
     */
    @QueryDefine(condition = QueryCondition.LIKE)
    private String db;
    /**
     * 数据库配置
     */
    private String config;
    /**
     * 数据库用户名
     */
    @QueryDefine(condition = QueryCondition.LIKE)
    private String username;
    /**
     * 数据库密码
     */
    @QueryDefine(condition = QueryCondition.LIKE)
    private String password;

    /**
     * 健康状态
     */
    @QueryDefine(condition = QueryCondition.EQ)
    private HealthStatus healthStatus;

    /**
     * 最后一次健康检查时间
     */
    private LocalDateTime lastHealthCheck;

    /**
     * 最后一次健康检查消息
     */
    private String lastCheckMessage;
}
