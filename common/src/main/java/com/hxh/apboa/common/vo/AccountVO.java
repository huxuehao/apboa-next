package com.hxh.apboa.common.vo;

import com.hxh.apboa.common.config.SerializableEnable;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 账号VO
 *
 * @author huxuehao
 */
@Data
@EqualsAndHashCode
public class AccountVO implements SerializableEnable {
    private Long id;
    private String nickname;
    private String email;
    private String username;
    private Boolean enabled;
    /** 当前租户内的角色 */
    private String tenantRole;
    /** 嵌入场景：业务方声称的外部用户 ID（仅在 externalIss 命名空间内有意义） */
    private String externalSub;
    /** 嵌入场景：外部身份出处（chatKey 标识） */
    private String externalIss;
    /** 嵌入场景：业务方声称的外部用户显示名 */
    private String externalName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long createdBy;
    private Long updatedBy;
    private Boolean rememberLastTenant;
}
