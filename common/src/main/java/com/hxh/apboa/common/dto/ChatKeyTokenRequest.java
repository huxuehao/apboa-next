package com.hxh.apboa.common.dto;

import lombok.Data;

/**
 * chatKey 换 token 请求体（可选，docs/identity-propagation-design.md §6.M6）
 *
 * @author vaulka
 */
@Data
public class ChatKeyTokenRequest {

    /**
     * 业务方后端用 embedSecret HMAC 签发的嵌入用户凭证
     * （{sub: 业务方用户ID, name?: 显示名, exp: 建议 5 分钟}）。
     * 空 = 纯匿名（向后兼容现状）
     */
    private String userJwt;
}
