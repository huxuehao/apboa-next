ALTER TABLE `dashboard_dataset`
    ADD COLUMN `type` varchar(16) NOT NULL DEFAULT 'SQL' COMMENT '数据集类型：SQL / HTTP' AFTER `remark`,
    ADD COLUMN `http_config` json DEFAULT NULL COMMENT 'HTTP 数据集配置（url、queries、headers、dataPath）' AFTER `datasource_id`;
