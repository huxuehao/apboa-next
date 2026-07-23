package com.hxh.apboa.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 身份断言签名密钥状态（docs/identity-propagation-design.md §5）
 *
 * <p>轮换流程：新密钥 ACTIVE、旧密钥转 RETIRING（仍在 JWKS 暴露供业务方验旧断言），
 * 观察期后转 RETIRED 彻底下线。
 *
 * @author vaulka
 */
@Getter
@AllArgsConstructor
public enum IdentityKeyStatus {
    ACTIVE("签名使用中"),
    RETIRING("轮换观察期（仅供验签）"),
    RETIRED("已下线");

    private final String description;
}
