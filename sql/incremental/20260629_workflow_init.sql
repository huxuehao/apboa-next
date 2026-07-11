
DROP TABLE IF EXISTS `cache`;
CREATE TABLE `cache` (
`id` bigint NOT NULL COMMENT 'Primary key',
`tenant_id` bigint NOT NULL COMMENT 'Tenant id',
`name` varchar(100) NOT NULL COMMENT 'Cache name',
`remark` varchar(500) DEFAULT NULL COMMENT 'Remark',
`type` varchar(32) NOT NULL DEFAULT 'REDIS' COMMENT 'Cache type',
`ip` varchar(255) NOT NULL COMMENT 'Host',
`port` int DEFAULT 6379 COMMENT 'Port',
`db` int DEFAULT 0 COMMENT 'Redis database',
`config` json DEFAULT NULL COMMENT 'Extended config',
`username` varchar(100) DEFAULT NULL COMMENT 'Username',
`password` varchar(255) DEFAULT NULL COMMENT 'Password',
`health_status` enum('HEALTHY','UNHEALTHY','UNKNOWN') DEFAULT 'UNKNOWN' COMMENT 'Health status',
`last_health_check` datetime DEFAULT NULL COMMENT 'Last health check time',
`last_check_message` varchar(500) DEFAULT NULL COMMENT 'Last health check message',
`enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT 'Enabled',
`created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
`updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
`created_by` bigint DEFAULT NULL,
`updated_by` bigint DEFAULT NULL,
PRIMARY KEY (`id`),
KEY `idx_cache_tenant_enabled` (`tenant_id`, `enabled`),
KEY `idx_cache_tenant_name` (`tenant_id`, `name`),
KEY `idx_cache_tenant_type_enabled` (`tenant_id`, `type`, `enabled`),
KEY `idx_cache_tenant_health` (`tenant_id`, `health_status`)
) COMMENT='Workflow cache resource';

DROP TABLE IF EXISTS `datasource`;
CREATE TABLE `datasource` (
`id` bigint NOT NULL COMMENT 'Primary key',
`tenant_id` bigint NOT NULL COMMENT 'Tenant id',
`name` varchar(100) NOT NULL COMMENT 'Datasource name',
`remark` varchar(500) DEFAULT NULL COMMENT 'Remark',
`type` varchar(32) NOT NULL COMMENT 'Datasource type',
`ip` varchar(255) NOT NULL COMMENT 'Host',
`port` varchar(16) NOT NULL COMMENT 'Port',
`db` varchar(128) NOT NULL COMMENT 'Database name',
`config` json DEFAULT NULL COMMENT 'Extended config',
`username` varchar(100) DEFAULT NULL COMMENT 'Username',
`password` varchar(255) DEFAULT NULL COMMENT 'Password',
`health_status` enum('HEALTHY','UNHEALTHY','UNKNOWN') DEFAULT 'UNKNOWN' COMMENT 'Health status',
`last_health_check` datetime DEFAULT NULL COMMENT 'Last health check time',
`last_check_message` varchar(500) DEFAULT NULL COMMENT 'Last health check message',
`enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT 'Enabled',
`created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
`updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
`created_by` bigint DEFAULT NULL,
`updated_by` bigint DEFAULT NULL,
PRIMARY KEY (`id`),
KEY `idx_datasource_tenant_enabled` (`tenant_id`, `enabled`),
KEY `idx_datasource_tenant_name` (`tenant_id`, `name`),
KEY `idx_datasource_tenant_type_enabled` (`tenant_id`, `type`, `enabled`),
KEY `idx_datasource_tenant_health` (`tenant_id`, `health_status`)
) COMMENT='Workflow datasource resource';

