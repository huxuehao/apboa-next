package com.hxh.apboa.engine.identity;

import com.hxh.apboa.common.util.PemUtils;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 身份断言密码学往返验证（docs/identity-propagation-design.md §6.M2）：
 * PEM 编解码、RS256 签名→公钥验签、过期拒绝。
 * 不起 Spring 上下文——bean 组装逻辑由 M3 端到端验证覆盖。
 *
 * @author vaulka
 */
class IdentityAssertionCryptoTest {

    @Test
    void pemRoundTripAndRs256SignVerify() {
        KeyPair keyPair = PemUtils.generateRsaKeyPair();

        // PEM 编解码往返
        String privatePem = PemUtils.toPkcs8Pem(keyPair.getPrivate());
        String publicPem = PemUtils.toX509Pem(keyPair.getPublic());
        PrivateKey privateKey = PemUtils.parsePkcs8PrivateKey(privatePem);
        PublicKey publicKey = PemUtils.parseX509PublicKey(publicPem);
        assertEquals(keyPair.getPrivate(), privateKey);
        assertEquals(keyPair.getPublic(), publicKey);

        // 以断言同款 claims 结构签名→验签
        long now = System.currentTimeMillis();
        String jwt = Jwts.builder()
                .header().keyId("test-kid").and()
                .issuer("apboa-platform")
                .subject("1024")
                .audience().add("mcp:order-system").and()
                .issuedAt(new Date(now))
                .expiration(new Date(now + 120_000))
                .claim("tenant_id", "7")
                .claim("tool_name", "query_order")
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();

        Claims claims = Jwts.parser()
                .verifyWith(publicKey)
                .build()
                .parseSignedClaims(jwt)
                .getPayload();

        assertEquals("apboa-platform", claims.getIssuer());
        assertEquals("1024", claims.getSubject());
        assertTrue(claims.getAudience().contains("mcp:order-system"));
        assertEquals("7", claims.get("tenant_id"));
        assertEquals("query_order", claims.get("tool_name"));
    }

    @Test
    void expiredAssertionRejected() {
        KeyPair keyPair = PemUtils.generateRsaKeyPair();
        long now = System.currentTimeMillis();
        String jwt = Jwts.builder()
                .issuer("apboa-platform")
                .issuedAt(new Date(now - 300_000))
                .expiration(new Date(now - 120_000))
                .signWith(keyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();

        assertThrows(ExpiredJwtException.class, () -> Jwts.parser()
                .verifyWith(keyPair.getPublic())
                .build()
                .parseSignedClaims(jwt));
    }
}
