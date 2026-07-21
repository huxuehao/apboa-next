package com.hxh.apboa.agent.service.impl;

import com.hxh.apboa.agent.mapper.AgentScopeSessionMapper;
import com.hxh.apboa.agent.mapper.ChatSessionMapper;
import com.hxh.apboa.agent.service.AgentDefinitionService;
import com.hxh.apboa.agent.service.ChatMessageService;
import com.hxh.apboa.agent.service.ChatSessionService;
import com.hxh.apboa.common.consts.SysConst;
import com.hxh.apboa.common.dto.ChatMessageAppendDTO;
import com.hxh.apboa.common.dto.ChatSessionCreateDTO;
import com.hxh.apboa.common.dto.ChatSessionQueryDTO;
import com.hxh.apboa.common.entity.AgentDefinition;
import com.hxh.apboa.common.entity.ChatMessage;
import com.hxh.apboa.common.entity.ChatSession;
import com.hxh.apboa.common.mp.support.PageParams;
import com.hxh.apboa.common.util.BeanUtils;
import com.hxh.apboa.common.util.FolderUtils;
import com.hxh.apboa.common.util.UserUtils;
import com.hxh.apboa.common.vo.ChatMessageVO;
import com.hxh.apboa.common.vo.ChatMessagePageVO;
import com.hxh.apboa.common.vo.ChatSessionVO;
import com.hxh.apboa.common.router.MessageTableRouter;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hxh.apboa.common.mp.support.MP;
import io.agentscope.spring.boot.agui.common.ThreadSessionManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 聊天会话 Service 实现：新会话插根消息并设 current_message_id；追加/重新生成插新消息并更新 current_message_id；切换分支仅更新 current_message_id；回显按 path 查消息链。
 *
 * @author huxuehao
 */
@Service
@RequiredArgsConstructor
public class ChatSessionServiceImpl extends ServiceImpl<ChatSessionMapper, ChatSession> implements ChatSessionService {

