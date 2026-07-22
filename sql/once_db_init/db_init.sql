/*
 Source Server         : localhost（本地）
 Source Server Type    : MySQL
 Source Server Version : 80019 (8.0.19)
 Source Host           : localhost:3306
 Source Schema         : apboa_next
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS `account`;
CREATE TABLE `account` (
  `id` bigint NOT NULL COMMENT '主键',
  `nickname` varchar(10) DEFAULT NULL COMMENT '昵称',
  `email` varchar(100) DEFAULT NULL COMMENT '邮箱',
  `username` varchar(40) DEFAULT NULL COMMENT '用户名',
  `password` varchar(100) DEFAULT NULL COMMENT '密码',
  `enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否可用',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  `remember_last_tenant` tinyint(1) DEFAULT 0 COMMENT '记住上次登录租户',
  `last_tenant_id` bigint DEFAULT NULL COMMENT '上次登录租户ID',
  PRIMARY KEY (`id`)
) COMMENT='账号';

INSERT INTO `account` (`id`, `nickname`, `email`, `username`, `password`, `enabled`, `created_at`, `updated_at`, `created_by`, `updated_by`, `remember_last_tenant`, `last_tenant_id`) VALUES (1111111111111111111, '管理员', 'admin@gmail.com', 'admin', '277fc0217db5d364b3b886a9672ea9d3', 1, '2026-02-07 18:50:51', '2026-06-07 15:28:49', NULL, 0, 0, 1);

DROP TABLE IF EXISTS `account_tenant`;
CREATE TABLE `account_tenant` (
  `id` bigint NOT NULL COMMENT '主键',
  `account_id` bigint NOT NULL COMMENT '账号ID',
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `role` enum('TENANT_OWNER','TENANT_ADMIN','TENANT_EDITOR','TENANT_VIEWER') NOT NULL COMMENT '租户内角色',
  `enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否可用',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) COMMENT='账号-租户关联表';

INSERT INTO `account_tenant` (`id`, `account_id`, `tenant_id`, `role`, `enabled`, `created_at`, `updated_at`) VALUES (1111111111111111121, 1111111111111111111, 1, 'TENANT_OWNER', 1, '2026-05-31 19:55:53', '2026-06-01 10:39:10');


DROP TABLE IF EXISTS `agent_a2a`;
CREATE TABLE `agent_a2a` (
  `id` bigint NOT NULL COMMENT '主键',
  `agent_definition_id` bigint NOT NULL COMMENT '智能体ID',
  `a2a_type` varchar(40) NOT NULL COMMENT 'A2A类型',
  `a2a_config` text NOT NULL COMMENT 'A2A配置',
  `tenant_id` bigint NOT NULL,
  PRIMARY KEY (`id`)
) COMMENT='智能体A2A关联表';

DROP TABLE IF EXISTS `agent_chat_key`;
CREATE TABLE `agent_chat_key` (
  `agent_code` varchar(100) NOT NULL COMMENT '智能体code',
  `chat_key` varchar(100) NOT NULL COMMENT 'chat key',
  `tenant_id` bigint NOT NULL
) COMMENT='智能体对话Key';

DROP TABLE IF EXISTS `agent_code_execution`;
CREATE TABLE `agent_code_execution` (
  `id` bigint NOT NULL,
  `agent_definition_id` bigint NOT NULL,
  `code_execution_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  PRIMARY KEY (`id`)
) COMMENT='智能体与代码执行环境配置关联表';

DROP TABLE IF EXISTS `agent_definition`;
CREATE TABLE `agent_definition` (
  `id` bigint NOT NULL,
  `agent_type` varchar(100) DEFAULT NULL COMMENT '智能体类型',
  `name` varchar(100) NOT NULL COMMENT '智能体名称',
  `agent_code` varchar(100) NOT NULL COMMENT '智能体代码（英文小写下划线）',
  `description` text COMMENT '智能体描述',
  `model_config_id` bigint DEFAULT NULL COMMENT '基础模型配置ID',
  `model_params_override` text COMMENT '模型参数覆盖',
  `tool_choice_strategy` enum('AUTO','NONE','REQUIRED','SPECIFIC') DEFAULT 'AUTO' COMMENT '工具选择策略',
  `specific_tool_name` varchar(100) DEFAULT NULL COMMENT '指定工具名称（当tool_choice_strategy=SPECIFIC时）',
  `system_prompt_template_id` bigint DEFAULT NULL COMMENT '系统提示词模板ID',
  `follow_template` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否跟随模板变化',
  `system_prompt` text COMMENT '系统提示词内容（当不跟随模板或模板为空时使用）',
  `sensitive_word_config_id` bigint DEFAULT NULL COMMENT '敏感词配置ID',
  `sensitive_filter_enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否启用敏感词过滤',
  `max_iterations` int DEFAULT 10 COMMENT 'React最大迭代次数',
  `enable_planning` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否启用计划',
  `show_tool_process` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否显示工具调用过程',
  `max_subtasks` int DEFAULT 10 COMMENT '最大子任务数',
  `require_plan_confirmation` tinyint(1) NOT NULL DEFAULT 0 COMMENT '计划是否需要确认',
  `enable_memory` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否启用记忆',
  `enable_memory_compression` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否启用记忆压缩',
  `memory_compression_config` text COMMENT '记忆压缩配置',
  `structured_output_enabled` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否启用结构化输出',
  `structured_output_reminder` enum('PROMPT','TOOL_CHOICE') DEFAULT NULL COMMENT '结构化输出模式',
  `structured_output_schema` text COMMENT '结构化输出模板/JSON Schema',
  `enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否可用',
  `version` varchar(20) DEFAULT '1.0.0' COMMENT '版本号',
  `tag` varchar(100) DEFAULT NULL COMMENT '标签',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  `tenant_id` bigint NOT NULL,
  PRIMARY KEY (`id`)
) COMMENT='智能体定义表';

DROP TABLE IF EXISTS `agent_hooks`;
CREATE TABLE `agent_hooks` (
  `id` bigint NOT NULL,
  `agent_definition_id` bigint NOT NULL,
  `hook_config_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  PRIMARY KEY (`id`)
) COMMENT='智能体与Hook关联表';

DROP TABLE IF EXISTS `agent_knowledge_bases`;
CREATE TABLE `agent_knowledge_bases` (
  `id` bigint NOT NULL,
  `agent_definition_id` bigint NOT NULL,
  `knowledge_base_config_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  PRIMARY KEY (`id`)
) COMMENT='智能体与知识库关联表';

DROP TABLE IF EXISTS `agent_long_term_memory`;
CREATE TABLE `agent_long_term_memory` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `agent_definition_id` bigint NOT NULL COMMENT 'Agent定义ID',
  `long_term_memory_config_id` bigint NOT NULL COMMENT '长期记忆配置ID',
  PRIMARY KEY (`id`)
) COMMENT='Agent与长期记忆关联表';

DROP TABLE IF EXISTS `agent_mcp_servers`;
CREATE TABLE `agent_mcp_servers` (
  `id` bigint NOT NULL,
  `agent_definition_id` bigint NOT NULL,
  `mcp_server_id` bigint NOT NULL,
  `exposure_mode` enum('ALL_GLOBAL','SELECTED_ONLY') NOT NULL DEFAULT 'ALL_GLOBAL' COMMENT 'Agent 侧 MCP 工具暴露模式',
  `tenant_id` bigint NOT NULL,
  PRIMARY KEY (`id`)
) COMMENT='智能体与MCP服务器关联表';

DROP TABLE IF EXISTS `agent_mcp_tool`;
CREATE TABLE `agent_mcp_tool` (
  `id` bigint NOT NULL,
  `agent_definition_id` bigint NOT NULL,
  `mcp_tool_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  PRIMARY KEY (`id`)
) COMMENT='Agent 与 MCP 工具局部选择关联表';

DROP TABLE IF EXISTS `agent_skill_packages`;
CREATE TABLE `agent_skill_packages` (
  `id` bigint NOT NULL,
  `agent_definition_id` bigint NOT NULL,
  `skill_package_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  PRIMARY KEY (`id`)
) COMMENT='智能体与技能包关联表';

DROP TABLE IF EXISTS `agent_studio`;
CREATE TABLE `agent_studio` (
  `id` bigint NOT NULL,
  `agent_definition_id` bigint NOT NULL,
  `studio_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  PRIMARY KEY (`id`)
) COMMENT='智能体与Studio关联表';

DROP TABLE IF EXISTS `agent_sub_agents`;
CREATE TABLE `agent_sub_agents` (
  `id` bigint NOT NULL,
  `parent_agent_id` bigint NOT NULL,
  `sub_agent_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  PRIMARY KEY (`id`)
) COMMENT='智能体与子智能体关联表';

DROP TABLE IF EXISTS `agent_tools`;
CREATE TABLE `agent_tools` (
  `id` bigint NOT NULL,
  `agent_definition_id` bigint NOT NULL,
  `tool_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  PRIMARY KEY (`id`)
) COMMENT='智能体与工具关联表';

DROP TABLE IF EXISTS `agentscope_sessions`;
CREATE TABLE `agentscope_sessions` (
  `session_id` varchar(255) NOT NULL,
  `state_key` varchar(255) NOT NULL,
  `item_index` int NOT NULL DEFAULT 0,
  `state_data` longtext NOT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`session_id`, `state_key`, `item_index`)
);

DROP TABLE IF EXISTS `attach`;
CREATE TABLE `attach` (
  `id` bigint NOT NULL COMMENT '主键',
  `file_id` bigint DEFAULT NULL COMMENT '文件id',
  `link` varchar(1000) DEFAULT NULL COMMENT '附件地址',
  `domain` varchar(500) DEFAULT NULL COMMENT '附件域名',
  `name` varchar(500) DEFAULT NULL COMMENT '附件名称',
  `original_name` varchar(500) DEFAULT NULL COMMENT '附件原名',
  `extension` varchar(12) DEFAULT NULL COMMENT '附件拓展名',
  `attach_size` bigint DEFAULT NULL COMMENT '附件大小',
  `path` varchar(255) DEFAULT NULL COMMENT '存储路径',
  `create_by` bigint DEFAULT NULL COMMENT '创建人',
  `create_at` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '修改人',
  `update_at` datetime DEFAULT NULL COMMENT '修改时间',
  `protocol` varchar(40) DEFAULT NULL COMMENT '存储协议',
  `status` int DEFAULT NULL COMMENT '状态',
  `tenant_id` bigint NOT NULL,
  PRIMARY KEY (`id`)
) COMMENT='附件表';

DROP TABLE IF EXISTS `attach_chunk`;
CREATE TABLE `attach_chunk` (
  `id` bigint NOT NULL COMMENT '主键',
  `chunk_hash` varchar(40) DEFAULT NULL COMMENT '分片的hash值',
  `chunk_index` int DEFAULT NULL COMMENT '分片的索引',
  `chunk_totals` int DEFAULT NULL COMMENT '分片总数',
  `file_key` varchar(40) DEFAULT NULL COMMENT '文件唯一标识',
  `file_total_size` int DEFAULT NULL COMMENT '文件大小',
  `file_name` varchar(255) DEFAULT NULL COMMENT '文件名称',
  `create_by` bigint DEFAULT NULL COMMENT '创建人',
  `create_at` datetime DEFAULT NULL COMMENT '创建时间',
  `tenant_id` bigint NOT NULL,
  PRIMARY KEY (`id`)
) COMMENT='附件表分片记录表';

DROP TABLE IF EXISTS `attach_log`;
CREATE TABLE `attach_log` (
  `id` bigint NOT NULL COMMENT '主键',
  `file_id` bigint DEFAULT NULL COMMENT '文件id',
  `original_name` varchar(500) DEFAULT NULL COMMENT '附件原名',
  `extension` varchar(12) DEFAULT NULL COMMENT '附件拓展名',
  `attach_size` bigint DEFAULT NULL COMMENT '附件大小',
  `opt_user` bigint DEFAULT NULL COMMENT '操作人',
  `opt_user_name` varchar(40) DEFAULT NULL COMMENT '操作人名称',
  `opt_time` datetime DEFAULT NULL COMMENT '操作时间',
  `opt_ip` varchar(20) DEFAULT NULL COMMENT '操作IP',
  `opt_type` varchar(10) DEFAULT NULL COMMENT '操作类型',
  `tenant_id` bigint NOT NULL,
  PRIMARY KEY (`id`)
) COMMENT='附件操作日志表';

DROP TABLE IF EXISTS `chat_message`;
CREATE TABLE `chat_message` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '消息ID',
  `session_id` bigint NOT NULL COMMENT '会话ID',
  `role` varchar(20) NOT NULL COMMENT '消息角色',
  `content` text NOT NULL COMMENT '消息内容',
  `parent_id` int DEFAULT NULL COMMENT '父消息ID',
  `path` text COMMENT '消息路径，格式如：/1/2/3/',
  `depth` int DEFAULT NULL COMMENT '消息深度，从0开始，根消息深度为0',
  `created_at` datetime DEFAULT NULL COMMENT '创建时间',
  `tenant_id` bigint NOT NULL,
  PRIMARY KEY (`id`)
) COMMENT='聊天消息表';

DROP TABLE IF EXISTS `chat_session`;
CREATE TABLE `chat_session` (
  `id` bigint NOT NULL COMMENT '会话ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `agent_id` bigint NOT NULL COMMENT '智能体ID',
  `current_message_id` int DEFAULT NULL COMMENT '当前消息ID',
  `title` varchar(255) DEFAULT NULL COMMENT '会话标题',
  `is_pinned` tinyint(1) DEFAULT 0 COMMENT '是否置顶',
  `pin_time` datetime DEFAULT NULL COMMENT '置顶时间',
  `created_at` datetime DEFAULT NULL COMMENT '创建时间',
  `updated_at` datetime DEFAULT NULL COMMENT '更新时间',
  `message_table` varchar(40) DEFAULT NULL COMMENT '消息所在归档表名，NULL=chat_message主表',
  `tenant_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_session_updated_at` (`updated_at`)
) COMMENT='聊天会话表';

-- chat_message 月度归档方案：归档表通过 ChatMessageArchiveTask 定时创建：CREATE TABLE chat_message_yyyyMM LIKE chat_message;

DROP TABLE IF EXISTS `code_execution_config`;
CREATE TABLE `code_execution_config` (
  `id` bigint NOT NULL COMMENT '主键',
  `config_name` varchar(128) NOT NULL COMMENT '配置名称，便于识别',
  `work_dir` varchar(512) DEFAULT NULL COMMENT '工作目录，空则使用临时目录',
  `upload_dir` varchar(512) DEFAULT NULL COMMENT '脚本上传目录，空则使用work_dir/skills',
  `auto_upload` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否自动上传skill文件，0=false',
  `enable_shell` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否启用ShellCommandTool',
  `enable_read` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否启用ReadFileTool',
  `enable_write` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否启用WriteFileTool',
  `command` varchar(300) NOT NULL COMMENT '允许执行的命令，如 python3、bash',
  `enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否可用',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  `tenant_id` bigint NOT NULL,
  PRIMARY KEY (`id`)
) COMMENT='代码执行环境配置';

DROP TABLE IF EXISTS `hook_config`;
CREATE TABLE `hook_config` (
  `id` bigint NOT NULL,
  `name` varchar(100) NOT NULL COMMENT 'Hook名称',
  `hook_type` enum('BUILTIN','CUSTOM') NOT NULL COMMENT 'Hook类型',
  `description` varchar(500) DEFAULT NULL COMMENT '描述',
  `class_path` varchar(255) DEFAULT NULL COMMENT 'hook路径（hook_type为BUILTIN时使用）',
  `code` text COMMENT 'hook内容（thook_type为CUSTOM时使用）',
  `enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否可用',
  `priority` int NOT NULL DEFAULT 0 COMMENT '执行优先级',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  `tenant_id` bigint NOT NULL,
  `scope_type` enum('GLOBAL','TENANT') NOT NULL DEFAULT 'GLOBAL' COMMENT '作用域类型: GLOBAL=全局, TENANT=指定租户',
  PRIMARY KEY (`id`)
) COMMENT='Hook配置表';

DROP TABLE IF EXISTS `knowledge_base_config`;
CREATE TABLE `knowledge_base_config` (
  `id` bigint NOT NULL,
  `name` varchar(100) NOT NULL COMMENT '知识库名称',
  `kb_type` enum('BAILIAN','DIFY','RAGFLOW','LOCAL') NOT NULL COMMENT '知识库类型',
  `rag_mode` enum('GENERIC','AGENTIC') NOT NULL DEFAULT 'GENERIC' COMMENT '集成模式',
  `description` varchar(500) DEFAULT NULL COMMENT '描述',
  `connection_config` text NOT NULL COMMENT '连接配置',
  `endpoint_config` text COMMENT '端点配置',
  `retrieval_config` text COMMENT '检索配置',
  `reranking_config` text COMMENT '重排序配置',
  `query_rewrite_config` text COMMENT '查询重写配置',
  `metadata_filters` text COMMENT '元数据过滤',
  `http_config` text COMMENT 'HTTP配置',
  `enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否可用',
  `health_status` enum('HEALTHY','UNHEALTHY','UNKNOWN') DEFAULT 'UNKNOWN' COMMENT '健康状态',
  `last_sync_time` datetime DEFAULT NULL COMMENT '最后同步时间',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  `tenant_id` bigint NOT NULL,
  PRIMARY KEY (`id`)
) COMMENT='知识库配置表';

DROP TABLE IF EXISTS `long_term_memory_config`;
CREATE TABLE `long_term_memory_config` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `config_name` varchar(100) NOT NULL COMMENT '配置名称',
  `memory_type` varchar(20) NOT NULL COMMENT '记忆类型：MEM0/REME/BAILIAN',
  `config` text COMMENT '配置详情JSON',
  `enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`)
) COMMENT='长期记忆配置表';

DROP TABLE IF EXISTS `mcp_server`;
CREATE TABLE `mcp_server` (
  `id` bigint NOT NULL,
  `name` varchar(100) NOT NULL COMMENT '服务器名称',
  `protocol` enum('HTTP','SSE','STDIO') NOT NULL COMMENT '协议类型',
  `enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否可用',
  `mode` enum('SYNC','ASYNC') NOT NULL DEFAULT 'SYNC' COMMENT '运行模式',
  `timeout` int DEFAULT 30 COMMENT '超时时间（秒）',
  `protocol_config` text COMMENT '协议配置',
  `description` varchar(500) DEFAULT NULL COMMENT '描述',
  `tool_schemas` mediumtext COMMENT 'Cached MCP tool schemas JSON',
  `activation_status` enum('NOT_ACTIVATED','ACTIVATING','ACTIVE','FAILED') NOT NULL DEFAULT 'NOT_ACTIVATED' COMMENT 'MCP 激活状态',
  `activation_message` varchar(500) DEFAULT NULL COMMENT '激活或同步说明',
  `failure_source` enum('NONE','RUNTIME_AUTO_DEGRADE') NOT NULL DEFAULT 'NONE' COMMENT '失败来源',
  `activation_status_changed_at` datetime DEFAULT NULL COMMENT '连接状态最近一次变更时间',
  `last_activation_time` datetime DEFAULT NULL COMMENT '上次激活时间',
  `last_tool_sync_time` datetime DEFAULT NULL COMMENT '上次工具同步时间',
  `tool_count` int NOT NULL DEFAULT 0 COMMENT '当前工具数量',
  `runtime_fail_threshold` int NOT NULL DEFAULT 3 COMMENT '运行时自动降级连续失败阈值，0 表示关闭',
  `activation_revision` bigint NOT NULL DEFAULT 0 COMMENT '激活版本号',
  `config_hash` varchar(64) DEFAULT NULL COMMENT '当前连接配置哈希',
  `needs_sync` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否需要同步工具列表',
  `activation_request_id` varchar(64) DEFAULT NULL COMMENT '当前激活请求标识',
  `health_status` enum('HEALTHY','UNHEALTHY','UNKNOWN') DEFAULT 'UNKNOWN' COMMENT '健康状态',
  `last_health_check` datetime DEFAULT NULL COMMENT '最后健康检查时间',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  `tenant_id` bigint NOT NULL,
  PRIMARY KEY (`id`)
) COMMENT='MCP服务器配置表';

DROP TABLE IF EXISTS `mcp_tool`;
CREATE TABLE `mcp_tool` (
  `id` bigint NOT NULL,
  `mcp_server_id` bigint NOT NULL COMMENT '所属 MCP 服务 ID',
  `tool_name` varchar(200) NOT NULL COMMENT '工具名',
  `description` varchar(1000) DEFAULT NULL COMMENT '工具描述',
  `input_schema` json DEFAULT NULL COMMENT '输入 Schema',
  `output_schema` json DEFAULT NULL COMMENT '输出 Schema',
  `raw_schema` json DEFAULT NULL COMMENT '原始工具 Schema',
  `schema_hash` varchar(64) DEFAULT NULL COMMENT 'Schema 摘要',
  `missing` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否已从当前 MCP 服务中消失',
  `sort` int NOT NULL DEFAULT 0 COMMENT '排序',
  `enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否全局可用',
  `last_discovered_at` datetime DEFAULT NULL COMMENT '首次发现时间',
  `last_seen_at` datetime DEFAULT NULL COMMENT '最近发现时间',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  `tenant_id` bigint NOT NULL,
  PRIMARY KEY (`id`)
) COMMENT='MCP 工具目录表';

DROP TABLE IF EXISTS `model_config`;
CREATE TABLE `model_config` (
  `id` bigint NOT NULL,
  `provider_id` bigint NOT NULL COMMENT '提供商ID',
  `name` varchar(100) NOT NULL COMMENT '模型名称',
  `model_id` varchar(100) NOT NULL COMMENT '模型编号/标识符',
  `model_type` varchar(100) DEFAULT NULL COMMENT '模型类型',
  `description` varchar(500) DEFAULT NULL COMMENT '模型描述',
  `streaming` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否支持流式',
  `thinking` tinyint(1) DEFAULT NULL COMMENT '是否支持思考',
  `context_window` int DEFAULT 2048 COMMENT '上下文窗口大小',
  `max_tokens` int DEFAULT 2000 COMMENT '最大输出token数',
  `temperature` decimal(3,2) DEFAULT 0.70 COMMENT '温度参数',
  `top_p` decimal(3,2) DEFAULT 0.90 COMMENT '核采样参数',
  `top_k` int DEFAULT 40 COMMENT 'Top-K采样',
  `repeat_penalty` decimal(3,2) DEFAULT 1.10 COMMENT '重复惩罚',
  `seed` bigint DEFAULT 42 COMMENT '随机种子',
  `extend_config` text COMMENT '扩展配置',
  `connectivity_status` enum('NOT_CHECKED','CHECKING','CONNECTED','FAILED') NOT NULL DEFAULT 'NOT_CHECKED' COMMENT '连接性检测状态',
  `connectivity_message` varchar(500) DEFAULT NULL COMMENT '连接性检测消息',
  `last_connectivity_check` datetime DEFAULT NULL COMMENT '最后连接性检测时间',
  `enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否可用',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  `tenant_id` bigint NOT NULL,
  PRIMARY KEY (`id`)
) COMMENT='模型配置表';

DROP TABLE IF EXISTS `model_provider`;
CREATE TABLE `model_provider` (
  `id` bigint NOT NULL,
  `type` varchar(50) NOT NULL COMMENT '提供商类型: DashScope, OpenAI, Anthropic, Gemini, Ollama',
  `name` varchar(100) NOT NULL COMMENT '提供商名称',
  `description` varchar(500) DEFAULT NULL COMMENT '提供商描述',
  `base_url` varchar(500) DEFAULT NULL COMMENT '基础URL',
  `auth_type` enum('CONFIG','ENV') NOT NULL DEFAULT 'CONFIG' COMMENT '认证类型: 直接配置/环境变量',
  `api_key` varchar(500) DEFAULT NULL COMMENT '加密后的API密钥（当auth_type=CONFIG时使用）',
  `env_var_name` varchar(100) DEFAULT NULL COMMENT '环境变量名（当auth_type=ENV时使用）',
  `enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否可用',
  `config_meta` text COMMENT '提供商特定配置元数据',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  `tenant_id` bigint NOT NULL,
  PRIMARY KEY (`id`)
) COMMENT='模型提供商表';

DROP TABLE IF EXISTS `params`;
CREATE TABLE `params` (
  `id` bigint NOT NULL COMMENT '主键',
  `param_name` varchar(40) DEFAULT NULL COMMENT '参数名称',
  `param_key` varchar(40) DEFAULT NULL COMMENT '参数Key',
  `param_value` varchar(255) DEFAULT NULL COMMENT '参数Value',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户ID（NULL=全局默认值，非NULL=租户覆盖）',
  PRIMARY KEY (`id`)
) COMMENT='参数表';

INSERT INTO `params` (`id`, `param_name`, `param_key`, `param_value`, `tenant_id`) VALUES (1, '访问Token有效期（单位 ms）', 'ACCESS_TOKEN_TTL', '21600000', 1);
INSERT INTO `params` (`id`, `param_name`, `param_key`, `param_value`, `tenant_id`) VALUES (2, '刷新Token有效期（单位 ms）', 'REFRESH_TOKEN_TTL', '64800000', 1);
INSERT INTO `params` (`id`, `param_name`, `param_key`, `param_value`, `tenant_id`) VALUES (3, '单个文件大小限制（单位 MB）', 'SINGLE_FILE_MAX_SIZE', '5', 1);
INSERT INTO `params` (`id`, `param_name`, `param_key`, `param_value`, `tenant_id`) VALUES (4, '支持的图片文件类型', 'ALLOW_IMAGE_FILE_TYPE', 'png,jpeg,png,gif,webp', 1);
INSERT INTO `params` (`id`, `param_name`, `param_key`, `param_value`, `tenant_id`) VALUES (5, '支持的音频文件类型', 'ALLOW_AUDIO_FILE_TYPE', 'mp3,wav,mpeg', 1);
INSERT INTO `params` (`id`, `param_name`, `param_key`, `param_value`, `tenant_id`) VALUES (6, '支持的视频文件类型', 'ALLOW_VIDEO_FILE_TYPE', 'mp4,mpeg', 1);
INSERT INTO `params` (`id`, `param_name`, `param_key`, `param_value`, `tenant_id`) VALUES (7, '技能包文件允许入库的扩展名', 'SKILL_FILE_ALLOWED_EXTENSIONS', 'md,py,sh,js,ts,json,yaml,yml,xml,txt,java,cs,go,rs,rb,php,sql,html,css,scss,less,cfg,conf,toml', 1);


DROP TABLE IF EXISTS `quartz_job_info`;
CREATE TABLE `quartz_job_info` (
`id` bigint NOT NULL COMMENT '任务身份唯一标识',
`type` enum('AGENT','WORKFLOW')  DEFAULT NULL COMMENT '类型(AGENT、WORKFLOW)',
`biz_id` varchar(64) DEFAULT NULL COMMENT '关联业务ID',
`cron` varchar(64) DEFAULT NULL COMMENT 'cron',
`job_class` varchar(100) DEFAULT NULL COMMENT 'job类路径',
`data_map` text COMMENT '执行参数',
`enabled` tinyint(1) DEFAULT NULL COMMENT '状态（0停止 1启动）',
`tenant_id` bigint NOT NULL,
`created_at` datetime DEFAULT NULL,
`updated_at` datetime DEFAULT NULL,
`created_by` bigint DEFAULT NULL,
`updated_by` bigint DEFAULT NULL,
PRIMARY KEY (`id`) USING BTREE,
KEY `idx_tenant_id` (`tenant_id`)
) COMMENT='quartz定时任务状态';

DROP TABLE IF EXISTS `quartz_job_log`;
CREATE TABLE `quartz_job_log` (
  `id` varchar(64) NOT NULL COMMENT '定时任务日志主键',
  `identity` varchar(255) DEFAULT NULL COMMENT '任务身份标识',
  `start_time` datetime DEFAULT NULL COMMENT '开始时间',
  `end_time` datetime DEFAULT NULL COMMENT '结束时间',
  `content` text COMMENT '执行情况（1标识成功，0标识失败）',
  `status` varchar(64) DEFAULT NULL COMMENT '状态',
  `duration` decimal(11,0) DEFAULT NULL COMMENT '持续时间（秒）',
  `tenant_id` bigint NOT NULL,
  PRIMARY KEY (`id`)
) COMMENT='quartz定时任务日志';

DROP TABLE IF EXISTS `quartz_job_records`;
CREATE TABLE `quartz_job_records` (
`job_id` bigint DEFAULT NULL COMMENT '任务ID',
`record_id` bigint DEFAULT NULL COMMENT '记录ID（chat_session_id，workflow_run_id）',
`create_time` datetime DEFAULT NULL COMMENT '创建时间'
) COMMENT='任务记录表';

DROP TABLE IF EXISTS `rag_document`;
CREATE TABLE `rag_document` (
  `id` bigint NOT NULL,
  `knowledge_base_config_id` bigint NOT NULL COMMENT '关联的知识库配置ID',
  `file_name` varchar(500) NOT NULL COMMENT '文件名',
  `file_path` varchar(1000) NOT NULL COMMENT '文件存储路径',
  `file_size` bigint NOT NULL DEFAULT 0 COMMENT '文件大小(字节)',
  `file_type` varchar(50) NOT NULL COMMENT '文件类型(pdf/txt/docx/xlsx/md等)',
  `chunk_count` int NOT NULL DEFAULT 0 COMMENT '分块数量',
  `status` enum('PENDING','PROCESSING','COMPLETED','FAILED') NOT NULL DEFAULT 'PENDING' COMMENT '处理状态',
  `error_message` text COMMENT '错误信息',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  `tenant_id` bigint NOT NULL,
  PRIMARY KEY (`id`)
) COMMENT='RAG文档表';

DROP TABLE IF EXISTS `rag_document_chunk`;
CREATE TABLE `rag_document_chunk` (
  `id` bigint NOT NULL,
  `document_id` bigint NOT NULL COMMENT '关联的文档ID',
  `file_name` varchar(500) NOT NULL DEFAULT '' COMMENT '文件名',
  `chunk_index` int NOT NULL COMMENT '分块序号',
  `content` text NOT NULL COMMENT '分块文本内容',
  `token_count` int DEFAULT NULL COMMENT 'Token数量(估算)',
  `start_offset` int DEFAULT NULL COMMENT '在原文中的起始偏移',
  `end_offset` int DEFAULT NULL COMMENT '在原文中的结束偏移',
  `metadata` text COMMENT '元数据(JSON)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `tenant_id` bigint NOT NULL,
  PRIMARY KEY (`id`)
) COMMENT='RAG文档分块表';

DROP TABLE IF EXISTS `secret_key`;
CREATE TABLE `secret_key` (
  `id` bigint NOT NULL COMMENT '主键ID',
  `name` varchar(100) NOT NULL COMMENT '秘钥名称（业务可读）',
  `value` varchar(500) DEFAULT NULL COMMENT '密钥',
  `enabled` tinyint DEFAULT 1 COMMENT '状态 1-启用 0-禁用',
  `expire_time` datetime DEFAULT NULL COMMENT '过期时间（为空表示不过期）',
  `created_by` bigint NOT NULL COMMENT '创建人',
  `created_at` datetime NOT NULL COMMENT '创建时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  `updated_at` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `tenant_id` bigint NOT NULL,
  PRIMARY KEY (`id`)
) COMMENT='访问秘钥';

DROP TABLE IF EXISTS `sensitive_word_config`;
CREATE TABLE `sensitive_word_config` (
  `id` bigint NOT NULL,
  `category` varchar(100) DEFAULT NULL COMMENT '分类',
  `name` varchar(100) NOT NULL COMMENT '配置名称',
  `description` varchar(500) DEFAULT NULL COMMENT '描述',
  `words` text NOT NULL COMMENT '敏感词列表',
  `action` enum('BLOCK','REPLACE','WARN') NOT NULL DEFAULT 'BLOCK' COMMENT '处理动作',
  `replacement` varchar(50) DEFAULT '***' COMMENT '替换文本（当action=REPLACE时使用）',
  `enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否可用',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  `tenant_id` bigint NOT NULL,
  PRIMARY KEY (`id`)
) COMMENT='敏感词配置表';

DROP TABLE IF EXISTS `skill_file`;
CREATE TABLE `skill_file` (
  `id` bigint NOT NULL,
  `skill_id` bigint NOT NULL COMMENT '技能包ID',
  `file_type` enum('SKILL_MD','REFERENCES','EXAMPLES','SCRIPTS') NOT NULL COMMENT '文件类型',
  `file_name` varchar(255) NOT NULL COMMENT '文件名',
  `file_path` varchar(1000) NOT NULL COMMENT '相对路径（相对于技能包根目录）',
  `content` longtext COMMENT '文件内容',
  `sort` int NOT NULL DEFAULT 0 COMMENT '排序',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  `enabled` tinyint(1) DEFAULT 1 COMMENT '是否可用',
  `tenant_id` bigint NOT NULL,
  PRIMARY KEY (`id`)
) COMMENT='技能包文件表';

DROP TABLE IF EXISTS `skill_package`;
CREATE TABLE `skill_package` (
  `id` bigint NOT NULL,
  `name` varchar(500) NOT NULL COMMENT '技能包名称',
  `description` text NOT NULL COMMENT '技能描述',
  `category` varchar(100) DEFAULT NULL COMMENT '技能分类',
  `enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否可用',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  `tenant_id` bigint NOT NULL,
  PRIMARY KEY (`id`)
) COMMENT='技能包表';

DROP TABLE IF EXISTS `skill_token`;
CREATE TABLE `skill_token` (
  `skill_token` varchar(64) NOT NULL
) COMMENT='内部服务鉴权 token';

DROP TABLE IF EXISTS `skill_tools`;
CREATE TABLE `skill_tools` (
  `id` bigint NOT NULL,
  `skill_id` bigint NOT NULL,
  `tool_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  PRIMARY KEY (`id`)
) COMMENT='技能工具关联表';

DROP TABLE IF EXISTS `storage_protocol`;
CREATE TABLE `storage_protocol` (
  `id` bigint NOT NULL COMMENT '主键',
  `name` varchar(255) DEFAULT NULL COMMENT '名称',
  `protocol` varchar(255) DEFAULT NULL COMMENT '存储协议',
  `protocol_config` varchar(1000) DEFAULT NULL COMMENT '协议配置',
  `create_by` varchar(40) DEFAULT NULL COMMENT '创建人',
  `create_at` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(40) DEFAULT NULL COMMENT '修改人',
  `update_at` datetime DEFAULT NULL COMMENT '修改时间',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `valid` int DEFAULT 1 COMMENT '是否有效',
  `tenant_id` bigint NOT NULL,
  PRIMARY KEY (`id`)
) COMMENT='文件存储协议配置';

DROP TABLE IF EXISTS `studio_config`;
CREATE TABLE `studio_config` (
  `id` bigint NOT NULL COMMENT 'ID',
  `url` varchar(40) NOT NULL COMMENT 'Studio Url',
  `project` varchar(60) NOT NULL COMMENT 'project',
  `tenant_id` bigint NOT NULL,
  PRIMARY KEY (`id`)
) COMMENT='Studio配置';

DROP TABLE IF EXISTS `system_prompt_template`;
CREATE TABLE `system_prompt_template` (
  `id` bigint NOT NULL COMMENT '主键ID',
  `category` varchar(100) DEFAULT NULL COMMENT '分类',
  `name` varchar(100) NOT NULL COMMENT '模板名称',
  `description` varchar(500) DEFAULT NULL COMMENT '模板描述',
  `content` text NOT NULL COMMENT '模板内容',
  `enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否可用',
  `usage_count` int DEFAULT 0 COMMENT '使用次数统计',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人ID',
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  PRIMARY KEY (`id`)
) COMMENT='系统提示词模板表';

DROP TABLE IF EXISTS `tenant`;
CREATE TABLE `tenant` (
  `id` bigint NOT NULL COMMENT '主键',
  `name` varchar(100) NOT NULL COMMENT '租户名称',
  `code` varchar(50) NOT NULL COMMENT '租户编码',
  `description` varchar(500) DEFAULT NULL COMMENT '租户描述',
  `status` tinyint DEFAULT '1' COMMENT '状态',
  `contact_name` varchar(100) DEFAULT NULL COMMENT '联系人姓名',
  `contact_email` varchar(200) DEFAULT NULL COMMENT '联系人邮箱',
  `config` json DEFAULT NULL COMMENT '租户扩展配置(JSON)',
  `enabled` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否可用',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  `discoverable` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否允许被发现',
  `joinable` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否允许申请加入',
  `join_approval_required` tinyint(1) NOT NULL DEFAULT '1' COMMENT '加入是否需要审批',
  PRIMARY KEY (`id`)
) COMMENT='租户表';

INSERT INTO `tenant` (`id`, `name`, `code`, `description`, `status`, `contact_name`, `contact_email`, `config`, `enabled`, `created_at`, `updated_at`, `created_by`, `updated_by`, `discoverable`, `joinable`, `join_approval_required`) VALUES (1, '默认组织', 'default', '系统初始化默认组织', 1, NULL, NULL, NULL, 1, '2026-05-31 19:55:53', '2026-06-01 21:51:19', NULL, 1111111111111111111, 1, 1, 1);


DROP TABLE IF EXISTS `tenant_join_request`;
CREATE TABLE `tenant_join_request` (
  `id` bigint NOT NULL COMMENT '主键',
  `tenant_id` bigint NOT NULL COMMENT '目标租户ID',
  `account_id` bigint NOT NULL COMMENT '申请人账号ID',
  `status` varchar(16) NOT NULL COMMENT '申请状态: PENDING/APPROVED/REJECTED/CANCELLED',
  `message` varchar(500) DEFAULT NULL COMMENT '申请留言',
  `reviewed_by` bigint DEFAULT NULL COMMENT '审核人ID',
  `reviewed_at` datetime DEFAULT NULL COMMENT '审核时间',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) COMMENT='租户加入申请表';

DROP TABLE IF EXISTS `tool_config`;
CREATE TABLE `tool_config` (
  `id` bigint NOT NULL,
  `name` varchar(100) NOT NULL COMMENT '工具名称',
  `tool_id` varchar(100) NOT NULL COMMENT '工具编号',
  `description` text NOT NULL COMMENT '工具描述',
  `category` varchar(100) DEFAULT NULL COMMENT '工具分类',
  `tool_type` enum('BUILTIN','CUSTOM') NOT NULL COMMENT '工具类型: 内置/自定义',
  `input_schema` text COMMENT '输入参数schema',
  `output_schema` text COMMENT '输出格式schema',
  `class_path` varchar(255) DEFAULT NULL COMMENT '工具路径（tool_type为SYSTEM时使用）',
  `language` enum('JAVA','JAVASCRIPT') DEFAULT NULL COMMENT '代码预演（tool_type为CUSTOM时使用）',
  `code` text COMMENT '工具内容（tool_type为CUSTOM时使用）',
  `need_confirm` tinyint(1) DEFAULT NULL COMMENT '是否需要用户确认',
  `enabled` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否可用',
  `version` varchar(20) DEFAULT '1.0.0' COMMENT '版本号',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  `tenant_id` bigint NOT NULL,
  `scope_type` enum('GLOBAL','TENANT') NOT NULL DEFAULT 'GLOBAL' COMMENT '作用域类型: GLOBAL=全局, TENANT=指定租户',
  PRIMARY KEY (`id`)
) COMMENT='工具表';

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


SET FOREIGN_KEY_CHECKS = 1;
