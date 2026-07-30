DROP TABLE IF EXISTS `dashboard_history`;
CREATE TABLE `dashboard_history` (
`id` bigint NOT NULL COMMENT '主键',
`tenant_id` bigint NOT NULL COMMENT '租户ID',
`dashboard_id` bigint NOT NULL COMMENT '关联模板ID',
`config` json DEFAULT NULL COMMENT '版本DSL快照',
`note` varchar(200) DEFAULT NULL COMMENT '版本备注',
`enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
`created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
`updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
`created_by` bigint DEFAULT NULL,
`updated_by` bigint DEFAULT NULL,
PRIMARY KEY (`id`),
KEY `idx_dashboard_history_owner` (`dashboard_id`, `created_by`, `created_at`),
KEY `idx_dashboard_history_tenant` (`tenant_id`)
) COMMENT='工作台个人版本历史';