    private final ChatMessageService chatMessageService;
    private final MessageTableRouter messageTableRouter;
    private final ThreadSessionManager sessionManager;
    private final AgentScopeSessionMapper agentScopeSessionMapper;
    private final AgentDefinitionService agentDefinitionService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChatSessionVO createSession(ChatSessionCreateDTO dto, Long userId) {
        if (userId == null || userId == 0L) {
            throw new RuntimeException("用户未登录");
        }
        if (dto.getAgentId() == null ) {
            throw new RuntimeException("agentId 不能为空");
        }

        // 校验智能体的合法性
        AgentDefinition agentDefinition = agentDefinitionService.getOne(
                new LambdaQueryWrapper<AgentDefinition>()
                        .eq(AgentDefinition::getId, dto.getAgentId())
                        .eq(AgentDefinition::getEnabled, true),
                false);

        if (agentDefinition == null) {
            throw new RuntimeException("智能体不存在或已禁用");
        }

        ChatSession session = new ChatSession();
        session.setUserId(userId);
        session.setAgentId(dto.getAgentId());
        session.setTitle(dto.getTitle() != null ? dto.getTitle() : "新对话");

        save(session);

        ChatMessage root = new ChatMessage();
        root.setSessionId(session.getId());
        root.setRole("system");
        root.setContent("");
        root.setParentId(null);
        chatMessageService.save(root);
        root.setPath(String.valueOf(root.getId()));
        root.setDepth(0);
        chatMessageService.updateById(root);

        session.setCurrentMessageId(root.getId());
        updateById(session);

        if (dto.getInitWorkspace() != null && dto.getInitWorkspace()) {
            FolderUtils.mkdirsByRelativePath(SysConst.getWorkspacePath() + "/" + session.getId());
        }

        return toSessionVO(session);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChatMessageVO appendMessage(Long sessionId, ChatMessageAppendDTO dto) {
        ChatSession session = getAndCheckSession(sessionId);
        if (session.getMessageTable() == null || session.getMessageTable().isEmpty()) {
            ChatMessage parent = getMessageBy(session.getCurrentMessageId(), sessionId);
            return saveNewMessageAndMoveCursor(session, parent, dto.getRole(), dto.getContent());
        }
        throw new RuntimeException("当前会话已经归档，不支持继续对话");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChatMessageVO regenerateMessage(Long sessionId, ChatMessageAppendDTO dto) {
        ChatSession session = getAndCheckSession(sessionId);
        ChatMessage parent = getMessageBy(session.getCurrentMessageId(), sessionId);
        return saveNewMessageAndMoveCursor(session, parent, dto.getRole(), dto.getContent());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void switchCurrentMessage(Long sessionId, Integer messageId) {
        ChatSession session = getAndCheckSession(sessionId);
        session.setCurrentMessageId(messageId);
        updateById(session);
    }

    @Override
    public List<ChatMessageVO> getCurrentMessages(Long sessionId) {
        ChatSession session = getById(sessionId);
        if (session == null) {
            return new ArrayList<>();
        }
        Integer curId = session.getCurrentMessageId();
        if (curId == null) {
            return new ArrayList<>();
        }
        // 根据 messageTable 决定从哪张表查 current message
        ChatMessage cur;
        if (isArchived(session)) {
            cur = messageTableRouter.getById(curId, session.getMessageTable());
        } else {
            cur = chatMessageService.getById(curId);
        }
        if (cur == null || !cur.getSessionId().equals(session.getId())) {
            return new ArrayList<>();
        }
        String path = cur.getPath();
        if (path == null || path.isEmpty()) {
            return new ArrayList<>();
        }
        List<Integer> ids = Arrays.stream(path.split("/"))
                .filter(s -> !s.isEmpty())
                .map(Integer::parseInt)
                .collect(Collectors.toList());
        if (ids.isEmpty()) {
            ids.add(curId);
        }
        List<ChatMessage> list = chatMessageService.listByIdsOrderByDepth(ids, session.getMessageTable());
        return BeanUtils.copyList(list, ChatMessageVO.class);
    }
    @Override
    public ChatMessagePageVO getCurrentMessagesPaged(Long sessionId, Integer beforeDepth, int size) {
        // 1. 解析当前路径上的所有消息 ID
        ChatSession session = getById(sessionId);
        ChatMessagePageVO result = new ChatMessagePageVO();
        if (session == null || session.getCurrentMessageId() == null) {
            result.setMessages(new ArrayList<>());
            result.setHasMore(false);
            return result;
        }
        // 根据 messageTable 决定从哪张表查 current message
        ChatMessage cur;
        if (isArchived(session)) {
            cur = messageTableRouter.getById(session.getCurrentMessageId(), session.getMessageTable());
        } else {
            cur = chatMessageService.getById(session.getCurrentMessageId());
        }
        if (cur == null || !cur.getSessionId().equals(session.getId()) || cur.getPath() == null || cur.getPath().isEmpty()) {
            result.setMessages(new ArrayList<>());
            result.setHasMore(false);
            return result;
        }
        List<Integer> ids = Arrays.stream(cur.getPath().split("/"))
                .filter(s -> !s.isEmpty())
                .map(Integer::parseInt)
                .collect(Collectors.toList());
        if (ids.isEmpty()) {
            ids.add(session.getCurrentMessageId());
        }

        // 2. 查询该路径上全部消息并按 depth 升序
        List<ChatMessage> allMessages = chatMessageService.listByIdsOrderByDepth(ids, session.getMessageTable());

        // 3. 根据游标切片
        List<ChatMessage> page;
        if (beforeDepth == null) {
            // 首次加载：取末尾 size 条
            int from = Math.max(0, allMessages.size() - size);
            page = allMessages.subList(from, allMessages.size());
        } else {
            // 加载 beforeDepth 之前的消息：取 depth < beforeDepth 的末尾 size 条
            List<ChatMessage> candidates = allMessages.stream()
                    .filter(m -> m.getDepth() < beforeDepth)
                    .collect(Collectors.toList());
            int from = Math.max(0, candidates.size() - size);
            page = candidates.subList(from, candidates.size());
        }

        // 4. 判断是否还有更早的消息
        boolean hasMore;
        if (page.isEmpty()) {
            hasMore = false;
        } else {
            int earliestDepth = page.getFirst().getDepth();
            hasMore = allMessages.stream().anyMatch(m -> m.getDepth() < earliestDepth);
        }

        result.setMessages(BeanUtils.copyList(page, ChatMessageVO.class));
        result.setHasMore(hasMore);
        result.setNextBeforeDepth(page.isEmpty() ? null : page.getFirst().getDepth());
        return result;
    }



    @Override
    public List<ChatSessionVO> listSessions(ChatSessionQueryDTO query) {
        Long userId = query.getUserId() != null ? query.getUserId() : UserUtils.getId();
        return lambdaQuery()
                .eq(userId != null, ChatSession::getUserId, userId)
                .eq(query.getAgentId() != null, ChatSession::getAgentId, query.getAgentId())
                .orderByDesc(ChatSession::getIsPinned)
                .orderByDesc(ChatSession::getUpdatedAt)
                .list()
                .stream()
                .map(this::toSessionVO)
                .collect(Collectors.toList());
    }

    @Override
    public IPage<ChatSessionVO> pageSessions(PageParams pageParams, ChatSessionQueryDTO query) {
        Long userId = query.getUserId() != null ? query.getUserId() : UserUtils.getId();
        LambdaQueryWrapper<ChatSession> wrapper = new LambdaQueryWrapper<ChatSession>()
                .eq(userId != null, ChatSession::getUserId, userId)
                .eq(query.getAgentId() != null, ChatSession::getAgentId, query.getAgentId())
                .eq(query.getIsPinned() != null, ChatSession::getIsPinned, query.getIsPinned())
                .orderByDesc(ChatSession::getUpdatedAt);
        IPage<ChatSession> page = page(MP.getPage(pageParams), wrapper);
        return BeanUtils.copyPage(page, ChatSessionVO.class);
    }

    @Override
    public ChatSessionVO getSessionDetail(Long id) {
        ChatSession session = getById(id);
        if (session == null) {
            return null;
        }
        return toSessionVO(session);
    }

    private ChatSession getAndCheckSession(Long sessionId) {
        ChatSession session = getById(sessionId);
        if (session == null) {
            throw new RuntimeException("会话不存在或已删除");
        }
        if (!session.getUserId().equals(UserUtils.getId())) {
            throw new RuntimeException("无权限操作该会话");
        }
        return session;
    }

    private ChatMessage getMessageBy(Integer messageId, Long sessionId) {
        // 先查 session 确定消息所在表
        ChatSession session = getById(sessionId);
        if (session == null) {
            throw new RuntimeException("会话不存在或已删除");
        }
        ChatMessage msg;
        if (isArchived(session)) {
            // 已归档：从归档表查询
            msg = messageTableRouter.getById(messageId, session.getMessageTable());
        } else {
            msg = chatMessageService.getById(messageId);
        }
        if (msg == null || !msg.getSessionId().equals(sessionId)) {
            throw new RuntimeException("当前消息不存在或不属于该会话");
        }
        return msg;
    }

    /**
     * 在父消息后插入新消息并更新会话的 current_message_id（用于正常追加与重新生成）
     */
    private ChatMessageVO saveNewMessageAndMoveCursor(ChatSession session, ChatMessage parent, String role, String content) {
        if (role == null || content == null) {
            throw new RuntimeException("role 与 content 不能为空");
        }
        // 归档会话只读：阻止追加消息
        if (isArchived(session)) {
            throw new RuntimeException("该会话已被归档，无法继续对话，请创建新会话");
        }

        ChatMessage msg = new ChatMessage();
        msg.setSessionId(session.getId());
        msg.setRole(role);
        msg.setContent(content);
        msg.setParentId(parent.getId());
        chatMessageService.save(msg);

        String parentPath = parent.getPath();
        String newPath = (parentPath == null || parentPath.isEmpty())
                ? String.valueOf(msg.getId())
                : parentPath + "/" + msg.getId();
        msg.setPath(newPath);
        msg.setDepth((parent.getDepth() == null ? 0 : parent.getDepth()) + 1);
        chatMessageService.updateById(msg);

        session.setCurrentMessageId(msg.getId());
        updateById(session);

        return BeanUtils.copy(msg, ChatMessageVO.class);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void pinSession(Long id) {
        ChatSession session = getAndCheckSession(id);
        session.setIsPinned(true);
        session.setPinTime(java.time.LocalDateTime.now());
        updateById(session);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unpinSession(Long id) {
        ChatSession session = getAndCheckSession(id);
        session.setIsPinned(false);
        session.setPinTime(null);
        updateById(session);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateTitle(Long id, String title) {
        ChatSession session = getAndCheckSession(id);
        session.setTitle(title != null ? title : "新对话");
        updateById(session);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateCurrentMessageContent(Long sessionId, String content) {
        ChatSession session = getAndCheckSession(sessionId);
        if (isArchived(session)) {
            throw new RuntimeException("已归档会话不支持修改消息");
        }
        Integer msgId = session.getCurrentMessageId();
        if (msgId == null) {
            throw new RuntimeException("当前会话无活跃消息");
        }
        ChatMessage msg = chatMessageService.getById(msgId);
        if (msg == null || !msg.getSessionId().equals(sessionId)) {
            throw new RuntimeException("当前消息不存在或不属于该会话");
        }
        msg.setContent(content);
        chatMessageService.updateById(msg);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteSession(Long id) {
        ChatSession session = getAndCheckSession(id);
        // 如果已归档，先删除归档表中的消息
        if (isArchived(session)) {
            messageTableRouter.deleteBySessionId(session.getId(), session.getMessageTable());
        }
        // 再删除主表 chat_message 中的消息
        chatMessageService.lambdaUpdate().eq(ChatMessage::getSessionId, session.getId()).remove();
        removeById(id);

        agentScopeSessionMapper.deleteById(String.valueOf(session.getId()));

        // 删除 agentscope session
        if (sessionManager != null) {
            sessionManager.removeSession(String.valueOf(session.getId()));
        }

        // 删除工作空间目录（文件 + sessionId文件夹本身）
        String workspacePath = SysConst.getWorkspacePath() + "/" + session.getId();
        FolderUtils.deleteRecursively(workspacePath);
    }

    private ChatSessionVO toSessionVO(ChatSession session) {
        return BeanUtils.copy(session, ChatSessionVO.class);
    }

    /**
     * 判断会话是否已成功归档到分表。
     * 仅当 message_table 以 chat_message_ 开头时才视为已归档，
     * 排除 null、空串、ARCHIVE_FAILED 等非归档表名。
     */
    private boolean isArchived(ChatSession session) {
        String mt = session.getMessageTable();
        return mt != null && !mt.isBlank() && mt.startsWith("chat_message_");
    }
}

