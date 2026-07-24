package com.hxh.apboa.common.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hxh.apboa.common.config.SerializableEnable;
import com.hxh.apboa.common.consts.TableConst;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * LLM 用量成本流水：一次对话 run 一条（token 为 run 内 ReAct 多轮调用累计），
 * 落库时按模型当时单价写入价格/成本快照，模型删改不影响历史账。
 * cost 为 NULL 表示记账时模型未配价（token 照记，统计侧提示补配后可重算）。
 *
 * @author huxuehao
 */
@Getter
@Setter
@TableName(TableConst.CHAT_USAGE_RECORD)
public class ChatUsageRecord implements SerializableEnable {

    /** 场景：普通对话 */
    public static final String BIZ_CHAT = "CHAT";
    /** 场景：工作流节点 */
    public static final String BIZ_WORKFLOW = "WORKFLOW";
    /** 场景：定时任务 */
    public static final String BIZ_SCHEDULED_JOB = "SCHEDULED_JOB";
    /** 场景：子智能体 */
    public static final String BIZ_SUB_AGENT = "SUB_AGENT";

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(fill = FieldFill.INSERT)
    private Long tenantId;

    /**
     * 会话ID，非对话消耗（workflow/定时任务）为 NULL
     */
    private Long sessionId;

    /**
     * 对应 assistant 正文消息ID，审计钻取回链
     */
    private Integer messageId;

    /**
     * 智能体ID
     */
    private Long agentId;

    /**
     * 智能体名称快照（删除后审计仍可读）
     */
    private String agentLabel;

    /**
     * 发起人ID
     */
    private Long userId;

    /**
     * 模型配置ID
     */
    private Long modelConfigId;

    /**
     * 模型名快照
     */
    private String modelLabel;

    /**
     * 供应商类型快照
     */
    private String providerType;

    /**
     * 场景：CHAT / WORKFLOW / SCHEDULED_JOB / SUB_AGENT
     */
    private String bizType;

    /**
     * 业务定义 ID：工作流为 workflow.id；未来定时任务可写任务定义 ID。
     */
    private String bizId;

    /**
     * 业务运行 ID：工作流为 workflow_run.id；用于把多条节点流水归并为一次执行账单。
     */
    private String bizRunId;

    /**
     * 业务名称快照：定义删除或改名后账单仍可读。
     */
    private String bizLabel;

    /**
     * 执行步骤 ID：工作流为 node.id；未来可复用于定时任务步骤。
     */
    private String stepId;

    /**
     * 执行步骤名称快照。
     */
    private String stepLabel;

    /**
     * 渠道：WEB / CHAT_KEY / SK_API
     */
    private String channel;

    /**
     * 输入 token（run 内累计）
     */
    private Long inputTokens;

    /**
     * 输出 token（run 内累计）
     */
    private Long outputTokens;

    /**
     * run 内 LLM 调用轮数
     */
    private Integer iterationCount;

    /**
     * run 墙钟耗时毫秒
     */
    private Long durationMs;

    /**
     * 记账时输入单价快照（元/百万token）
     */
    private BigDecimal inputPrice;

    /**
     * 记账时输出单价快照（元/百万token）
     */
    private BigDecimal outputPrice;

    /**
     * 成本额（元）；NULL=记账时模型未配价
     */
    private BigDecimal cost;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
