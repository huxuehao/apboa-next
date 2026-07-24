package com.hxh.apboa.common.entity;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hxh.apboa.common.config.SerializableEnable;
import com.hxh.apboa.common.consts.TableConst;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 智能体对话Key
 *
 * @author huxuehao
 */
@Getter
@Setter
@TableName(TableConst.AGENT_CHAT_KEY)
@AllArgsConstructor
@NoArgsConstructor
public class AgentChatKey implements SerializableEnable {
    @TableField(fill = FieldFill.INSERT)
    private Long tenantId;
    private String agentCode;
    private String chatKey;

    /**
     * 嵌入身份密钥（业务方后端用它 HMAC 签 userJwt，平台换 token 时验签；
     * docs/identity-propagation-design.md §6.M6）。空 = 未启用嵌入身份验证
     */
    private String embedSecret;

    /**
     * 上一代嵌入身份密钥（轮换双活：新旧同时可验，避免轮换瞬断）
     */
    private String embedSecretPrev;
}
