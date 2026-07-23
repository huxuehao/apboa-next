package com.hxh.apboa.account.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hxh.apboa.account.mapper.IdentitySigningKeyMapper;
import com.hxh.apboa.account.service.IdentitySigningKeyService;
import com.hxh.apboa.common.entity.IdentitySigningKey;
import com.hxh.apboa.common.enums.IdentityKeyStatus;
import com.hxh.apboa.common.util.PemUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.KeyPair;
import java.util.List;
import java.util.UUID;

/**
 * 描述：身份断言签名密钥业务实现
 *
 * <p>密钥为全局资源（表无 tenant_id，不受租户拦截器影响）。首启懒生成：
 * 查无 ACTIVE 时生成 RSA 2048 入库；进程内 synchronized 防并发重复生成，
 * 多实例并发首启依赖 kid 唯一约束 + 插入失败重查兜底（幂等）。
 *
 * @author vaulka
 **/
@Slf4j
@Service
public class IdentitySigningKeyServiceImpl
        extends ServiceImpl<IdentitySigningKeyMapper, IdentitySigningKey>
        implements IdentitySigningKeyService {

    private static final String DEFAULT_ALGORITHM = "RS256";

    @Override
    public IdentitySigningKey getActiveKey() {
        IdentitySigningKey active = queryActiveKey();
        if (active != null) {
            return active;
        }
        return generateActiveKeyIfAbsent();
    }

    @Override
    public List<IdentitySigningKey> listServableKeys() {
        return lambdaQuery()
                .in(IdentitySigningKey::getStatus, IdentityKeyStatus.ACTIVE, IdentityKeyStatus.RETIRING)
                .eq(IdentitySigningKey::getEnabled, true)
                .orderByDesc(IdentitySigningKey::getCreatedAt)
                .list();
    }

    private IdentitySigningKey queryActiveKey() {
        return lambdaQuery()
                .eq(IdentitySigningKey::getStatus, IdentityKeyStatus.ACTIVE)
                .eq(IdentitySigningKey::getEnabled, true)
                .orderByDesc(IdentitySigningKey::getCreatedAt)
                .last("limit 1")
                .one();
    }

    private synchronized IdentitySigningKey generateActiveKeyIfAbsent() {
        // double-check：拿到锁后可能已被并发线程生成
        IdentitySigningKey active = queryActiveKey();
        if (active != null) {
            return active;
        }

        KeyPair keyPair = PemUtils.generateRsaKeyPair();
        IdentitySigningKey key = new IdentitySigningKey();
        key.setKid(UUID.randomUUID().toString().replace("-", ""));
        key.setAlgorithm(DEFAULT_ALGORITHM);
        key.setPrivatePem(PemUtils.toPkcs8Pem(keyPair.getPrivate()));
        key.setPublicPem(PemUtils.toX509Pem(keyPair.getPublic()));
        key.setStatus(IdentityKeyStatus.ACTIVE);

        try {
            save(key);
            log.info("Generated identity signing key, kid={}", key.getKid());
            return key;
        } catch (Exception e) {
            // 多实例并发首启：他人已插入，重查取现成的
            IdentitySigningKey existing = queryActiveKey();
            if (existing != null) {
                return existing;
            }
            throw e;
        }
    }
}
