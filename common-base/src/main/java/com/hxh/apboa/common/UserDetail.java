package com.hxh.apboa.common;

import lombok.*;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 描述：用户详情
 *
 * @author huxuehao
 **/
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserDetail implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private Long id;
    // 昵称
    private String name;
    // 账号
    private String username;
    // 邮箱
    private String email;
    // 当前会话选中的租户ID
    private Long tenantId;
    // 当前租户编码
    private String tenantCode;
    // 当前租户内的角色
    private String tenantRole;
    // 当前租户名称
    private String tenantName;
    // 用户可访问的租户列表
    private List<TenantInfo> tenants;

    // ===== 嵌入场景的业务方外部用户身份（docs/identity-propagation-design.md §6.M6）=====
    // UserDetail 即会话 token 的 subject JSON：chat-key-token 换 token 验过 userJwt 后
    // 写入这三个字段，token 自带、AuthInterceptor 解析自动带出，全链零额外机制。
    // 语义：externalSub 仅在 externalIss（哪个 chatKey/业务方声称的）命名空间内有意义，
    // 平台只背书"确实是该业务方说的"，外部标识真伪由业务方自己负责。

    // 业务方声称的外部用户 ID（userJwt.sub）
    private String externalSub;
    // 外部身份出处（chatKey 标识）
    private String externalIss;
    // 业务方声称的外部用户显示名（userJwt.name，可选）
    private String externalName;

    /**
     * 租户简要信息（用于切换器展示）
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TenantInfo implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private Long tenantId;
        private String tenantCode;
        private String tenantName;
        private String role;
    }
}