DROP TABLE IF EXISTS `mq`;
CREATE TABLE `mq` (
`id` bigint NOT NULL COMMENT 'Primary key',
`tenant_id` bigint NOT NULL COMMENT 'Tenant id',
`name` varchar(100) NOT NULL COMMENT 'MQ name',
`remark` varchar(500) DEFAULT NULL COMMENT 'Remark',
`type` varchar(32) NOT NULL COMMENT 'MQ type',
`address` varchar(255) NOT NULL COMMENT 'Broker address or host',
`port` int DEFAULT NULL COMMENT 'Port',
`username` varchar(100) DEFAULT NULL COMMENT 'Username',
`password` varchar(255) DEFAULT NULL COMMENT 'Password',
`config` json DEFAULT NULL COMMENT 'Extended config',
`health_status` enum('HEALTHY','UNHEALTHY','UNKNOWN') DEFAULT 'UNKNOWN' COMMENT 'Health status',
`last_health_check` datetime DEFAULT NULL COMMENT 'Last health check time',
`last_check_message` varchar(500) DEFAULT NULL COMMENT 'Last health check message',
`enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT 'Enabled',
`created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
`updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
`created_by` bigint DEFAULT NULL,
`updated_by` bigint DEFAULT NULL,
PRIMARY KEY (`id`),
KEY `idx_mq_tenant_enabled` (`tenant_id`, `enabled`),
KEY `idx_mq_tenant_name` (`tenant_id`, `name`),
KEY `idx_mq_tenant_type_enabled` (`tenant_id`, `type`, `enabled`),
KEY `idx_mq_tenant_health` (`tenant_id`, `health_status`)
) COMMENT='Workflow MQ resource';

DROP TABLE IF EXISTS `workflow`;
CREATE TABLE `workflow` (
`id` bigint NOT NULL COMMENT 'Primary key',
`tenant_id` bigint NOT NULL COMMENT 'Tenant id',
`name` varchar(120) NOT NULL COMMENT 'Workflow name',
`remark` varchar(500) DEFAULT NULL COMMENT 'Remark',
`route_id` varchar(100) DEFAULT NULL COMMENT 'Route id',
`status` varchar(32) NOT NULL DEFAULT 'DRAFT' COMMENT 'Workflow status',
`version` varchar(32) NOT NULL DEFAULT '0' COMMENT 'Current published version',
`config` json DEFAULT NULL COMMENT 'Workflow draft definition',
`locked` tinyint NOT NULL DEFAULT 0 COMMENT 'Edit lock',
`enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT 'Enabled',
`created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
`updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
`created_by` bigint DEFAULT NULL,
`updated_by` bigint DEFAULT NULL,
PRIMARY KEY (`id`),
KEY `idx_workflow_tenant_status` (`tenant_id`, `status`),
KEY `idx_workflow_route` (`route_id`)
) COMMENT='Workflow definition';

DROP TABLE IF EXISTS `workflow_version`;
CREATE TABLE `workflow_version` (
`id` bigint NOT NULL COMMENT 'Primary key',
`tenant_id` bigint NOT NULL COMMENT 'Tenant id',
`name` varchar(120) NOT NULL COMMENT 'Workflow name',
`route_id` varchar(100) DEFAULT NULL COMMENT 'Route id',
`workflow_id` varchar(64) NOT NULL COMMENT 'Workflow id',
`remark` varchar(500) DEFAULT NULL COMMENT 'Publish remark',
`config` json NOT NULL COMMENT 'Immutable workflow definition',
`version` varchar(32) NOT NULL COMMENT 'Version',
`enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT 'Enabled',
`created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
`updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
`created_by` bigint DEFAULT NULL,
`updated_by` bigint DEFAULT NULL,
PRIMARY KEY (`id`),
UNIQUE KEY `uk_workflow_version` (`workflow_id`, `version`),
KEY `idx_workflow_version_route` (`route_id`)
) COMMENT='Workflow version snapshot';

DROP TABLE IF EXISTS `workflow_run`;
CREATE TABLE `workflow_run` (
`id` bigint NOT NULL COMMENT 'Primary key',
`tenant_id` bigint NOT NULL COMMENT 'Tenant id',
`route_id` varchar(100) DEFAULT NULL COMMENT 'Route id',
`workflow_id` varchar(64) NOT NULL COMMENT 'Workflow id',
`version` varchar(32) DEFAULT NULL COMMENT 'Run version',
`config` json DEFAULT NULL COMMENT 'Workflow definition used by run',
`status` varchar(32) NOT NULL COMMENT 'Run status',
`inputs` json DEFAULT NULL COMMENT 'Run inputs',
`outputs` json DEFAULT NULL COMMENT 'Run outputs',
`error` text DEFAULT NULL COMMENT 'Error message',
`start_time` bigint DEFAULT NULL COMMENT 'Start timestamp',
`end_time` bigint DEFAULT NULL COMMENT 'End timestamp',
`enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT 'Enabled',
`created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
`updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
`created_by` bigint DEFAULT NULL,
`updated_by` bigint DEFAULT NULL,
PRIMARY KEY (`id`),
KEY `idx_workflow_run_workflow` (`workflow_id`, `created_at`),
KEY `idx_workflow_run_status` (`status`)
) COMMENT='Workflow run record';

