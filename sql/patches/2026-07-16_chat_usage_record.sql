-- 新增 chat_usage_record：LLM 用量成本流水表（成本中心的唯一事实源）。
--
-- 背景：chat_message.meta 里的 token 统计是 TEXT JSON（无法索引聚合），且
-- 消息表按月归档（chat_message_yyyyMM）跨表统计困难；workflow/定时任务/
-- 子智能体等消耗没有 assistant 消息可挂。故独立流水表：一次对话 run 写一条
-- （token 为 run 内 ReAct 多轮 LLM 调用累计，iteration_count 是调用轮数），
-- 落库时按模型当时单价算好成本快照（价格/成本冗余存储，改价删模型不影响
-- 历史账），未配价只记 token、cost 为 NULL。
-- 关联改动：TableConst/ChatUsageRecord entity/ChatUsageRecordMapper、
-- ChatLogHook 链路写入、sql/once_db_init/db_init.sql 同步。
-- 本脚本只对已经跑起来的存量环境生效。

CREATE TABLE `chat_usage_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '流水ID',
  `session_id` bigint DEFAULT NULL COMMENT '会话ID，非对话消耗（workflow/定时任务）为NULL',
  `message_id` int DEFAULT NULL COMMENT '对应assistant正文消息ID，审计钻取回链',
  `agent_id` bigint NOT NULL COMMENT '智能体ID',
  `user_id` bigint DEFAULT NULL COMMENT '发起人ID',
  `model_config_id` bigint NOT NULL COMMENT '模型配置ID',
  `model_label` varchar(100) NOT NULL COMMENT '模型名快照（模型删改后流水仍自洽）',
  `provider_type` varchar(50) DEFAULT NULL COMMENT '供应商类型快照',
  `biz_type` varchar(20) NOT NULL DEFAULT 'CHAT' COMMENT '场景：CHAT/WORKFLOW/SCHEDULED_JOB/SUB_AGENT',
  `channel` varchar(20) DEFAULT NULL COMMENT '渠道：WEB/CHAT_KEY/SK_API',
  `input_tokens` bigint NOT NULL DEFAULT 0 COMMENT '输入token（run内累计）',
  `output_tokens` bigint NOT NULL DEFAULT 0 COMMENT '输出token（run内累计）',
  `iteration_count` int NOT NULL DEFAULT 1 COMMENT 'run内LLM调用轮数',
  `duration_ms` bigint DEFAULT NULL COMMENT 'run墙钟耗时毫秒',
  `input_price` decimal(12,4) DEFAULT NULL COMMENT '记账时输入单价快照（元/百万token）',
  `output_price` decimal(12,4) DEFAULT NULL COMMENT '记账时输出单价快照（元/百万token）',
  `cost` decimal(16,8) DEFAULT NULL COMMENT '成本额（元）；NULL=记账时模型未配价',
  `created_at` datetime NOT NULL COMMENT '创建时间',
  `tenant_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_tenant_time` (`tenant_id`, `created_at`),
  KEY `idx_session` (`session_id`),
  KEY `idx_agent_time` (`agent_id`, `created_at`),
  KEY `idx_model_time` (`model_config_id`, `created_at`),
  KEY `idx_user_time` (`user_id`, `created_at`)
) COMMENT='LLM用量成本流水表';
