DROP TABLE IF EXISTS `dashboard`;
CREATE TABLE `dashboard` (
`id` bigint NOT NULL COMMENT 'Primary key',
`tenant_id` bigint NOT NULL COMMENT 'Tenant id',
`name` varchar(128) NOT NULL COMMENT 'Dashboard template name',
`remark` varchar(512) DEFAULT NULL COMMENT 'Remark',
`status` enum('PUBLISHED','DRAFT') DEFAULT 'DRAFT' COMMENT 'Template status',
`is_default` tinyint(1) DEFAULT 0 COMMENT 'Whether tenant default template',
`version` varchar(32) DEFAULT NULL COMMENT 'Template version',
`config` json DEFAULT NULL COMMENT 'Dashboard DSL config',
`enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT 'Enabled',
`created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
`updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
`created_by` bigint DEFAULT NULL,
`updated_by` bigint DEFAULT NULL,
PRIMARY KEY (`id`),
KEY `idx_dashboard_tenant_enabled` (`tenant_id`, `enabled`),
KEY `idx_dashboard_tenant_default` (`tenant_id`, `is_default`)
) COMMENT='Dashboard template';

DROP TABLE IF EXISTS `dashboard_user`;
CREATE TABLE `dashboard_user` (
`id` bigint NOT NULL COMMENT 'Primary key',
`tenant_id` bigint NOT NULL COMMENT 'Tenant id',
`dashboard_id` bigint NOT NULL COMMENT 'Related template id',
`config` json DEFAULT NULL COMMENT 'Personal DSL snapshot',
`based_version` varchar(32) DEFAULT NULL COMMENT 'Template version cloned from',
`enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT 'Enabled',
`created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
`updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
`created_by` bigint DEFAULT NULL,
`updated_by` bigint DEFAULT NULL,
PRIMARY KEY (`id`),
UNIQUE KEY `uk_dashboard_user` (`dashboard_id`, `created_by`),
KEY `idx_dashboard_user_tenant` (`tenant_id`)
) COMMENT='Dashboard personal override';

DROP TABLE IF EXISTS `dashboard_dataset`;
CREATE TABLE `dashboard_dataset` (
`id` bigint NOT NULL COMMENT 'Primary key',
`tenant_id` bigint NOT NULL COMMENT 'Tenant id',
`name` varchar(128) NOT NULL COMMENT 'Dataset name',
`remark` varchar(512) DEFAULT NULL COMMENT 'Remark',
`sql_text` text COMMENT 'Query statement (SELECT only)',
`params` json DEFAULT NULL COMMENT 'Parameter declaration',
`result_schema` json DEFAULT NULL COMMENT 'Cached result columns',
`cache_ttl` int DEFAULT 0 COMMENT 'Result cache ttl in seconds',
`datasource_id` bigint DEFAULT NULL COMMENT 'Bound external datasource id (null means main db)',
`enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT 'Enabled',
`created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
`updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
`created_by` bigint DEFAULT NULL,
`updated_by` bigint DEFAULT NULL,
PRIMARY KEY (`id`),
KEY `idx_dashboard_dataset_tenant_enabled` (`tenant_id`, `enabled`),
KEY `idx_dashboard_dataset_tenant_name` (`tenant_id`, `name`)
) COMMENT='Dashboard dataset';
