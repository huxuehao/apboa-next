package com.hxh.apboa.agent.service;

import com.hxh.apboa.common.enums.ConfirmMode;

import com.hxh.apboa.common.dto.ChatMessageAppendDTO;
import com.hxh.apboa.common.dto.ChatSessionCreateDTO;
import com.hxh.apboa.common.dto.ChatSessionQueryDTO;
import com.hxh.apboa.common.entity.ChatSession;
import com.hxh.apboa.common.mp.support.PageParams;
import com.hxh.apboa.common.vo.ChatMessageVO;
import com.hxh.apboa.common.vo.ChatMessagePageVO;
import com.hxh.apboa.common.vo.ChatSessionVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * 聊天会话 Service
 *
 * @author huxuehao
 */
public interface ChatSessionService extends IService<ChatSession> {

    /**
     * 创建新会话：插入根消息并设置 current_message_id
     *
     * @param dto 创建参数（agentId、title）
     * @param userId 用户ID
     * @return 会话 VO
     */
    ChatSessionVO createSession(ChatSessionCreateDTO dto, Long userId);

    /**
     * 正常对话追加：在 current_message_id 后插入新消息并更新 current_message_id
     *
     * @param sessionId 会话 ID
     * @param dto       消息内容（role、content）
     * @return 新消息 VO
     */
    ChatMessageVO appendMessage(Long sessionId, ChatMessageAppendDTO dto);

    /**
     * 重新生成：以当前消息为父节点插入新消息（新分支），并更新 current_message_id
     *
     * @param sessionId 会话 ID
     * @param dto       消息内容（role、content）
     * @return 新消息 VO
     */
    ChatMessageVO regenerateMessage(Long sessionId, ChatMessageAppendDTO dto);

    /**
     * 切换历史分支：仅更新 current_message_id
     *
     * @param sessionId 会话 ID
     * @param messageId 目标消息 ID
     */
    void switchCurrentMessage(Long sessionId, Integer messageId);

    /**
     * 回显当前完整对话：根据 current_message_id 取 path，按 path 的 id 列表查询并按 depth 排序
     *
     * @param sessionId 会话 ID
     * @return 当前路径上的消息列表（按深度升序）
     */
    List<ChatMessageVO> getCurrentMessages(Long sessionId);

    /**
     * 分页加载当前对话消息：首次加载最新 size 条，后续按 beforeDepth 向前加载
     *
     * @param sessionId    会话 ID
     * @param beforeDepth  游标：加载此 depth 之前的消息，null 表示首次加载（取最新）
     * @param size         每页大小
     * @return 分页消息结果
     */
    ChatMessagePageVO getCurrentMessagesPaged(Long sessionId, Integer beforeDepth, int size);

    /**
     * 分页或列表查询会话（未删除，可按 userId、agentId 筛选）
     *
     * @param query 查询条件
     * @return 会话 VO 列表
     */
    List<ChatSessionVO> listSessions(ChatSessionQueryDTO query);

    /**
     * 分页查询会话（支持 isPinned 筛选）
     *
     * @param query 分页查询条件
     * @return 分页会话 VO
     */
    IPage<ChatSessionVO> pageSessions(PageParams pageParams, ChatSessionQueryDTO query);

    /**
     * 会话详情
     *
     * @param id 会话 ID
     * @return 会话 VO，不存在返回 null
     */
    ChatSessionVO getSessionDetail(Long id);

    /**
     * 置顶会话
     *
     * @param id 会话 ID
     */
    void pinSession(Long id);

    /**
     * 取消置顶会话
     *
     * @param id 会话 ID
     */
    void unpinSession(Long id);

    /**
     * 更新会话标题
     *
     * @param id    会话 ID
     * @param title 新标题
     */
    void updateTitle(Long id, String title);

    /**
     * 更新当前消息内容（通过 session 的 current_message_id 定位，用于持久化 UIP 提交数据等场景）
     *
     * @param sessionId 会话 ID
     * @param content   新内容
     */
    void updateCurrentMessageContent(Long sessionId, String content);

    /**
     * 删除会话（物理删除会话及其消息）
     *
     * @param id 会话 ID
     */
    void deleteSession(Long id);

    /**
     * 查询会话「一键授权」开关（Redis，无记录=false 逐步确认）
     *
     * @param sessionId 会话 ID
     * @return 当前授权模式
     */
    ConfirmMode getConfirmMode(Long sessionId);

    /**
     * 设置会话 HITL 授权模式（三态）：AUTO_APPROVE/AUTO_REJECT 写 Redis（TTL 30 天滚动刷新），
     * MANUAL 删 key。runtime 侧在 stopAgent 前/暂停处理层实时读取——一键授权直接放行，
     * 拒绝授权自动全拒续跑，逐步确认冒泡人工决策。
     *
     * @param sessionId 会话 ID
     * @param mode      授权模式
     */
    void setConfirmMode(Long sessionId, ConfirmMode mode);

    /**
     * 查询会话思考模式覆盖值（Redis）。
     *
     * @return true/false=会话覆盖；null=无覆盖（默认开，见 ChatModelFactory 合成语义）
     */
    Boolean getThinkingMode(Long sessionId);

    /**
     * 设置会话思考模式覆盖（写 Redis，TTL 30 天滚动）。
     * runtime 侧 ChatModelFactory 构建模型时读取，AguiRequestProcessor 检测变化重建 agent。
     */
    void setThinkingMode(Long sessionId, boolean enabled);
}

