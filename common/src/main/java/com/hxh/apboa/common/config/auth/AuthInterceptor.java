package com.hxh.apboa.common.config.auth;

import com.hxh.apboa.common.UserDetail;
import com.hxh.apboa.common.consts.SysConst;
import com.hxh.apboa.common.enums.TenantRole;
import com.hxh.apboa.common.util.*;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 描述：权限认证拦截器
 *
 * @author huxuehao
 **/
@Slf4j
public class AuthInterceptor implements HandlerInterceptor {

    private static final int UNAUTHORIZED_STATUS = HttpServletResponse.SC_UNAUTHORIZED;
    private static final int OK_STATUS = HttpServletResponse.SC_OK;

    /** 存储有效的SK ID集合，用于快速校验SK是否有效 */
    private static final Set<Long> SK_ID_SET = ConcurrentHashMap.newKeySet();

    private final RedisUtils redisUtils;

    public AuthInterceptor(RedisUtils redisUtils) {
        this.redisUtils = redisUtils;
    }

    /**
     * 添加有效的SK ID到集合中（创建SK时调用）
     *
     * @param skId SK的ID
     */
    public static void addSkId(Long skId) {
        SK_ID_SET.add(skId);
    }

    /**
     * 批量移除SK ID（批量删除SK时调用）
     *
     * @param skIds SK的ID列表
     */
    public static void removeSkIds(List<Long> skIds) {
        skIds.forEach(SK_ID_SET::remove);
    }

    /**
     * 检查SK ID是否有效
     *
     * @param skId SK的ID
     * @return 是否有效
     */
    public static boolean isSkIdValid(Long skId) {
        return SK_ID_SET.contains(skId);
    }

