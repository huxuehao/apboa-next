package com.hxh.apboa.engine.agui;

import com.hxh.apboa.common.UserDetail;
import com.hxh.apboa.common.consts.SysConst;
import com.hxh.apboa.common.util.RequestHolder;
import com.hxh.apboa.common.vo.AccountVO;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 描述：可信用户身份解析器（身份盖章，docs/identity-propagation-design.md §6.M1）
 *
 * <p>从 AuthInterceptor 认证后放入 request attribute 的 {@link UserDetail} 构造
 * {@link AccountVO}，作为 {@link AgentContext} userInfo 的唯一可信来源——前端
 * forwardedProps 自报的 userInfo 不再被采信（可任意伪造，详见设计文档 §2）。
 *
 * <p>必须在 controller 同步线程调用：AgentContext.init 运行在 executorService 的
 * 异步线程，届时 postHandle 已清理 RequestHolder；且池化线程的 InheritableThreadLocal
 * 可能残留其他请求的引用，在异步块内回读有串号风险。
 *
 * @author vaulka
 */
public final class TrustedUserInfoResolver {

    private TrustedUserInfoResolver() {
    }

    /**
     * 从当前请求的认证结果解析可信用户身份。
     *
     * @return 认证用户身份；无认证上下文（@PassAuth 等）返回 null，按匿名处理，不回退自报值
     */
    public static AccountVO fromCurrentRequest() {
        HttpServletRequest request = RequestHolder.getRequest();
        if (request == null) {
            return null;
        }
        Object attribute = request.getAttribute(SysConst.USER_DETAIL);
        if (!(attribute instanceof UserDetail userDetail)) {
            return null;
        }
        AccountVO userInfo = new AccountVO();
        userInfo.setId(userDetail.getId());
        userInfo.setNickname(userDetail.getName());
        userInfo.setUsername(userDetail.getUsername());
        userInfo.setEmail(userDetail.getEmail());
        userInfo.setTenantRole(userDetail.getTenantRole());
        // 嵌入场景的业务方外部用户身份（chat-key-token 验过 userJwt 后烙进会话 token）
        userInfo.setExternalSub(userDetail.getExternalSub());
        userInfo.setExternalIss(userDetail.getExternalIss());
        userInfo.setExternalName(userDetail.getExternalName());
        return userInfo;
    }
}
