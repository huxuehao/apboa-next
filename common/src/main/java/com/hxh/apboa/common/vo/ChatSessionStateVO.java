package com.hxh.apboa.common.vo;

import lombok.Data;

/**
 * 会话状态VO
 *
 * @author huxuehao
 */
@Data
public class ChatSessionStateVO {

    /** HITL 授权模式：MANUAL/AUTO_APPROVE/AUTO_REJECT */
    private String confirmMode;

    /** 思考模式有效值（会话覆盖 ?? 默认开） */
    private Boolean thinkingMode;
}
