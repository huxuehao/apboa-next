-- agent_definition 增加 agent 级 TTS/ASR 参数覆盖字段。
--
-- 背景：TTS 音色（voice）此前只能配在「模型配置层」
-- (model_config.extend_config.bodyParams.voice)，同一 TTS 模型被多个 agent 绑定时
-- 音色共享、无法按 agent 区分。本次为 agent 增加独立的参数覆盖字段，使同一 TTS 模型
-- 可被不同 agent 用不同音色，优先级：agent 覆盖 > 模型层默认 > provider 缺省 Cherry。
-- tts_params_override 为扁平 bodyParams（如 {"voice":"Cherry"}），由 TtsService 并入 bodyParams；
-- asr_params_override 本次仅建列占位，provider 消费与前端 UI 二期接入。
-- 命名与 LLM 的 model_params_override 对称、独立不混用。
-- 关联改动：AgentDefinition/AgentDefinitionVO 新增同名字段、ExtendConfigHelper.mergeBodyParams、
-- TtsService.resolveConfigWrapper 注入覆盖、sql/once_db_init/db_init.sql 同步加列。
-- 本脚本只对已经跑起来、库里已有 agent_definition 表的环境生效。

ALTER TABLE `agent_definition`
  ADD COLUMN `tts_params_override` text NULL COMMENT '语音合成(TTS)参数覆盖（agent 级，如 {"voice":"Cherry"}）' AFTER `model_params_override`,
  ADD COLUMN `asr_params_override` text NULL COMMENT '语音识别(ASR)参数覆盖（预留，二期启用）' AFTER `tts_params_override`;
