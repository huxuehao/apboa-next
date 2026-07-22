package com.hxh.apboa.channel.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hxh.apboa.channel.enums.ChannelType;
import com.hxh.apboa.common.consts.TableConst;
import com.hxh.apboa.common.entity.BaseTenantEntity;
import com.hxh.apboa.common.enums.HealthStatus;
import com.hxh.apboa.common.mp.annotation.QueryDefine;
import com.hxh.apboa.common.mp.support.QueryCondition;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 通知渠道配置表
 *
 * @author huxuehao
 */
@Getter
@Setter
@TableName(TableConst.CHANNEL)
public class Channel extends BaseTenantEntity {
    /**
     * 渠道名称
     */
    @QueryDefine(condition = QueryCondition.LIKE)
    private String name;
    /**
     * 渠道描述
     */
    private String remark;
    /**
     * 渠道类型
     */
    @QueryDefine(condition = QueryCondition.EQ)
    private ChannelType type;
    /**
     * 渠道配置（JSON格式，存储不同类型渠道的连接参数）
     */
    private String config;
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
