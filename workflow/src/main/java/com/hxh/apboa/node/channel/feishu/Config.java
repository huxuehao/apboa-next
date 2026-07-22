package com.hxh.apboa.node.channel.feishu;

import com.hxh.apboa.node.base.NodeConfig;
import com.hxh.apboa.node.base.template.FormatterType;
import lombok.Getter;
import lombok.Setter;

/**
 * 飞书发送节点配置
 *
 * @author huxuehao
 */
@Getter
@Setter
public class Config implements NodeConfig {
    /**
     * 渠道ID，对应 channel 表中配置的飞书渠道
     */
    private String channelId;
    /**
     * 模板格式化器类型，默认 STRING
     */
    private FormatterType formatterType = FormatterType.STRING;
    /**
     * 消息标题（支持变量模板）
     */
    private String subject;
    /**
     * 消息内容（静态）
     */
    private String content;
    /**
     * 消息内容模板（优先级高于 content，支持变量替换）
     */
    private String contentTemplate;
}
