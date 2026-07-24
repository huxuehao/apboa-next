-- agent_model_configs 加 per-model 参数覆盖列。
--
-- 背景：agent_definition.model_params_override 是 agent 级一份，多候选模型
-- 场景下对所有候选生效——不同模型的合理参数与厂商专有参数（extendConfig）
-- 可能完全不同。本列给每个「额外候选」独立的参数覆盖（结构与 agent 级
-- model_params_override 相同）；NULL=跟随模型自身默认。
-- 归属口径：默认模型的覆盖仍走 agent_definition.model_params_override；
-- ChatModelFactory 按「本次实际选定的模型」取对应归属的覆盖。
-- 关联改动：AgentModelConfig 实体（autoResultMap+JsonNodeTypeHandler）、
-- AgentModelConfigService 取参方法、ChatModelFactory 归属选取、
-- AgentDefinitionVO.modelsParamsOverride、后台表单 per-model 配置、
-- sql/once_db_init/db_init.sql 同步。
-- 本脚本只对已经跑起来的存量环境生效。

ALTER TABLE `agent_model_configs`
  ADD COLUMN `model_params_override` text COMMENT '该候选模型的参数覆盖（结构同 agent_definition.model_params_override；NULL=跟随模型默认）' AFTER `model_config_id`;
