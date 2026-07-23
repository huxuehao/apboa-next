-- 新增 agent_model_configs 表：智能体候选对话模型关联（多模型绑定）。
--
-- 背景：agent_definition.model_config_id 只能绑一个对话生成模型。本次改造支持
-- 一个智能体绑定多个候选模型，前台对话页由用户按会话切换（Redis 覆盖
-- apboa:chat:model:{sessionId}，runtime 检测变化重建 agent，机制对齐会话级
-- 思考模式开关）。
-- 口径：agent_definition.model_config_id 仍是「默认模型」的单一事实源，
-- 本表只存「额外候选」；全部候选 = 默认 ∪ 本表。旧读取点零改动天然兜底。
-- 关联改动：AgentModelConfig 实体、biz-agent 的 AgentModelConfigService、
-- ChatSessionService 会话级模型覆盖、engine 的 SessionModelResolver 与
-- ChatModelFactory、AguiRequestProcessor 重建检测泛化、biz-model 的
-- 模型删除引用检查、sql/once_db_init/db_init.sql 同步建表。
-- 本脚本只对已经跑起来的存量环境生效。

CREATE TABLE `agent_model_configs` (
  `id` bigint NOT NULL,
  `agent_definition_id` bigint NOT NULL COMMENT '智能体定义ID',
  `model_config_id` bigint NOT NULL COMMENT '额外候选模型ID（默认模型仍在 agent_definition.model_config_id）',
  `sort` int DEFAULT 0 COMMENT '展示排序',
  `tenant_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_model` (`agent_definition_id`,`model_config_id`)
) COMMENT='智能体候选对话模型关联表';
