package com.hxh.apboa.console.agent;

import com.hxh.apboa.agent.lock.SessionLockManager;
import com.hxh.apboa.agent.service.ChatSessionService;
import com.hxh.apboa.common.config.auth.ChatKeyAccess;
import com.hxh.apboa.common.config.auth.SkAccess;
import com.hxh.apboa.common.dto.ChatMessageAppendDTO;
import com.hxh.apboa.common.enums.ConfirmMode;
import com.hxh.apboa.common.dto.ChatSessionCreateDTO;
import com.hxh.apboa.common.dto.ChatSessionQueryDTO;
import com.hxh.apboa.common.mp.support.PageParams;
import com.hxh.apboa.common.r.R;
import com.hxh.apboa.common.util.UserUtils;
import com.hxh.apboa.common.vo.ChatMessageVO;
import com.hxh.apboa.common.vo.ChatMessagePageVO;
import com.hxh.apboa.common.vo.ChatSessionVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 聊天会话 Controller：创建会话、追加/重新生成消息、切换分支、回显当前对话、会话列表与详情
 *
 * @author huxuehao
 */
@RestController
@RequestMapping("/agent/chat/session")
@RequiredArgsConstructor
public class ChatSessionController {

    private final ChatSessionService chatSessionService;
    private final SessionLockManager sessionLockManager;

    /**
     * 创建新会话（插入根消息并设置 current_message_id）
     */
    @SkAccess
    @ChatKeyAccess
    @PostMapping
    public R<ChatSessionVO> create(@RequestBody ChatSessionCreateDTO dto) {
        return R.data(chatSessionService.createSession(dto, UserUtils.getId()));
    }

    /**
     * 正常对话追加消息
     */
    @SkAccess
    @ChatKeyAccess
    @PostMapping("/{sessionId}/message")
    public R<ChatMessageVO> appendMessage(@PathVariable("sessionId") Long sessionId, @RequestBody ChatMessageAppendDTO dto) {
        ReentrantLock lock = sessionLockManager.getLock(sessionId);
        lock.lock();
        try {
            return R.data(chatSessionService.appendMessage(sessionId, dto));
        } finally {
            lock.unlock();
            sessionLockManager.cleanupIfUnused(sessionId, lock);
        }
    }

    /**
     * 重新生成（新分支，更新 current_message_id）
     */
    @SkAccess
    @ChatKeyAccess
    @PostMapping("/{sessionId}/regenerate")
    public R<ChatMessageVO> regenerate(@PathVariable("sessionId") Long sessionId, @RequestBody ChatMessageAppendDTO dto) {
        return R.data(chatSessionService.regenerateMessage(sessionId, dto));
    }

    /**
     * 切换历史分支（仅更新 current_message_id）
     */
    @SkAccess
    @ChatKeyAccess
    @PutMapping("/{sessionId}/current")
    public R<Void> switchCurrentMessage(@PathVariable("sessionId") Long sessionId, @RequestParam("messageId") Integer messageId) {
        chatSessionService.switchCurrentMessage(sessionId, messageId);
        return R.success("操作成功");
    }

    /**
     * 回显当前完整对话（按 path 查消息链，按 depth 排序）
     */
    @SkAccess
    @ChatKeyAccess
    @GetMapping("/{sessionId}/messages/current")
    public R<List<ChatMessageVO>> getCurrentMessages(@PathVariable("sessionId") Long sessionId) {
        return R.data(chatSessionService.getCurrentMessages(sessionId));
    }

    /**
     * 分页加载当前对话消息（滚动加载历史）
     * 首次加载：不传 beforeDepth，返回最新 50 条
     * 加载更多：传入上一次返回的 nextBeforeDepth，返回更早的 50 条
     */
    @SkAccess
    @ChatKeyAccess
    @GetMapping("/{sessionId}/messages/paged")
    public R<ChatMessagePageVO> getCurrentMessagesPaged(
            @PathVariable("sessionId") Long sessionId,
            @RequestParam(value = "beforeDepth", required = false) Integer beforeDepth,
            @RequestParam(value = "size", defaultValue = "50") int size) {
        return R.data(chatSessionService.getCurrentMessagesPaged(sessionId, beforeDepth, size));
    }

    /**
     * 查询会话 HITL 授权模式（Redis，无记录=MANUAL 逐步确认）
     */
    @SkAccess
    @ChatKeyAccess
    @GetMapping("/{sessionId}/confirm-mode")
    public R<String> getConfirmMode(@PathVariable("sessionId") Long sessionId) {
        return R.data(chatSessionService.getConfirmMode(sessionId).name());
    }

