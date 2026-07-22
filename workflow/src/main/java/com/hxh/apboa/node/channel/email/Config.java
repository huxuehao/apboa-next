package com.hxh.apboa.node.channel.email;

import com.hxh.apboa.node.base.NodeConfig;
import com.hxh.apboa.node.base.template.FormatterType;
import lombok.Getter;
import lombok.Setter;

/**
 * 发送邮件节点配置
 *
 * @author huxuehao
 */
@Getter
@Setter
public class Config implements NodeConfig {
    /**
     * 渠道ID，对应 channel 表中配置的邮箱渠道
     */
    private String channelId;
    /**
     * 模板格式化器类型，默认 STRING
     */
    private FormatterType formatterType = FormatterType.STRING;
    /**
     * 收件人（多个用逗号分隔，支持变量模板）
     */
    private String toRecipients;
    /**
     * 抄送（多个用逗号分隔，支持变量模板）
     */
    private String ccRecipients;
    /**
     * 邮件主题（支持变量模板）
     */
    private String subject;
    /**
     * 邮件内容（静态）
     */
    private String content;
    /**
     * 邮件内容模板（优先级高于 content，支持变量替换）
     */
    private String contentTemplate;
    /**
     * 是否同步执行，默认 true
     */
    private boolean syncExecute = true;
}
