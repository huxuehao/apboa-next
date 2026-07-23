-- chat_usage_record 加 agent_label 智能体名称快照列。
--
-- 背景：流水表对模型做了 label 快照（删改不影响历史账），智能体却没有——
-- 智能体删除后 TopN/账单只能显示「已删除#<长id>」。补上快照对齐自洽原则：
-- 写入时记当时名称，统计侧回落链 = 现存最新名 > 快照名 > 「已删除智能体(#尾号)」。
-- 存量流水按现存 agent 回填一次；此前已删除的留 NULL 走兜底展示。
-- 关联改动：ChatUsageRecord entity、UsageRecordWriter 两条写入链、
-- 回填 SQL（backfillFromMessages）、CostStatServiceImpl 展示回落链、
-- sql/once_db_init/db_init.sql 同步。
-- 本脚本只对已经跑起来的存量环境生效。

ALTER TABLE `chat_usage_record`
  ADD COLUMN `agent_label` varchar(100) DEFAULT NULL COMMENT '智能体名称快照（删除后审计仍可读）' AFTER `agent_id`;

UPDATE `chat_usage_record` r
  INNER JOIN `agent_definition` a ON r.agent_id = a.id
  SET r.agent_label = a.name
  WHERE r.agent_label IS NULL;
