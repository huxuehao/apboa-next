DROP TABLE IF EXISTS `channel`;
CREATE TABLE `channel` (
`id` bigint NOT NULL COMMENT '渠道唯一标识',
`name` varchar(128) DEFAULT NULL COMMENT '渠道名称',
`remark` varchar(512) DEFAULT NULL COMMENT '渠道描述',
`type` enum('EMAIL','WECOM','DINGTALK','FEISHU') DEFAULT NULL COMMENT '渠道类型',
`config` text COMMENT '渠道配置JSON',
`enabled` tinyint(1) DEFAULT NULL COMMENT '状态（0禁用 1启用）',
`health_status` enum('HEALTHY','UNHEALTHY','UNKNOWN') DEFAULT 'UNKNOWN' COMMENT '健康状态',
`last_health_check` datetime DEFAULT NULL COMMENT '最后一次健康检查时间',
`last_check_message` varchar(500) DEFAULT NULL COMMENT '最后一次健康检查消息',
`tenant_id` bigint NOT NULL,
`created_at` datetime DEFAULT NULL,
`updated_at` datetime DEFAULT NULL,
`created_by` bigint DEFAULT NULL,
`updated_by` bigint DEFAULT NULL,
PRIMARY KEY (`id`) USING BTREE,
KEY `idx_tenant_id` (`tenant_id`),
KEY `idx_type` (`type`)
) COMMENT='通知渠道配置表';

DROP TABLE IF EXISTS `workflow_channel`;
CREATE TABLE `workflow_channel` (
`workflow_id` varchar(64) NOT NULL COMMENT '工作流ID',
`channel_id` bigint NOT NULL COMMENT '渠道ID',
PRIMARY KEY (`workflow_id`, `channel_id`)
) COMMENT='工作流渠道绑定表';