    /**
     * 获取角色等级，用于层级比较
     *
     * @param role 租户角色
     * @return 等级数值（越大权限越高）
     */
    private static int getRoleLevel(TenantRole role) {
        return switch (role) {
            case TENANT_OWNER -> 3;
            case TENANT_ADMIN -> 2;
            case TENANT_EDITOR -> 1;
            case TENANT_VIEWER -> 0;
        };
    }

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
                             @NonNull HttpServletResponse response,
                             @NonNull Object handler) {
        RequestHolder.setRequest(request);

        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        // 检查是否需要跳过认证
        if (shouldSkipAuthentication(handlerMethod)) {
            log.debug("跳过认证: {}", handlerMethod.getMethod().getName());
            return true;
        }

        try {
            String token = TokenUtils.getToken();
            if (token == null) {
                sendErrorResponse(response, OK_STATUS, UNAUTHORIZED_STATUS, "无权进行此操作");
                return false;
            }

            // SK token 走独立的认证分支
            if (token.startsWith("sk-")) {
                return handleSkToken(token, request, response, handlerMethod);
            }

            // 常规 JWT token 认证
            return handleJwtToken(token, request, response, handlerMethod);

        } catch (Exception e) {
            sendErrorResponse(response, UNAUTHORIZED_STATUS, UNAUTHORIZED_STATUS, e.getMessage());
            return false;
        }
    }

    @Override
    public void postHandle(HttpServletRequest request,
                           HttpServletResponse response,
                           Object handler,
                           ModelAndView modelAndView) {
        RequestHolder.clear();
        TenantUtils.clear();
    }

    /**
     * 检查是否需要跳过认证
     */
    private boolean shouldSkipAuthentication(HandlerMethod handlerMethod) {
        Method method = handlerMethod.getMethod();
        Class<?> controllerClass = handlerMethod.getBeanType();

        return method.isAnnotationPresent(PassAuth.class) ||
                controllerClass.isAnnotationPresent(PassAuth.class);
    }

    /**
     * 处理JWT Token认证
     *
     * @param token         JWT token
     * @param request       HTTP请求
     * @param response      HTTP响应
     * @param handlerMethod 处理方法
     * @return 是否通过认证
     */
    private boolean handleJwtToken(String token,
                                   HttpServletRequest request,
                                   HttpServletResponse response,
                                   HandlerMethod handlerMethod) {
        Claims claims = TokenUtils.parseAndValidateToken(token);

        // 检查用户登录状态
        if (redisUtils.get(RedisKeyBuilder.globalKey("login:" + token)) == null) {
            sendErrorResponse(response, UNAUTHORIZED_STATUS, UNAUTHORIZED_STATUS, "用户未登录");
            return false;
        }

        UserDetail userDetail = JsonUtils.parse(claims.getSubject(), UserDetail.class);

        // chatKey 判定：只认签发时烙进 token 的 authChannel（此前依赖 30 天过期的
        // chatkey: Redis 缓存判定，过期即失效；改烙印后无此问题。烙印前发出的
        // 旧 chatKey token 会被当普通登录 token——按无外部接入方处理，不做兼容）
        boolean isChatKeyToken = SysConst.CHANNEL_CHAT_KEY.equals(userDetail.getAuthChannel());
        if (isChatKeyToken) {
            if (!isChatKeyAccessAllowed(handlerMethod)) {
                sendErrorResponse(response, UNAUTHORIZED_STATUS, UNAUTHORIZED_STATUS, "该接口不支持ChatKey访问");
                return false;
            }
            request.setAttribute(SysConst.AUTH_CHANNEL, SysConst.CHANNEL_CHAT_KEY);
        } else {
            // 常规登录：渠道为网页（成本流水归因）
            request.setAttribute(SysConst.AUTH_CHANNEL, SysConst.CHANNEL_WEB);
        }

        // 将用户信息存储到请求中供后续使用
        request.setAttribute(SysConst.USER_DETAIL, userDetail);

        // 设置租户上下文
        if (userDetail.getTenantId() != null) {
            TenantUtils.setCurrentTenant(userDetail.getTenantId(), userDetail.getTenantCode());
        }

        // 检查接口权限
        if (!checkRoleNeed(handlerMethod, userDetail)) {
            sendErrorResponse(response, OK_STATUS, UNAUTHORIZED_STATUS, "无权进行此操作");
            return false;
        }

        return true;
    }

    /**
     * 处理SK token认证：验证token有效性并检查接口是否允许SK访问
     *
     * @param token         以sk-开头的完整token
     * @param request       HTTP请求
     * @param response      HTTP响应
     * @param handlerMethod 处理方法
     * @return 是否通过认证
     */
    private boolean handleSkToken(String token,
                                  HttpServletRequest request,
                                  HttpServletResponse response,
                                  HandlerMethod handlerMethod) {
        // 检查接口是否被 @SkAccess 标记
        if (!isSkAccessAllowed(handlerMethod)) {
            sendErrorResponse(response, UNAUTHORIZED_STATUS, UNAUTHORIZED_STATUS, "该接口不支持SK访问");
            return false;
        }

        try {
            // 剔除 sk- 前缀，解压还原原始JWT后解析
            String jwtToken = TokenUtils.decompressJwt(token.substring(3));
            Claims claims = TokenUtils.parseAndValidateToken(jwtToken);

            // subject 即为创建SK时传入的name（JsonUtils.toJsonStr对String直接返回原值）
            Long id = Long.parseLong(claims.getId());

            // 校验SK ID是否有效（是否被删除）
            if (!isSkIdValid(id)) {
                sendErrorResponse(response, UNAUTHORIZED_STATUS, UNAUTHORIZED_STATUS, "SK已失效");
                return false;
            }

            // 将用户信息存储到请求中供后续使用
            UserDetail userDetail = JsonUtils.parse(claims.getSubject(), UserDetail.class);
            request.setAttribute(SysConst.USER_DETAIL, userDetail);
            // API 密钥渠道标记（成本流水归因）
            request.setAttribute(SysConst.AUTH_CHANNEL, SysConst.CHANNEL_SK_API);

            // 设置租户上下文
            if (userDetail.getTenantId() != null) {
                TenantUtils.setCurrentTenant(userDetail.getTenantId(), userDetail.getTenantCode());
            }

            return true;

        } catch (Exception e) {
            sendErrorResponse(response, UNAUTHORIZED_STATUS, UNAUTHORIZED_STATUS, e.getMessage());
            return false;
        }
    }

    /**
     * 检查方法或类是否被 @SkAccess 标记
     *
     * @param handlerMethod 处理方法
     * @return 是否允许SK访问
     */
    private boolean isSkAccessAllowed(HandlerMethod handlerMethod) {
        Method method = handlerMethod.getMethod();
        Class<?> controllerClass = handlerMethod.getBeanType();
        return method.isAnnotationPresent(SkAccess.class) ||
                controllerClass.isAnnotationPresent(SkAccess.class);
    }

    /**
     * 检查方法或类是否被 @ChatKeyAccess 标记
     *
     * @param handlerMethod 处理方法
     * @return 是否允许ChatKey访问
     */
    private boolean isChatKeyAccessAllowed(HandlerMethod handlerMethod) {
        Method method = handlerMethod.getMethod();
        Class<?> controllerClass = handlerMethod.getBeanType();
        return method.isAnnotationPresent(ChatKeyAccess.class) ||
                controllerClass.isAnnotationPresent(ChatKeyAccess.class);
    }

    /**
     * 检查接口权限：从 UserDetail.tenantRole 直接读取当前租户角色并采用等级比较
     * 用户角色等级 >= 所需任意角色等级即通过
     */
    private boolean checkRoleNeed(HandlerMethod handlerMethod, UserDetail userDetail) {
        Method method = handlerMethod.getMethod();

        RoleNeed annotation = method.getAnnotation(RoleNeed.class);
        if (annotation == null) {
            return true;
        }

        TenantRole[] roles = annotation.value();
        if (roles.length == 0) {
            return true;
        }

        String tenantRoleStr = userDetail.getTenantRole();
        if (tenantRoleStr == null) {
            return false;
        }

        TenantRole userTenantRole;
        try {
            userTenantRole = TenantRole.valueOf(tenantRoleStr);
        } catch (IllegalArgumentException e) {
            return false;
        }

        int userLevel = getRoleLevel(userTenantRole);
        // 等级比较：用户等级 >= 任一所需角色等级即通过
        for (TenantRole required : roles) {
            if (userLevel >= getRoleLevel(required)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 发送错误响应
     *
     * @param response   HTTP响应
     * @param httpStatus HTTP状态码
     * @param code       业务错误码
     * @param message    错误消息
     */
    private void sendErrorResponse(HttpServletResponse response, int httpStatus, int code, String message) {
        response.setStatus(httpStatus);
        response.setContentType("application/json;charset=UTF-8");

        try {
            String errorJson = String.format("{\"code\": %d, \"msg\": \"%s\"}", code, message);
            response.getWriter().write(errorJson);
        } catch (Exception e) {
            log.error("写入错误响应失败", e);
        }
    }
}
