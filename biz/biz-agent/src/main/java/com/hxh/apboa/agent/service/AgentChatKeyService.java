package com.hxh.apboa.agent.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hxh.apboa.common.entity.AgentChatKey;

/**
 * 描述：智能体对话Key服务
 *
 * @author huxuehao
 **/
public interface AgentChatKeyService extends IService<AgentChatKey> {
    /**
     * 获取或生成智能体对话Key
     *
     * @param agentId 智能体ID
     * @param refresh 是否刷新Key
     * @return 对话Key
     */
    String getChatKey(Long agentId, boolean refresh);

    /**
     * 根据ChatKey获取AgentCode
     * 优先从Redis缓存获取，缓存未命中则从数据库查询并回填缓存
     * 实现缓存穿透防护（布隆过滤器/空值缓存）
     *
     * @param chatKey 对话Key
     * @return AgentCode，如果不存在则返回null
     */
    String getAgentCodeByChatKey(String chatKey);

    Long getAgentIdByChatKey(String chatKey);

    /**
     * 查询嵌入身份密钥（docs/identity-propagation-design.md §6.M6）。
     *
     * @param agentId 智能体ID
     * @return embedSecret；未启用返回 null
     */
    String getEmbedSecret(Long agentId);

    /**
     * 生成/轮换嵌入身份密钥：新密钥生效、旧密钥转 prev 双活（新旧同时可验签，
     * 业务方切换期不瞬断）。chatKey 不存在时先生成。
     *
     * @param agentId 智能体ID
     * @return 新 embedSecret
     */
    String rotateEmbedSecret(Long agentId);

    /**
     * 停用嵌入身份验证：embedSecret 与 prev 一并置空。
     * 停用后带 userJwt 的换 token 请求会被拒绝（401 未启用），嵌入访客退回纯匿名；
     * 治理闭环的最后一环（生成 → 轮换 → 停用）。
     *
     * @param agentId 智能体ID
     */
    void disableEmbedSecret(Long agentId);
}
