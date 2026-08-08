package com.hxh.apboa.common.vo;

import com.hxh.apboa.common.config.SerializableEnable;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SubAgentRunVO implements SerializableEnable {
    private String invocationId;
    private String parentInvocationId;
    private String rootRunId;
    private String agentCode;
    private String agentTitle;
    private String subagentSessionId;
    private String status;
    private String task;
    private String summary;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private List<SubAgentTraceEventVO> events;
}
