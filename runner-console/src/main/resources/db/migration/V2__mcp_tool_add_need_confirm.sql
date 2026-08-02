ALTER TABLE `mcp_tool`
    ADD COLUMN `need_confirm` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否需要人工确认。调用前由 IConfirmationHook 拦截暂停，等用户允许/拒绝';
