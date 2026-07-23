package com.hxh.apboa.console.identity;

import com.hxh.apboa.account.service.IdentitySigningKeyService;
import com.hxh.apboa.common.config.auth.PassAuth;
import com.hxh.apboa.common.entity.IdentitySigningKey;
import com.hxh.apboa.common.util.PemUtils;
import io.jsonwebtoken.security.Jwks;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.util.List;
import java.util.Map;

/**
 * 平台身份断言 JWKS 公钥端点（docs/identity-propagation-design.md §5/§6.M2）
 *
 * <p>业务方系统启动时拉取本端点缓存公钥（建议每日刷新），用于验签平台在工具调用
 * 时注入的身份断言（介绍信）。免登：公钥本就是公开信息（"公证处把如何辨认我的章
 * 贴在大门口"）。输出 ACTIVE + RETIRING 双 kid，保证密钥轮换全程验签不中断。
 *
 * @author vaulka
 */
@RestController
@RequiredArgsConstructor
public class JwksController {

    private final IdentitySigningKeyService identitySigningKeyService;

    /**
     * 标准 JWKS（RFC 7517 JWK Set）。业务方按断言 header 的 kid 匹配公钥。
     */
    @PassAuth
    @GetMapping(value = "/.well-known/jwks.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> jwks() {
        List<IdentitySigningKey> keys = identitySigningKeyService.listServableKeys();
        if (keys.isEmpty()) {
            // 首次访问触发懒生成（getActiveKey 幂等），业务方启动拉公钥不会拉到空集
            identitySigningKeyService.getActiveKey();
            keys = identitySigningKeyService.listServableKeys();
        }
        List<Map<String, Object>> jwkList = keys.stream()
                .map(this::toPublicJwk)
                .toList();
        return Map.of("keys", jwkList);
    }

    private Map<String, Object> toPublicJwk(IdentitySigningKey key) {
        PublicKey publicKey = PemUtils.parseX509PublicKey(key.getPublicPem());
        // Jwk 实现 Map<String,Object>（kty/n/e），补 kid/alg/use 后交 Jackson 序列化
        Map<String, Object> jwk = new java.util.LinkedHashMap<>(
                Jwks.builder().key((RSAPublicKey) publicKey).id(key.getKid()).build());
        jwk.put("alg", key.getAlgorithm());
        jwk.put("use", "sig");
        return jwk;
    }
}
