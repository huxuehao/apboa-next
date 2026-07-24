-- 新增 identity_signing_key 表：平台身份断言签名密钥。
--
-- 背景（docs/identity-propagation-design.md）：平台在每次工具调用（MCP/_meta、
-- 脚本工具）时附带平台私钥签名的短命 JWT（身份断言），业务方系统用平台 JWKS
-- 公钥验签后自行做用户级权限判定——平台零权限逻辑，只做可信身份传递。
-- 本表存签名密钥对：runtime 用 ACTIVE 私钥签发；console 经
-- GET /.well-known/jwks.json 暴露 ACTIVE+RETIRING 公钥。
-- 轮换：新增 ACTIVE、旧转 RETIRING（观察期仅供验签）、最终 RETIRED 下线，
-- JWKS 双 kid 并存保证轮换全程不中断（设计文档 §5）。
-- 全局资源，无 tenant_id；首次启动时由 IdentitySigningKeyService 自动生成。
-- 关联改动：IdentitySigningKey 实体、IdentityKeyStatus 枚举、biz-account 的
-- IdentitySigningKeyService、engine 的 IdentityAssertionSigner、console 的
-- JwksController、sql/once_db_init/db_init.sql 同步建表。
-- 本脚本只对已经跑起来的存量环境生效。

CREATE TABLE `identity_signing_key` (
  `id` bigint NOT NULL,
  `kid` varchar(64) NOT NULL COMMENT '密钥标识（JWT header kid）',
  `algorithm` varchar(16) NOT NULL DEFAULT 'RS256' COMMENT '签名算法',
  `private_pem` text NOT NULL COMMENT '私钥（PKCS#8 PEM），绝不出库到日志/前端',
  `public_pem` text NOT NULL COMMENT '公钥（X.509 PEM）',
  `status` varchar(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE=签名用, RETIRING=轮换观察期仅验签, RETIRED=下线',
  `enabled` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否可用',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_identity_signing_key_kid` (`kid`)
) COMMENT='平台身份断言签名密钥';
