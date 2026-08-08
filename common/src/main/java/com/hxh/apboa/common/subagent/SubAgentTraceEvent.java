package com.hxh.apboa.common.subagent;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 描述：子智能体执行事件的序列化与持久化契约，携带协议版本号。
 *      invocationId 取自父级 ToolUseBlock.id，同一子智能体会话被多次调用时仍保持唯一。
 *
 * @author huxuehao
 */
public class SubAgentTraceEvent {

    public static final int PROTOCOL_VERSION = 1;
    public static final String METADATA_KEY = "apboa.subagent.trace";
    public static final String CUSTOM_EVENT_NAME = "APBOA_SUBAGENT_EVENT";
    /** Marker on the parent tool result; its dedicated card replaces the generic tool entry. */
    public static final String FINAL_RESULT_METADATA_KEY = "apboa.subagent.final";

    private int protocolVersion = PROTOCOL_VERSION;
    private String eventId;
    private String invocationId;
    private String rootRunId;
    private String parentInvocationId;
    private long sequence;
    private LocalDateTime occurredAt;
    private SubAgentTraceEventType eventType;
    private Agent agent;
    private Map<String, Object> payload = new LinkedHashMap<>();

    public int getProtocolVersion() { return protocolVersion; }
    public void setProtocolVersion(int protocolVersion) { this.protocolVersion = protocolVersion; }
    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public String getInvocationId() { return invocationId; }
    public void setInvocationId(String invocationId) { this.invocationId = invocationId; }
    public String getRootRunId() { return rootRunId; }
    public void setRootRunId(String rootRunId) { this.rootRunId = rootRunId; }
    public String getParentInvocationId() { return parentInvocationId; }
    public void setParentInvocationId(String parentInvocationId) { this.parentInvocationId = parentInvocationId; }
    public long getSequence() { return sequence; }
    public void setSequence(long sequence) { this.sequence = sequence; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
    public void setOccurredAt(LocalDateTime occurredAt) { this.occurredAt = occurredAt; }
    public SubAgentTraceEventType getEventType() { return eventType; }
    public void setEventType(SubAgentTraceEventType eventType) { this.eventType = eventType; }
    public Agent getAgent() { return agent; }
    public void setAgent(Agent agent) { this.agent = agent; }
    public Map<String, Object> getPayload() { return payload; }
    public void setPayload(Map<String, Object> payload) {
        this.payload = payload == null ? new LinkedHashMap<>() : new LinkedHashMap<>(payload);
    }

    public static class Agent {
        private Long definitionId;
        private String code;
        private String title;
        private String runtimeId;
        private String subagentSessionId;

        public Long getDefinitionId() { return definitionId; }
        public void setDefinitionId(Long definitionId) { this.definitionId = definitionId; }
        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getRuntimeId() { return runtimeId; }
        public void setRuntimeId(String runtimeId) { this.runtimeId = runtimeId; }
        public String getSubagentSessionId() { return subagentSessionId; }
        public void setSubagentSessionId(String subagentSessionId) { this.subagentSessionId = subagentSessionId; }
    }
}