    /**
     * 设置会话 HITL 授权模式（AUTO_APPROVE 一键授权 / MANUAL 逐步确认 / AUTO_REJECT 拒绝授权；
     * AUTO_APPROVE/AUTO_REJECT 写 Redis TTL 30 天滚动，MANUAL 删 key。
     * runtime 侧实时读取生效：Hook 放行 / 暂停处理层自动全拒 / 冒泡人工决策）
     */
    @SkAccess
    @ChatKeyAccess
    @PutMapping("/{sessionId}/confirm-mode")
    public R<Boolean> setConfirmMode(@PathVariable("sessionId") Long sessionId,
                                     @RequestParam("mode") String mode) {
        chatSessionService.setConfirmMode(sessionId, ConfirmMode.fromName(mode));
        return R.data(true);
    }

    /**
     * 查询会话思考模式有效值（会话覆盖 ?? 默认开）。仅支持思考开关的模型
     * （agent 详情 thinkingSwitchSupported=true，当前 DASH_SCOPE）有实际意义
     */
    @SkAccess
    @ChatKeyAccess
    @GetMapping("/{sessionId}/thinking-mode")
    public R<Boolean> getThinkingMode(@PathVariable("sessionId") Long sessionId) {
        Boolean override = chatSessionService.getThinkingMode(sessionId);
        return R.data(override == null || override);
    }

    /**
     * 设置会话思考模式（写 Redis 覆盖值，下一条消息生效——runtime 检测变化重建 agent）
     */
    @SkAccess
    @ChatKeyAccess
    @PutMapping("/{sessionId}/thinking-mode")
    public R<Boolean> setThinkingMode(@PathVariable("sessionId") Long sessionId,
                                      @RequestParam("enabled") boolean enabled) {
        chatSessionService.setThinkingMode(sessionId, enabled);
        return R.data(true);
    }

    /**
     * 会话列表（未删除，默认当前用户，可按 agentId 筛选）
     */
    @SkAccess
    @ChatKeyAccess
    @GetMapping("/list")
    public R<List<ChatSessionVO>> list(ChatSessionQueryDTO query) {
        return R.data(chatSessionService.listSessions(query));
    }

    /**
     * 分页查询会话（支持 isPinned 筛选）
     */
    @SkAccess
    @ChatKeyAccess
    @GetMapping("/page")
    public R<IPage<ChatSessionVO>> page(PageParams pageParams, ChatSessionQueryDTO query) {
        return R.data(chatSessionService.pageSessions(pageParams, query));
    }

    /**
     * 会话详情
     */
    @SkAccess
    @ChatKeyAccess
    @GetMapping("/{id}")
    public R<ChatSessionVO> detail(@PathVariable("id") Long id) {
        return R.data(chatSessionService.getSessionDetail(id));
    }

    /**
     * 置顶会话
     */
    @SkAccess
    @ChatKeyAccess
    @PutMapping("/{id}/pin")
    public R<Void> pin(@PathVariable("id") Long id) {
        chatSessionService.pinSession(id);
        return R.success("操作成功");
    }

    /**
     * 取消置顶会话
     */
    @SkAccess
    @ChatKeyAccess
    @PutMapping("/{id}/unpin")
    public R<Void> unpin(@PathVariable("id") Long id) {
        chatSessionService.unpinSession(id);
        return R.success("操作成功");
    }

    /**
     * 更新会话标题
     */
    @SkAccess
    @ChatKeyAccess
    @PutMapping("/{id}/title")
    public R<Void> updateTitle(@PathVariable("id") Long id, @RequestParam("title") String title) {
        chatSessionService.updateTitle(id, title);
        return R.success("操作成功");
    }

    /**
     * 更新当前消息内容（后端通过 session 的 current_message_id 定位，调用方无需传 messageId）
     */
    @SkAccess
    @ChatKeyAccess
    @PutMapping("/{sessionId}/current-message/content")
    public R<Void> updateCurrentMessageContent(
            @PathVariable("sessionId") Long sessionId,
            @RequestBody Map<String, String> body) {
        String content = body.get("content");
        if (content == null) {
            return R.fail("content 不能为空");
        }
        chatSessionService.updateCurrentMessageContent(sessionId, content);
        return R.success("操作成功");
    }

    /**
     * 删除会话
     */
    @SkAccess
    @ChatKeyAccess
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable("id") Long id) {
        chatSessionService.deleteSession(id);
        return R.success("操作成功");
    }
}

