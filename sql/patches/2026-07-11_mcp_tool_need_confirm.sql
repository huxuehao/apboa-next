-- mcp_tool 增加 need_confirm 列：MCP 工具级人工确认开关（HITL §6.6）。
--
-- 背景：McpTool.needConfirm 实体字段与批量设置接口
-- PUT /mcp/server/{id}/tools/global-need-confirm（McpToolServiceImpl#updateNeedConfirm）
-- 已上线，工具调用前由 IConfirmationHook 拦截暂停等用户允许/拒绝，
-- 但建表脚本一直缺列，全新初始化的库会导致该功能 SQL 报错。
-- 关联改动：sql/once_db_init/db_init.sql 同步加列。
-- 本脚本只对已经跑起来的存量环境生效。
--
-- 注意：部分环境（如开发库）此前已手工加过该列，MySQL 的 ADD COLUMN 不支持
-- IF NOT EXISTS，故先查 information_schema 再决定是否执行，重复跑无副作用。

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'mcp_tool'
    AND COLUMN_NAME = 'need_confirm'
);

SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE `mcp_tool` ADD COLUMN `need_confirm` tinyint(1) NOT NULL DEFAULT 0 COMMENT ''是否需要用户确认'' AFTER `enabled`',
  'SELECT ''mcp_tool.need_confirm 已存在，跳过'' AS notice');

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
