-- mcp_server 增加 audience 列：身份断言的 aud 声明来源。
--
-- 背景（docs/identity-propagation-design.md §6.M5）：平台在 MCP 工具调用时
-- 注入平台签名的身份断言（_meta），断言的 aud 必须限定目标系统（防止业务方 A
-- 拿断言冒充用户去调业务方 B）。本列即该 MCP 对应业务方系统的 audience 标识
-- （如 mcp:order-system），业务方验签时必须校验 aud 与自己一致。
-- 未配置（NULL/空）时该 MCP 的工具调用不注入断言（负责人拍板：宁缺毋滥）。
-- 关联改动：McpServer/McpServerVO 实体、McpForm.vue 表单、
-- LazyMcpAgentTool 注入逻辑（M3）、sql/once_db_init/db_init.sql 同步加列。
-- 本脚本只对已经跑起来的存量环境生效。

ALTER TABLE `mcp_server`
  ADD COLUMN `audience` varchar(200) DEFAULT NULL COMMENT '身份断言 audience（业务方验签的 aud 标识），空则不注入断言' AFTER `description`;
