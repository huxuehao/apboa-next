package com.hxh.apboa.engine.agui;

import com.fasterxml.jackson.core.type.TypeReference;
import com.hxh.apboa.common.ApboaSpringContextHolder;
import com.hxh.apboa.common.entity.AgentDefinition;
import com.hxh.apboa.common.util.JsonUtils;
import com.hxh.apboa.common.vo.AccountVO;
import com.hxh.apboa.engine.identity.IdentityAssertionSigner;
import io.agentscope.core.agui.model.RunAgentInput;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.*;

/**
 * 描述：智能体上下文
 *
 * @author huxuehao
 **/
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgentContext {
    private static final ThreadLocal<AgentContext> CONTEXT_HOLDER = new ThreadLocal<>();

    private String threadId;
    private String runId;
    private boolean memoryActive;
    private boolean planActive;
    private List<String> fileIds;
    private AccountVO userInfo;
    private AgentDefinition agentDefinition;
    private Map<String, Object> params;
    private String tenantCode;
    private Long tenantId;

    /**
     * 初始化上下文（身份盖章，docs/identity-propagation-design.md §6.M1）。
     *
     * @param input           AGUI 请求
     * @param threadId        会话 ID
     * @param trustedUserInfo 服务端认证身份（{@link TrustedUserInfoResolver#fromCurrentRequest()}，
     *                        须在 controller 同步线程解析后传入）；null = 匿名。
     *                        forwardedProps.userInfo 自报值不再采信（可伪造）
     */
    public static void init(RunAgentInput input, String threadId, AccountVO trustedUserInfo) {
        // 每次请求创建全新的上下文，避免复用旧上下文导致租户信息串扰
        AgentContext agentContext = new AgentContext();

        agentContext.setThreadId(threadId);

        agentContext.setRunId(input.getRunId());

        boolean memoryActive = input.getForwardedProp("memoryActive") != null
                ? (Boolean) input.getForwardedProp("memoryActive")
                : false;
        agentContext.setMemoryActive(memoryActive);

        agentContext.setPlanActive(
                input.getForwardedProp("planActive") != null
                        ? (Boolean) input.getForwardedProp("planActive")
                        : false);

        agentContext.setFileIds(toList(input.getForwardedProp("fileIds")));

        agentContext.setUserInfo(trustedUserInfo);

        agentContext.setParams(toMap(input.getForwardedProp("params")));

        init(agentContext);
    }

    public static void init(AgentContext agentContext) {
        CONTEXT_HOLDER.set(agentContext);
    }

    private static Map<String, Object> toMap(Object params) {
        if (params == null) {
            return new HashMap<>();
        }

        return JsonUtils.parse(JsonUtils.toJsonStr(params), new TypeReference<Map<String, Object>>() {});
    }

    private static List<String> toList(Object params) {
        if (params == null) {
            return new ArrayList<>();
        }
        return JsonUtils.parse(JsonUtils.toJsonStr(params), new TypeReference<List<String>>() {});
    }

    private static String toStr(Object params) {
        if (params == null) {
            return null;
        }
        return params instanceof String ? (String) params : params.toString();
    }

    public static AgentContext get() {
        AgentContext agentContext = CONTEXT_HOLDER.get();
        if (agentContext == null) {
            throw new IllegalStateException(
                    String.format("AgentContext not initialized for thread %s. " +
                                    "Please ensure init() is called before get().",
                            Thread.currentThread().getName())
            );
        }
        return agentContext;
    }

    public static Optional<AgentContext> getIfExists() {
        return Optional.ofNullable(CONTEXT_HOLDER.get());
    }

    public static void set(AgentContext agentContext) {
        CONTEXT_HOLDER.set(agentContext);
    }

    public static boolean exist() {
        return CONTEXT_HOLDER.get() != null;
    }

    public static void clean() {
        CONTEXT_HOLDER.remove();
    }

    /**
     * 获取一张以当前会话真实身份签发的身份断言（docs/identity-propagation-design.md §6.M4）。
     *
     * <p>供 Groovy 等动态工具脚本调用，把返回的 JWT 放进自己发起的 HTTP 请求头
     * （如 Authorization: Bearer）交业务方系统验签。安全边界：只暴露"签好的断言"，
     * 脚本拿不到签名私钥；断言身份即当前会话的真实认证身份（M1 盖章），无法冒充他人。
     *
     * @param audience 目标系统标识（业务方验签断言的 aud 必须与自己一致）
     * @return 签名 JWT（短命，默认 5 分钟）
     */
    public String getIdentityAssertion(String audience) {
        IdentityAssertionSigner signer = ApboaSpringContextHolder.getBean(IdentityAssertionSigner.class);
        return signer.sign(this, "dynamic_tool", audience);
    }
}
