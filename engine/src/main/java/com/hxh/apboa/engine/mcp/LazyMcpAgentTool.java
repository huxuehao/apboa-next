package com.hxh.apboa.engine.mcp;

import com.hxh.apboa.common.util.FuncUtils;
import com.hxh.apboa.common.util.TenantUtils;
import com.hxh.apboa.engine.agui.AgentContext;
import com.hxh.apboa.engine.identity.IdentityAssertionSigner;
import com.hxh.apboa.mcp.service.McpRuntimeDegradeService;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.AgentTool;
import io.agentscope.core.tool.ToolCallParam;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import io.agentscope.core.tool.mcp.McpContentConverter;
import io.agentscope.core.tool.mcp.McpTool;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * 基于缓存工具目录注册的懒加载 MCP 工具。
 */
public class LazyMcpAgentTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(LazyMcpAgentTool.class);

    private final RuntimeDegradeContext degradeContext;
    private final McpSchema.Tool toolSchema;
    private final Supplier<Mono<McpClientWrapper>> initializedClientSupplier;
    private final McpRuntimeDegradeService mcpRuntimeDegradeService;
    /** 会话失效时作废共享连接的回调（入参为失败时刻的 client 实例，作 CAS 比对令牌） */
    private final Consumer<McpClientWrapper> contextInvalidator;
    /** 身份断言 audience（McpServer.audience）；空则不注入断言（M5 拍板：宁缺毋滥） */
    private final String assertionAudience;
    /** 断言签名器；audience 为空时可为 null */
    private final IdentityAssertionSigner assertionSigner;
    private final Map<String, Object> parameters;
    private final Map<String, Object> outputSchema;

    public LazyMcpAgentTool(RuntimeDegradeContext degradeContext,
                            McpSchema.Tool toolSchema,
                            Supplier<Mono<McpClientWrapper>> initializedClientSupplier,
                            McpRuntimeDegradeService mcpRuntimeDegradeService,
                            Consumer<McpClientWrapper> contextInvalidator,
                            String assertionAudience,
                            IdentityAssertionSigner assertionSigner) {
        this.degradeContext = degradeContext;
        this.toolSchema = toolSchema;
        this.initializedClientSupplier = initializedClientSupplier;
        this.mcpRuntimeDegradeService = mcpRuntimeDegradeService;
        this.contextInvalidator = contextInvalidator;
        this.assertionAudience = assertionAudience;
        this.assertionSigner = assertionSigner;
        this.parameters = McpTool.convertMcpSchemaToParameters(toolSchema.inputSchema(), Set.of());
        this.outputSchema = toolSchema.outputSchema() != null
                ? new HashMap<>(toolSchema.outputSchema())
                : null;
    }

    @Override
    public String getName() {
        return toolSchema.name();
    }

    @Override
    public String getDescription() {
        return toolSchema.description() != null ? toolSchema.description() : "";
    }

    @Override
    public Map<String, Object> getParameters() {
        return parameters;
    }

    @Override
    public Map<String, Object> getOutputSchema() {
        return outputSchema;
    }

    @Override
    public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
        AgentContext agentContext = param.getContext().get(AgentContext.class);
        if (agentContext == null) {
            return Mono.just(ToolResultBlock.error(unavailableMessage(new Exception("AgentContext is null"))));
        }

        // 保存租户信息到局部变量
        Long tenantId = agentContext.getTenantId();
        String tenantCode = agentContext.getTenantCode();

        // 身份断言（docs/identity-propagation-design.md §6.M3）：配置了 audience 才签发注入，
        // 经 Reactor Context 传给 runner-runtime 覆盖的 wrapper 构造 _meta（本类编译期
        // 只见原版二参 callTool，见 IdentityAssertionSigner.MCP_CALL_META_CONTEXT_KEY 注释）
        Map<String, Object> callMeta = buildCallMeta(agentContext);

        // 使用 defer 确保整个执行链都在租户上下文中
        return Mono.defer(() -> {
            // 设置租户上下文
            TenantUtils.setCurrentTenant(tenantId, tenantCode);

            return callOnce(param)
                    // 会话失效：连接已在 callOnce 内作废，重建后重试一次（新 session）；其余错误不重试
                    .onErrorResume(e -> isSessionLost(e)
                            ? Mono.defer(() -> {
                                log.info("MCP session lost for tool '{}' from '{}', rebuilding connection and retrying once",
                                        getName(), degradeContext.serverName());
                                return callOnce(param);
                            })
                            : Mono.error(e))
                    .doOnSuccess(result -> {
                        mcpRuntimeDegradeService.recordSuccess(
                                degradeContext.serverId(),
                                degradeContext.activationRevision(),
                                degradeContext.configHash(),
                                degradeContext.runtimeFailThreshold(),
                                tenantId);
                    })
                    .map(McpContentConverter::convertCallToolResult)
                    .onErrorResume(e -> {
                        mcpRuntimeDegradeService.recordFailure(
                                degradeContext.serverId(),
                                degradeContext.activationRevision(),
                                degradeContext.configHash(),
                                degradeContext.runtimeFailThreshold(),
                                e,
                                tenantId);
                        log.warn("MCP tool '{}' from '{}' unavailable: {}",
                                getName(), degradeContext.serverName(), e.getMessage());
                        return Mono.just(ToolResultBlock.error(unavailableMessage(e)));
                    })
                    .doFinally(signalType -> TenantUtils.clear());
        }).contextWrite(ctx -> callMeta == null
                ? ctx
                : ctx.put(IdentityAssertionSigner.MCP_CALL_META_CONTEXT_KEY, callMeta));
    }

    /**
     * 组装 tools/call 的 _meta：audience 已配置时现签一张身份断言（短命一次性，
     * 会话失效重试共用同一张——重试在秒级内完成，远小于断言 TTL）。
     * 签发失败不阻断工具调用：降级为不带断言（业务方侧自行决定拒绝或放行）。
     */
    private Map<String, Object> buildCallMeta(AgentContext agentContext) {
        if (FuncUtils.isEmpty(assertionAudience) || assertionSigner == null) {
            return null;
        }
        try {
            String assertion = assertionSigner.sign(agentContext, getName(), assertionAudience);
            return Map.of(IdentityAssertionSigner.MCP_META_ASSERTION_KEY, assertion);
        } catch (Exception e) {
            log.warn("Sign identity assertion failed for MCP tool '{}' (aud={}): {}",
                    getName(), assertionAudience, e.getMessage());
            return null;
        }
    }

    /**
     * 单次调用；失败且为会话失效时立即作废共享连接（client 实例即 CAS 比对令牌），
     * 使后续（重试或下次调用）经 supplier 重建新 session。
     */
    private Mono<McpSchema.CallToolResult> callOnce(ToolCallParam param) {
        return initializedClientSupplier.get()
                .flatMap(client -> client.callTool(getName(), param.getInput())
                        .doOnError(e -> {
                            if (isSessionLost(e)) {
                                contextInvalidator.accept(client);
                            }
                        }));
    }

    /**
     * 会话失效特征（高德等 Streamable HTTP 服务端回收 session 后的典型报文），可按需扩展
     */
    private static boolean isSessionLost(Throwable e) {
        String msg = e.getMessage();
        return msg != null && (msg.contains("Session ID not found") || msg.contains("Please reconnect"));
    }

    private String unavailableMessage(Throwable e) {
        String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        return "MCP service '" + degradeContext.serverName() + "' is unavailable. Tool '" + getName()
                + "' cannot be used right now. Reason: " + reason;
    }

    /**
     * 运行时自动降级上下文快照。
     */
    public record RuntimeDegradeContext(Long serverId,
                                        String serverName,
                                        Long activationRevision,
                                        String configHash,
                                        Integer runtimeFailThreshold) {
    }
}
