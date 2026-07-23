package com.hxh.apboa.common.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hxh.apboa.common.consts.TableConst;
import com.hxh.apboa.common.enums.IdentityKeyStatus;
import lombok.Getter;
import lombok.Setter;

/**
 * 平台身份断言签名密钥（docs/identity-propagation-design.md §5）
 *
 * <p>全局资源（非租户隔离）：runtime 用 ACTIVE 私钥签发工具调用身份断言，
 * console 经 /.well-known/jwks.json 暴露 ACTIVE+RETIRING 公钥供业务方验签。
 *
 * @author vaulka
 */
@Getter
@Setter
@TableName(TableConst.IDENTITY_SIGNING_KEY)
public class IdentitySigningKey extends BaseEntity {

    /**
     * 密钥标识（JWT header kid，业务方按它选择验签公钥）
     */
    private String kid;

    /**
     * 签名算法（当前固定 RS256）
     */
    private String algorithm;

    /**
     * 私钥（PKCS#8 PEM）。绝不出库到日志 / AgentContext / 前端
     */
    private String privatePem;

    /**
     * 公钥（X.509 PEM）
     */
    private String publicPem;

    /**
     * 密钥状态
     */
    private IdentityKeyStatus status;
}
