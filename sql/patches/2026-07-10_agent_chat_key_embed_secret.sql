-- agent_chat_key 增加嵌入身份密钥两列。
--
-- 背景（docs/identity-propagation-design.md §6.M6，Intercom Identity
-- Verification 模式）：业务方网页嵌入 agent 时，业务方后端用 embed_secret
-- 给自己的登录用户 HMAC 签一张短命 userJwt；平台在 chat-key-token 换 token
-- 时验签，通过则把 external_sub/external_iss/external_name 烙进平台会话
-- token（UserDetail 即 token subject JSON，全链自动透传），最终随工具调用
-- 断言下发给业务方系统做用户级权限判定。
-- embed_secret_prev 为轮换双活：新旧密钥同时可验，避免业务方切换期瞬断。
-- 两列为空 = 该 chatKey 未启用嵌入身份验证（纯匿名，现状行为不变）。
-- 关联改动：AgentChatKey 实体、AgentChatKeyService（重建 chatKey 时继承
-- 密钥 + 生成/轮换接口）、AccountService.chatKeyToken 验签、
-- UserDetail/AccountVO/TrustedUserInfoResolver/IdentityAssertionSigner
-- 的 external_* 透传、sql/once_db_init/db_init.sql 同步加列。
-- 本脚本只对已经跑起来的存量环境生效。

ALTER TABLE `agent_chat_key`
  ADD COLUMN `embed_secret` varchar(128) DEFAULT NULL COMMENT '嵌入身份密钥（业务方 HMAC 签 userJwt 用），空=未启用' AFTER `chat_key`,
  ADD COLUMN `embed_secret_prev` varchar(128) DEFAULT NULL COMMENT '上一代嵌入身份密钥（轮换双活）' AFTER `embed_secret`;
