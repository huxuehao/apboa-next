-- model_config 加 logo_color 列：模型展示图标的颜色（hex，如 #0F74FF）。
--
-- 背景：对话页模型下拉的图标颜色改为全部按各自配置色渲染（不再按选中态
-- 灰/蓝切换），颜色随图标一起在后台模型配置中点选；NULL=默认主题蓝 #0F74FF。
-- 关联改动：ModelConfig/ModelConfigVO/AgentModelOptionVO 加 logoColor、
-- buildModelOptions 带出、ModelConfigForm 色板选择、对话页菜单 inline 着色、
-- sql/once_db_init/db_init.sql 同步。
-- 本脚本只对已经跑起来的存量环境生效。

ALTER TABLE `model_config`
  ADD COLUMN `logo_color` varchar(20) DEFAULT NULL COMMENT '展示图标颜色（hex；NULL=默认 #0F74FF）' AFTER `logo`;
