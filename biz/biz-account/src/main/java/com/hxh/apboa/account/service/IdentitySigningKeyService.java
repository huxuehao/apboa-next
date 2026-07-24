package com.hxh.apboa.account.service;

import com.hxh.apboa.common.entity.IdentitySigningKey;

import java.util.List;

/**
 * 描述：身份断言签名密钥服务（docs/identity-propagation-design.md §5/§6.M2）
 *
 * @author vaulka
 **/
public interface IdentitySigningKeyService {

    /**
     * 获取当前签名密钥（状态 ACTIVE）。首次调用时若库中无 ACTIVE 密钥，
     * 自动生成 RSA 2048 密钥对入库（多实例并发下靠 kid 唯一约束幂等兜底）。
     *
     * @return ACTIVE 签名密钥
     */
    IdentitySigningKey getActiveKey();

    /**
     * 获取可对外验签的密钥（ACTIVE + RETIRING，轮换观察期双 kid 并存），
     * 供 JWKS 端点输出公钥。
     *
     * @return 可验签密钥列表
     */
    List<IdentitySigningKey> listServableKeys();
}
