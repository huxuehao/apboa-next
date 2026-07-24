package com.hxh.apboa.runtime.endpoint;

import com.hxh.apboa.common.config.auth.ChatKeyAccess;
import com.hxh.apboa.common.config.auth.SkAccess;
import com.hxh.apboa.engine.hitl.PendingSubConfirmRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 子智能体 HITL 确认端点：主会话前端对「子智能体内需确认工具」的决策下行与刷新恢复查询。
 *
 * <p>与主流程 {@code /agui/resume/{threadId}}（走暂停态持久化 + SSE 续流）不同，
 * 子确认走 {@link PendingSubConfirmRegistry} 进程内挂起-唤醒：子智能体 run 未结束、
 * SSE 主流仍在，决策只需唤醒挂起的 SubAgentTool，续跑事件沿原 SSE 流继续下发，
 * 本端点即时返回 JSON 不产生新事件流。路径前缀/鉴权与现有 /agui/* 一致。
 *
 * @author huxuehao
 */
@Slf4j
@RestController
public class SubAgentConfirmEndPoint {

    /**
     * 提交子智能体确认决策，唤醒挂起等待的 SubAgentTool 续跑。
     *
     * @param body {subSessionId, decisions:[{toolUseId, name, approved}]}
     * @return {resumed: true} 或 {resumed: false, error}（挂起条目已超时/已决策/实例重启丢失）
     */
    @SkAccess
    @ChatKeyAccess
    @PostMapping(
            value = "${agentscope.agui.path-prefix:/agui}/subagent/resume",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> resume(@RequestBody SubAgentResumeRequest body) {
        if (body == null || body.subSessionId() == null || body.subSessionId().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("resumed", false, "error", "subSessionId 不能为空"));
        }
        boolean resumed = PendingSubConfirmRegistry.complete(body.subSessionId(), body.decisions());
        if (!resumed) {
            return ResponseEntity.ok(
                    Map.of("resumed", false, "error", "确认已失效（已超时按全拒绝处理，或已完成决策）"));
        }
        return ResponseEntity.ok(Map.of("resumed", true));
    }

    /**
     * 查询主会话下所有挂起中的子确认请求，供前端刷新/重进会话重建确认 UI。
     *
     * @param threadId 主会话 ID
     * @return {pending: [PendingInfo]}，元素结构与 SUBAGENT_CONFIRM_REQUIRED 事件载荷同构
     */
    @SkAccess
    @ChatKeyAccess
    @GetMapping(
            value = "${agentscope.agui.path-prefix:/agui}/subagent/pending",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> pending(@RequestParam("threadId") String threadId) {
        return ResponseEntity.ok(
                Map.of("pending", PendingSubConfirmRegistry.findByParentThreadId(threadId)));
    }

    /** 决策下行请求体，Decision 结构与主 resume 的 ResumeDecision 对齐 */
    public record SubAgentResumeRequest(
            String subSessionId, List<PendingSubConfirmRegistry.Decision> decisions) {}
}
