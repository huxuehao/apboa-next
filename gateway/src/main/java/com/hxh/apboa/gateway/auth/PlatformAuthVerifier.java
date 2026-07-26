package com.hxh.apboa.gateway.auth;

import com.hxh.apboa.common.UserDetail;
import com.hxh.apboa.common.config.auth.AuthInterceptor;
import com.hxh.apboa.common.util.JsonUtils;
import com.hxh.apboa.common.util.RedisKeyBuilder;
import com.hxh.apboa.common.util.RedisUtils;
import com.hxh.apboa.common.util.TokenUtils;
import io.jsonwebtoken.Claims;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 描述：平台鉴权校验器
 * 复用平台统一的凭证体系（与 AuthInterceptor 保持一致的校验语义）：
 * 1. 平台登录JWT：验签 + Redis登录态校验
 * 2. SK令牌（sk-前缀）：解压还原JWT后验签 + SK有效性校验
 * 校验涉及Redis阻塞IO，统一在虚拟线程中执行，避免阻塞Vert.x事件循环
 *
 * @author huxuehao
 **/
@Component
@RequiredArgsConstructor
public class PlatformAuthVerifier {
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String SK_PREFIX = "sk-";

    private final RedisUtils redisUtils;

    /** 鉴权专用虚拟线程执行器 */
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    /**
     * 异步校验Authorization请求头，返回凭证对应的用户信息
     *
     * @param authHeader Authorization请求头原始值（支持 Bearer 前缀）
     */
    public CompletableFuture<UserDetail> verifyAsync(String authHeader) {
        return CompletableFuture.supplyAsync(() -> verify(authHeader), executor);
    }

    /**
     * 同步校验，失败时抛出带提示信息的异常
     */
    private UserDetail verify(String authHeader) {
        String token = extractToken(authHeader);
        if (token.startsWith(SK_PREFIX)) {
            return verifySkToken(token);
        }
        return verifyPlatformToken(token);
    }

    /**
     * 校验平台登录JWT：验签有效且Redis中存在登录态
     */
    private UserDetail verifyPlatformToken(String token) {
        Claims claims = TokenUtils.parseAndValidateToken(token);
        if (redisUtils.get(RedisKeyBuilder.globalKey("login:" + token)) == null) {
            throw new RuntimeException("用户未登录或登录已过期");
        }
        return parseUserDetail(claims);
    }

    /**
     * 校验SK令牌：剔除sk-前缀解压还原JWT后验签，并校验SK未被删除
     */
    private UserDetail verifySkToken(String token) {
        String jwtToken = TokenUtils.decompressJwt(token.substring(SK_PREFIX.length()));
        Claims claims = TokenUtils.parseAndValidateToken(jwtToken);

        Long skId = Long.parseLong(claims.getId());
        if (!AuthInterceptor.isSkIdValid(skId)) {
            throw new RuntimeException("SK已失效");
        }
        return parseUserDetail(claims);
    }

    /**
     * 从claims的subject中解析用户信息
     */
    private UserDetail parseUserDetail(Claims claims) {
        UserDetail userDetail = JsonUtils.parse(claims.getSubject(), UserDetail.class);
        if (userDetail == null) {
            throw new RuntimeException("凭证中缺少用户信息");
        }
        return userDetail;
    }

    /**
     * 提取凭证内容，兼容 Bearer 前缀
     */
    private String extractToken(String authHeader) {
        if (authHeader == null || authHeader.isBlank()) {
            throw new RuntimeException("未携带有效的 Authorization");
        }
        String token = authHeader.startsWith(BEARER_PREFIX)
                ? authHeader.substring(BEARER_PREFIX.length()).trim()
                : authHeader.trim();
        if (token.isEmpty()) {
            throw new RuntimeException("Token不能为空");
        }
        return token;
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
    }
}
