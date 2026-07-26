DROP TABLE IF EXISTS `gateway_app`;
CREATE TABLE `gateway_app` (
`id` bigint NOT NULL COMMENT '主键',
`tenant_id` bigint NOT NULL COMMENT '租户ID',
`name` varchar(100) NOT NULL COMMENT '应用名称',
`remark` varchar(500) DEFAULT NULL COMMENT '备注',
`protocol` varchar(16) NOT NULL DEFAULT 'HTTP' COMMENT '协议：HTTP',
`port` int NOT NULL COMMENT '监听端口',
`config` json DEFAULT NULL COMMENT '应用配置（跨域、内容长度等）',
`online` tinyint(1) NOT NULL DEFAULT 0 COMMENT '在线状态：1在线，0离线',
`enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
`created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
`updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
`created_by` bigint DEFAULT NULL COMMENT '创建人',
`updated_by` bigint DEFAULT NULL COMMENT '更新人',
PRIMARY KEY (`id`),
UNIQUE KEY `uk_gateway_app_port` (`port`),
KEY `idx_gateway_app_tenant_enabled` (`tenant_id`, `enabled`),
KEY `idx_gateway_app_tenant_name` (`tenant_id`, `name`),
KEY `idx_gateway_app_tenant_online` (`tenant_id`, `online`)
) COMMENT='网关应用（每个应用占用一个监听端口）';

DROP TABLE IF EXISTS `gateway_api`;
CREATE TABLE `gateway_api` (
`id` bigint NOT NULL COMMENT '主键',
`tenant_id` bigint NOT NULL COMMENT '租户ID',
`app_id` bigint NOT NULL COMMENT '网关应用ID',
`category` varchar(100) DEFAULT NULL COMMENT '自定义分类名称',
`name` varchar(100) NOT NULL COMMENT 'API名称',
`remark` varchar(500) DEFAULT NULL COMMENT '备注',
`method` varchar(16) NOT NULL DEFAULT 'GET' COMMENT 'HTTP方法：GET/POST/PUT/DELETE/PATCH/ALL',
`path` varchar(255) NOT NULL COMMENT '路由路径，支持:param占位符',
`config` json DEFAULT NULL COMMENT 'API配置（认证、限流、参数等）',
`online` tinyint(1) NOT NULL DEFAULT 0 COMMENT '在线状态：1在线，0离线',
`enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
`created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
`updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
`created_by` bigint DEFAULT NULL COMMENT '创建人',
`updated_by` bigint DEFAULT NULL COMMENT '更新人',
PRIMARY KEY (`id`),
KEY `idx_gateway_api_tenant_app` (`tenant_id`, `app_id`),
KEY `idx_gateway_api_tenant_category` (`tenant_id`, `category`),
KEY `idx_gateway_api_tenant_name` (`tenant_id`, `name`),
KEY `idx_gateway_api_tenant_online` (`tenant_id`, `online`),
KEY `idx_gateway_api_app_online` (`app_id`, `online`)
) COMMENT='网关API定义';

DROP TABLE IF EXISTS `gateway_api_workflow`;
CREATE TABLE `gateway_api_workflow` (
`id` bigint NOT NULL COMMENT '主键',
`tenant_id` bigint NOT NULL COMMENT '租户ID',
`api_id` bigint NOT NULL COMMENT '网关API ID',
`workflow_id` bigint NOT NULL COMMENT '绑定的工作流ID（已发布的工作流，多个API对应同一个工作流）',
`enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
`created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
`updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
`created_by` bigint DEFAULT NULL COMMENT '创建人',
`updated_by` bigint DEFAULT NULL COMMENT '更新人',
PRIMARY KEY (`id`),
UNIQUE KEY `uk_gateway_api_workflow_api` (`api_id`),
KEY `idx_gateway_api_workflow_tenant_wf` (`tenant_id`, `workflow_id`),
KEY `idx_gateway_api_workflow_wf` (`workflow_id`)
) COMMENT='网关API与已发布工作流绑定关系（n:1）';

DROP TABLE IF EXISTS `gateway_client`;
CREATE TABLE `gateway_client` (
`id` bigint NOT NULL COMMENT '主键',
`tenant_id` bigint NOT NULL COMMENT '租户ID',
`code` varchar(64) NOT NULL COMMENT '客户端编码（用于请求令牌）',
`name` varchar(100) NOT NULL COMMENT '客户端名称',
`consumer` varchar(100) DEFAULT NULL COMMENT '消费者（客户端所有者）',
`expire_at` datetime DEFAULT NULL COMMENT '客户端过期时间',
`token_secret` varchar(128) NOT NULL COMMENT '令牌签名密钥（HMAC256）',
`token_ttl` bigint NOT NULL DEFAULT 7200000 COMMENT '令牌有效期（毫秒）',
`online` tinyint(1) NOT NULL DEFAULT 1 COMMENT '在线状态：1在线，0离线',
`enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
`created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
`updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
`created_by` bigint DEFAULT NULL COMMENT '创建人',
`updated_by` bigint DEFAULT NULL COMMENT '更新人',
PRIMARY KEY (`id`),
UNIQUE KEY `uk_gateway_client_code` (`code`),
KEY `idx_gateway_client_tenant_enabled` (`tenant_id`, `enabled`),
KEY `idx_gateway_client_tenant_name` (`tenant_id`, `name`)
) COMMENT='网关令牌客户端';

