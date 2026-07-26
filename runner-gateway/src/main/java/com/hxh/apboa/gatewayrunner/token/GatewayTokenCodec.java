package com.hxh.apboa.gatewayrunner.token;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * 描述：网关Token编解码工具
 * 使用客户端专属密钥进行HMAC-SHA256签名，subject承载客户端编号
 *
 * @author huxuehao
 **/
public class GatewayTokenCodec {
    private static final String ISSUER = "apboa-gateway";

    /**
     * 签发Token
     *
     * @param clientCode 客户端编号
     * @param ttlMillis  有效期（毫秒）
     * @param secret     客户端签名密钥
     */
    public static String issue(String clientCode, long ttlMillis, String secret) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .issuer(ISSUER)
                .subject(clientCode)
                .issuedAt(new Date(now))
                .expiration(new Date(now + ttlMillis))
                .signWith(secretKey(secret))
                .compact();
    }

    /**
     * 验签并解析Token（签名错误或过期时抛出JwtException）
     */
    public static Claims verify(String token, String secret) {
        return Jwts.parser()
                .verifyWith(secretKey(secret))
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private static SecretKey secretKey(String secret) {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}
