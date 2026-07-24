-- 为成本流水补充通用的「业务定义 / 运行实例 / 执行步骤」关联维度。
-- 本次由工作流写入；字段命名保持业务无关，后续定时任务可直接复用。

ALTER TABLE `chat_usage_record`
  ADD COLUMN `biz_id` varchar(64) DEFAULT NULL COMMENT '业务定义ID（workflow.id/未来任务定义ID）' AFTER `biz_type`,
  ADD COLUMN `biz_run_id` varchar(64) DEFAULT NULL COMMENT '业务运行ID（workflow_run.id/未来任务运行ID）' AFTER `biz_id`,
  ADD COLUMN `biz_label` varchar(160) DEFAULT NULL COMMENT '业务名称快照' AFTER `biz_run_id`,
  ADD COLUMN `step_id` varchar(100) DEFAULT NULL COMMENT '执行步骤ID（workflow node.id/未来任务步骤ID）' AFTER `biz_label`,
  ADD COLUMN `step_label` varchar(200) DEFAULT NULL COMMENT '执行步骤名称快照' AFTER `step_id`,
  ADD KEY `idx_biz_run` (`tenant_id`, `biz_type`, `biz_run_id`),
  ADD KEY `idx_biz_entity` (`tenant_id`, `biz_type`, `biz_id`, `created_at`);
