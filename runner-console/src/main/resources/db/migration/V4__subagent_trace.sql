CREATE TABLE IF NOT EXISTS `chat_subagent_run` (
`id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
`tenant_id` bigint NOT NULL COMMENT '租户ID',
`session_id` bigint NOT NULL COMMENT '会话ID',
`invocation_id` varchar(128) NOT NULL COMMENT '调用ID（唯一标识一次子智能体调用）',
`parent_invocation_id` varchar(128) DEFAULT NULL COMMENT '父调用ID（用于嵌套调用场景）',
`root_run_id` varchar(128) DEFAULT NULL COMMENT '根运行ID（追踪完整调用链路）',
`agent_code` varchar(128) DEFAULT NULL COMMENT '子智能体编码',
`agent_title` varchar(512) DEFAULT NULL COMMENT '子智能体标题/名称',
`subagent_session_id` varchar(128) DEFAULT NULL COMMENT '子智能体会话ID',
`status` varchar(24) NOT NULL COMMENT '调用状态（如：running/completed/failed等）',
`task` mediumtext COMMENT '任务描述/输入内容',
`summary` mediumtext COMMENT '执行结果摘要',
`started_at` datetime NOT NULL COMMENT '开始时间',
`ended_at` datetime DEFAULT NULL COMMENT '结束时间',
PRIMARY KEY (`id`),
UNIQUE KEY `uk_chat_subagent_run_invocation` (`session_id`, `invocation_id`) COMMENT '会话+调用ID唯一索引',
KEY `idx_chat_subagent_run_session_started` (`session_id`, `started_at`) COMMENT '会话+开始时间索引'
) COMMENT='子智能体调用卡片表';

CREATE TABLE IF NOT EXISTS `chat_subagent_event` (
`id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
`tenant_id` bigint NOT NULL COMMENT '租户ID',
`session_id` bigint NOT NULL COMMENT '会话ID',
`invocation_id` varchar(128) NOT NULL COMMENT '所属调用ID（关联chat_subagent_run）',
`sequence` bigint NOT NULL COMMENT '事件序号（按执行顺序递增）',
`event_id` varchar(180) NOT NULL COMMENT '事件唯一标识',
`event_type` varchar(32) NOT NULL COMMENT '事件类型（如：start/thinking/tool_call/response等）',
`payload` mediumtext COMMENT '事件载荷数据（JSON格式）',
`occurred_at` datetime NOT NULL COMMENT '事件发生时间',
PRIMARY KEY (`id`),
UNIQUE KEY `uk_chat_subagent_event_session_id` (`session_id`, `event_id`) COMMENT '会话+事件ID唯一索引',
UNIQUE KEY `uk_chat_subagent_event_session_sequence` (`session_id`, `invocation_id`, `sequence`) COMMENT '会话+调用ID+序号唯一索引',
KEY `idx_chat_subagent_event_session_invocation_seq` (`session_id`, `invocation_id`, `sequence`) COMMENT '会话+调用ID+序号复合索引'
) COMMENT='子智能体调用过程事件表';