DROP TABLE IF EXISTS `workflow_node_execution`;
CREATE TABLE `workflow_node_execution` (
`id` bigint NOT NULL COMMENT 'Primary key',
`tenant_id` bigint NOT NULL COMMENT 'Tenant id',
`route_id` varchar(100) DEFAULT NULL COMMENT 'Route id',
`workflow_id` varchar(64) NOT NULL COMMENT 'Workflow id',
`workflow_run_id` varchar(64) NOT NULL COMMENT 'Workflow run id',
`node_id` varchar(100) NOT NULL COMMENT 'Node id',
`node_title` varchar(200) DEFAULT NULL COMMENT 'Node title',
`node_type` varchar(64) NOT NULL COMMENT 'Node type',
`inputs` longtext DEFAULT NULL COMMENT 'Node inputs',
`process_data` longtext DEFAULT NULL COMMENT 'Process data',
`outputs` longtext DEFAULT NULL COMMENT 'Node outputs',
`status` varchar(32) NOT NULL COMMENT 'Node status',
`error` text DEFAULT NULL COMMENT 'Error message',
`start_time` bigint DEFAULT NULL COMMENT 'Start timestamp',
`end_time` bigint DEFAULT NULL COMMENT 'End timestamp',
`enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT 'Enabled',
`created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
`updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
`created_by` bigint DEFAULT NULL,
`updated_by` bigint DEFAULT NULL,
PRIMARY KEY (`id`),
KEY `idx_workflow_node_run` (`workflow_run_id`, `start_time`),
KEY `idx_workflow_node_node` (`node_id`)
) COMMENT='Workflow node execution log';

DROP TABLE IF EXISTS `workflow_cache`;
CREATE TABLE `workflow_cache` (
`id` bigint NOT NULL COMMENT 'Primary key',
`tenant_id` bigint NOT NULL COMMENT 'Tenant id',
`workflow_id` varchar(64) NOT NULL COMMENT 'Workflow id',
`cache_id` varchar(64) NOT NULL COMMENT 'Cache id',
`enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT 'Enabled',
`created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
`updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
`created_by` bigint DEFAULT NULL,
`updated_by` bigint DEFAULT NULL,
PRIMARY KEY (`id`),
UNIQUE KEY `uk_workflow_cache` (`workflow_id`, `cache_id`),
KEY `idx_workflow_cache_resource` (`cache_id`)
) COMMENT='Workflow cache binding';

DROP TABLE IF EXISTS `workflow_datasource`;
CREATE TABLE `workflow_datasource` (
`id` bigint NOT NULL COMMENT 'Primary key',
`tenant_id` bigint NOT NULL COMMENT 'Tenant id',
`workflow_id` varchar(64) NOT NULL COMMENT 'Workflow id',
`datasource_id` varchar(64) NOT NULL COMMENT 'Datasource id',
`enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT 'Enabled',
`created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
`updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
`created_by` bigint DEFAULT NULL,
`updated_by` bigint DEFAULT NULL,
PRIMARY KEY (`id`),
UNIQUE KEY `uk_workflow_datasource` (`workflow_id`, `datasource_id`),
KEY `idx_workflow_datasource_resource` (`datasource_id`)
) COMMENT='Workflow datasource binding';

DROP TABLE IF EXISTS `workflow_mq`;
CREATE TABLE `workflow_mq` (
`id` bigint NOT NULL COMMENT 'Primary key',
`tenant_id` bigint NOT NULL COMMENT 'Tenant id',
`workflow_id` varchar(64) NOT NULL COMMENT 'Workflow id',
`mq_id` varchar(64) NOT NULL COMMENT 'MQ id',
`enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT 'Enabled',
`created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
`updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
`created_by` bigint DEFAULT NULL,
`updated_by` bigint DEFAULT NULL,
PRIMARY KEY (`id`),
UNIQUE KEY `uk_workflow_mq` (`workflow_id`, `mq_id`),
KEY `idx_workflow_mq_resource` (`mq_id`)
) COMMENT='Workflow MQ binding';

DROP TABLE IF EXISTS `workflow_plugin`;
CREATE TABLE `workflow_plugin` (
`id` bigint NOT NULL COMMENT 'Primary key',
`tenant_id` bigint NOT NULL COMMENT 'Tenant id',
`workflow_id` varchar(64) NOT NULL COMMENT 'Workflow id',
`plugin_id` varchar(64) NOT NULL COMMENT 'Plugin id',
`enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT 'Enabled',
`created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
`updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
`created_by` bigint DEFAULT NULL,
`updated_by` bigint DEFAULT NULL,
PRIMARY KEY (`id`),
UNIQUE KEY `uk_workflow_plugin` (`workflow_id`, `plugin_id`),
KEY `idx_workflow_plugin_resource` (`plugin_id`)
) COMMENT='Workflow plugin binding';

DROP TABLE IF EXISTS `agent_workflows`;
CREATE TABLE `agent_workflows` (
`id` bigint NOT NULL,
`agent_definition_id` bigint NOT NULL,
`workflow_id` bigint NOT NULL,
`tenant_id` bigint NOT NULL,
PRIMARY KEY (`id`) USING BTREE,
UNIQUE KEY `uk_agent_workflow` (`tenant_id`,`agent_definition_id`,`workflow_id`),
KEY `idx_agent_id` (`agent_definition_id`) USING BTREE,
KEY `idx_workflow_id` (`workflow_id`) USING BTREE,
KEY `idx_tenant_id` (`tenant_id`)
) COMMENT='智能体与工具关联表';
