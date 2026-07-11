package com.hxh.apboa.common.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hxh.apboa.common.consts.TableConst;
import com.hxh.apboa.common.enums.HealthStatus;
import com.hxh.apboa.common.enums.cache.CacheType;
import com.hxh.apboa.common.mp.annotation.QueryDefine;
import com.hxh.apboa.common.mp.support.QueryCondition;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 描述：缓存表
 *
 * @author huxuehao
 **/
@Getter
@Setter
@TableName(TableConst.CACHE)
public class Cache extends BaseTenantEntity {
    /**
     * 缓存名称
     */
    @QueryDefine(condition = QueryCondition.LIKE)
    private String name;
    /**
     * 缓存描述
     */
    private String remark;
    /**
     * 缓存类型
     */
    @QueryDefine(condition = QueryCondition.EQ)
    private CacheType type;
    /**
     * 缓存地址
     */
    @QueryDefine(condition = QueryCondition.LIKE)
    private String ip;
    /**
     * 缓存端口
     */
    @QueryDefine(condition = QueryCondition.EQ)
    private Integer port;
    /**
     * 数据库
     */
    @QueryDefine(condition = QueryCondition.EQ)
    private Integer db;
    /**
     * 数据库配置
     */
    private String config;
    /**
     * 用户名
     */
    @QueryDefine(condition = QueryCondition.LIKE)
    private String username;
    /**
     * 密码
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