DROP TABLE IF EXISTS `gateway_client_api`;
CREATE TABLE `gateway_client_api` (
`id` bigint NOT NULL COMMENT '主键',
`tenant_id` bigint NOT NULL COMMENT '租户ID',
`client_id` bigint NOT NULL COMMENT '网关客户端ID',
`api_id` bigint NOT NULL COMMENT '网关API ID',
`enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
`created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
`updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
`created_by` bigint DEFAULT NULL COMMENT '创建人',
`updated_by` bigint DEFAULT NULL COMMENT '更新人',
PRIMARY KEY (`id`),
UNIQUE KEY `uk_gateway_client_api` (`client_id`, `api_id`),
KEY `idx_gateway_client_api_tenant` (`tenant_id`, `client_id`),
KEY `idx_gateway_client_api_api` (`api_id`)
) COMMENT='网关客户端与API授权关系';

DROP TABLE IF EXISTS `gateway_access_log`;
CREATE TABLE `gateway_access_log` (
`id` bigint NOT NULL COMMENT '主键',
`tenant_id` bigint NOT NULL COMMENT '租户ID',
`app_id` bigint NOT NULL COMMENT '网关应用ID',
`api_id` bigint NOT NULL COMMENT '网关API ID',
`workflow_run_id` bigint DEFAULT NULL COMMENT '本次请求的工作流运行ID',
`method` varchar(16) DEFAULT NULL COMMENT 'HTTP方法',
`path` varchar(255) DEFAULT NULL COMMENT '请求路径',
`header_params` text DEFAULT NULL COMMENT '采集的Header参数',
`path_params` text DEFAULT NULL COMMENT '采集的路径参数',
`query_params` text DEFAULT NULL COMMENT '采集的查询参数',
`request_body` mediumtext DEFAULT NULL COMMENT '请求体',
`response_body` mediumtext DEFAULT NULL COMMENT '响应体',
`access_ip` varchar(64) DEFAULT NULL COMMENT '访问IP',
`status` tinyint(1) NOT NULL DEFAULT 1 COMMENT '结果状态：1成功，0失败',
`http_status` int DEFAULT NULL COMMENT 'HTTP响应状态码',
`error` text DEFAULT NULL COMMENT '错误信息',
`start_time` bigint DEFAULT NULL COMMENT '开始时间戳（毫秒）',
`end_time` bigint DEFAULT NULL COMMENT '结束时间戳（毫秒）',
`enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
`created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
`updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
`created_by` bigint DEFAULT NULL COMMENT '创建人',
`updated_by` bigint DEFAULT NULL COMMENT '更新人',
PRIMARY KEY (`id`),
KEY `idx_gateway_access_log_tenant_api` (`tenant_id`, `api_id`),
KEY `idx_gateway_access_log_tenant_app` (`tenant_id`, `app_id`),
KEY `idx_gateway_access_log_tenant_status` (`tenant_id`, `status`),
KEY `idx_gateway_access_log_created` (`created_at`)
) COMMENT='网关API访问日志';

DROP TABLE IF EXISTS `gateway_token_log`;
CREATE TABLE `gateway_token_log` (
`id` bigint NOT NULL COMMENT '主键',
`tenant_id` bigint NOT NULL COMMENT '租户ID',
`client_code` varchar(64) NOT NULL COMMENT '客户端编码',
`access_ip` varchar(64) DEFAULT NULL COMMENT '访问IP',
`status` tinyint(1) NOT NULL DEFAULT 1 COMMENT '颁发状态：1成功，0失败',
`error` varchar(500) DEFAULT NULL COMMENT '错误信息',
`enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
`created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
`updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
`created_by` bigint DEFAULT NULL COMMENT '创建人',
`updated_by` bigint DEFAULT NULL COMMENT '更新人',
PRIMARY KEY (`id`),
KEY `idx_gateway_token_log_tenant_code` (`tenant_id`, `client_code`),
KEY `idx_gateway_token_log_created` (`created_at`)
) COMMENT='网关令牌颁发日志';
