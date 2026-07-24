-- model_config 加 logo 列：存 antd 图标组件名（如 DeploymentUnitOutlined）。
--
-- 背景：对话页模型切换下拉富化为「图标+名称+描述」，图标从后台模型配置
-- 的预置图标集中点选（非图片上传），未配置回落默认 DeploymentUnitOutlined。
-- 名称/描述沿用本表既有 name/description 字段。
-- 关联改动：ModelConfig/ModelConfigVO/AgentModelOptionVO 加 logo、
-- AgentDefinitionController.buildModelOptions 带出、前端 modelIcons 映射表
-- 与 ModelConfigForm 图标选择器、sql/once_db_init/db_init.sql 同步。
-- 本脚本只对已经跑起来的存量环境生效。

ALTER TABLE `model_config`
  ADD COLUMN `logo` varchar(100) DEFAULT NULL COMMENT '展示图标（antd 图标组件名；NULL=默认 DeploymentUnitOutlined）' AFTER `description`;
