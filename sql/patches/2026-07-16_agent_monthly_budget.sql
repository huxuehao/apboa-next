-- agent_definition 加月度成本预算列：成本模块的「管钱」阶段。
--
-- 背景：外嵌页面（chatKey 免登）对公网开放时智能体可被恶意刷对话，
-- 预算挂智能体（1 agent : 1 chatKey，等效 chatKey 限额且全渠道受控）：
-- 每次对话 run 建立前查当月已计价成本（chat_usage_record 聚合），
-- 达到预算即拒绝新调用并返回友好提示。NULL=不限额。
-- 未配价模型的用量不计成本、也就不受预算约束——启用预算前先把
-- 相关模型配价。
-- 关联改动：AgentDefinition/AgentDefinitionVO 加 monthlyBudget、
-- AguiRequestProcessor 预算熔断、前端 AgentFormBasic 预算输入、
-- sql/once_db_init/db_init.sql 同步。
-- 本脚本只对已经跑起来的存量环境生效。

ALTER TABLE `agent_definition`
  ADD COLUMN `monthly_budget` decimal(12,2) DEFAULT NULL COMMENT '月度成本预算（元）；NULL=不限额，达到即拒绝新对话' AFTER `max_iterations`;
