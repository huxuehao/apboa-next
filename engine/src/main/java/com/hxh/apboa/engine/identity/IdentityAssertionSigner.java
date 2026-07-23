package com.hxh.apboa.engine.identity;

import com.hxh.apboa.account.service.IdentitySigningKeyService;
import com.hxh.apboa.common.entity.AgentDefinition;
import com.hxh.apboa.common.entity.IdentitySigningKey;
import com.hxh.apboa.common.util.PemUtils;
import com.hxh.apboa.common.vo.AccountVO;
import com.hxh.apboa.engine.agui.AgentContext;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.PrivateKey;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 描述：身份断言签名器（"公证处"，docs/identity-propagation-design.md §3.2/§6.M2）
 *
 * <p>为每次工具调用签发短命 JWT 身份断言（介绍信）：声明"本次调用由认证用户 X
 * （租户 T）发起，仅限 audience 系统使用"。业务方用平台 JWKS 公钥验签后自行做
 * 用户级权限判定——平台零权限逻辑，只做可信身份传递。
 *
 * <p>安全边界（设计文档 §7 坑 2）：私钥只存在于本 bean 与 DB，对外仅暴露
 * "签好的断言"字符串；Groovy 脚本经 AgentContext 只能拿到以当前会话真实身份
 * 签的断言，无法触达私钥、无法冒充他人。
 *
 * @author vaulka
 */
@Component
@RequiredArgsConstructor
public class IdentityAssertionSigner {

    /**
     * MCP tools/call 请求 _meta 中携带断言的 key（业务方 MCP server 按此 key 取 JWT）
     */
    public static final String MCP_META_ASSERTION_KEY = "apboa.identityAssertion";

    /**
     * Reactor Context 传递通道的 key：LazyMcpAgentTool（engine，编译期只见原版
     * 二参 callTool）经 contextWrite 写入 _meta map，runner-runtime 的同包覆盖
     * McpSync/AsyncClientWrapper 经 deferContextual 读出并构造三参 CallToolRequest。
     * 选 Reactor Context 而非改 wrapper 签名：engine 编译 classpath 上是原版
     * agentscope jar，新增方法签名对 engine 不可见（docs/identity-propagation-design.md §6.M3）
     */
    public static final String MCP_CALL_META_CONTEXT_KEY = "apboa.mcpCallMeta";

    private final IdentitySigningKeyService identitySigningKeyService;

    /**
     * 断言签发方标识（业务方验签时校验 iss）
     */
    @Value("${apboa.identity.issuer:apboa-platform}")
    private String issuer;

    /**
     * 断言有效期（秒，默认 5 分钟）。短时一次性凭证：每次工具调用现签，泄漏重放窗口小
     */
    @Value("${apboa.identity.assertion-ttl-seconds:300}")
    private long ttlSeconds;

    /**
     * kid -> 私钥解析缓存（PEM 解析一次复用）
     */
    private final Map<String, PrivateKey> privateKeyCache = new ConcurrentHashMap<>();

    /**
     * ACTIVE 密钥的短 TTL 缓存，避免每次工具调用查库；轮换后最长延迟 60s 生效
     * （轮换流程本身有 RETIRING 观察期，见设计文档 §5，60s 延迟无影响）
     */
    private volatile IdentitySigningKey cachedActiveKey;
    private volatile long cachedActiveKeyAt;
    private static final long ACTIVE_KEY_CACHE_MS = 60_000L;

    /**
     * 以当前 AgentContext 的身份签发一张工具调用断言。
     *
     * @param ctx      当前会话上下文（身份已经 M1 服务端盖章）
     * @param toolName 本次调用的工具名（一信一用）
     * @param audience 目标系统标识（业务方必须验 aud），由调用方保证非空
     * @return 签名 JWT
     */
    public String sign(AgentContext ctx, String toolName, String audience) {
        IdentitySigningKey key = activeKey();
        PrivateKey privateKey = privateKeyCache.computeIfAbsent(
                key.getKid(), k -> PemUtils.parsePkcs8PrivateKey(key.getPrivatePem()));

        long nowMillis = System.currentTimeMillis();
        JwtBuilder builder = Jwts.builder()
                .header().keyId(key.getKid()).and()
                .issuer(issuer)
                .id(UUID.randomUUID().toString())
                .issuedAt(new Date(nowMillis))
                .expiration(new Date(nowMillis + ttlSeconds * 1000))
                .audience().add(audience).and()
                .claim("thread_id", ctx.getThreadId())
                .claim("tool_name", toolName);

        // 平台侧身份（chatKey 场景为会话级随机 id，见设计文档 §3.2 sub 语义）
        AccountVO userInfo = ctx.getUserInfo();
        if (userInfo != null && userInfo.getId() != null) {
            builder.subject(String.valueOf(userInfo.getId()));
        }
        if (userInfo != null && userInfo.getTenantRole() != null) {
            builder.claim("tenant_role", userInfo.getTenantRole());
        }

        // 嵌入场景的业务方外部用户身份（M6）：语义 = "external_iss 声称此人是
        // 他家用户 external_sub，平台核验过这话确实是该业务方说的（embedSecret 验签）"。
        // 业务方验断言时必须同时校验 aud 与 external_iss（防跨业务方替身，设计文档 §7 坑 3）
        if (userInfo != null && userInfo.getExternalSub() != null) {
            builder.claim("external_sub", userInfo.getExternalSub());
            builder.claim("external_iss", userInfo.getExternalIss());
            if (userInfo.getExternalName() != null) {
                builder.claim("external_name", userInfo.getExternalName());
            }
        }
        if (ctx.getTenantId() != null) {
            builder.claim("tenant_id", String.valueOf(ctx.getTenantId()));
        }

        AgentDefinition agentDefinition = ctx.getAgentDefinition();
        if (agentDefinition != null && agentDefinition.getId() != null) {
            builder.claim("agent_id", String.valueOf(agentDefinition.getId()));
        }

        return builder.signWith(privateKey, Jwts.SIG.RS256).compact();
    }

    private IdentitySigningKey activeKey() {
        long now = System.currentTimeMillis();
        IdentitySigningKey key = cachedActiveKey;
        if (key == null || now - cachedActiveKeyAt > ACTIVE_KEY_CACHE_MS) {
            key = identitySigningKeyService.getActiveKey();
            cachedActiveKey = key;
            cachedActiveKeyAt = now;
        }
        return key;
    }
}
