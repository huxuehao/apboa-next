package com.hxh.apboa.console.agent;

import com.hxh.apboa.agent.service.AgentChatKeyService;
import com.hxh.apboa.common.config.auth.ChatKeyAccess;
import com.hxh.apboa.common.config.auth.SkAccess;
import com.hxh.apboa.common.r.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 描述：智能体对话Key Controller
 *
 * @author huxuehao
 **/
@RestController
@RequestMapping("/agent/chat-key")
@RequiredArgsConstructor
public class AgentChatKeyController {
    private final AgentChatKeyService agentChatKeyService;

    /**
     * 获取chat key
     * @param agentId agent Id
     * @param refresh 是否刷新key
     */
    @SkAccess
    @ChatKeyAccess
    @GetMapping("/{agentId}")
    public R<String> getChatKey(@PathVariable("agentId") Long agentId,
                                @RequestParam("refresh") boolean refresh) {
        return R.data(agentChatKeyService.getChatKey(agentId, refresh));
    }

    @SkAccess
    @ChatKeyAccess
    @GetMapping("/{chatKey}/get-agent-id")
    public R<Long> getAgentIdByChatKey(@PathVariable("chatKey") String chatKey) {
        return R.data(agentChatKeyService.getAgentIdByChatKey(chatKey));
    }

    /**
     * 查询嵌入身份密钥（docs/identity-propagation-design.md §6.M6）。
     * 未启用返回 null。仅平台登录用户可见（无 @ChatKeyAccess/@SkAccess——密钥
     * 绝不能暴露给免登通道，chatKey token 持有者拿到 secret 即可伪造任意外部用户）
     */
    @GetMapping("/{agentId}/embed-secret")
    public R<String> getEmbedSecret(@PathVariable("agentId") Long agentId) {
        return R.data(agentChatKeyService.getEmbedSecret(agentId));
    }

    /**
     * 生成/轮换嵌入身份密钥（旧密钥转 prev 双活）。访问控制同上，仅平台登录用户
     */
    @PostMapping("/{agentId}/embed-secret/rotate")
    public R<String> rotateEmbedSecret(@PathVariable("agentId") Long agentId) {
        return R.data(agentChatKeyService.rotateEmbedSecret(agentId));
    }

    /**
     * 停用嵌入身份验证（embedSecret 与 prev 一并置空）。停用后带 userJwt 的
     * 换 token 请求被拒（401 未启用），嵌入访客退回纯匿名。访问控制同上
     */
    @DeleteMapping("/{agentId}/embed-secret")
    public R<Boolean> disableEmbedSecret(@PathVariable("agentId") Long agentId) {
        agentChatKeyService.disableEmbedSecret(agentId);
        return R.data(true);
    }
}
