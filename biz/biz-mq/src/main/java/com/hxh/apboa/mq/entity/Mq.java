package com.hxh.apboa.mq.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hxh.apboa.common.consts.TableConst;
import com.hxh.apboa.common.entity.BaseTenantEntity;
import com.hxh.apboa.common.mp.annotation.QueryDefine;
import com.hxh.apboa.common.mp.support.QueryCondition;
import com.hxh.apboa.mq.enums.MqType;
import lombok.Getter;
import lombok.Setter;

/**
 * 描述：消息队列表
 *
 * @author huxuehao
 **/
@Getter
@Setter
@TableName(TableConst.MQ)
public class Mq extends BaseTenantEntity {
    /**
     * 消息队列名称
     */
    @QueryDefine(condition = QueryCondition.LIKE)
    private String name;
    /**
     * 消息队列描述
     */
    private String remark;
    /**
     * 消息队列类型
     */
    @QueryDefine(condition = QueryCondition.EQ)
    private MqType type;
    /**
     * 连接地址（Kafka broker地址 或 RabbitMQ/RocketMQ host）
     */
    @QueryDefine(condition = QueryCondition.LIKE)
    private String address;
    /**
     * 端口
     */
    @QueryDefine(condition = QueryCondition.EQ)
    private Integer port;
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
     * 扩展配置（JSON格式，用于存储额外参数如虚拟主机、分区策略等）
     */
    private String config;
}
