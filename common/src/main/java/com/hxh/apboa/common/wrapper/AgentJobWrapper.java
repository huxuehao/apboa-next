package com.hxh.apboa.common.wrapper;

import com.hxh.apboa.common.config.SerializableEnable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 描述：智能体任务包装类
 *
 * @author huxuehao
 **/
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AgentJobWrapper implements SerializableEnable {
    private String jobName;
    private String bizName;
    // 类型(AGENT、WORKFLOW、SIMPLE)
    private String type;
    // 关联业务ID
    private String bizId;
    // 智能体用户提示词（仅 AGENT）
    private String userPrompt;
    // 工具授权模式（仅 AGENT）：AUTO_APPROVE=一键授权（缺省）/ AUTO_REJECT=拒绝授权，
    // 决定执行中需人工确认的工具被自动批准还是自动拒绝
    private String confirmMode;
    // 流程输入配置：开始节点参数展平 Map（仅 WORKFLOW）
    private Map<String, Object> params;
    // 工作流自定义变量覆盖（仅 WORKFLOW，透传 WorkflowRunRequest.variables）
    private Map<String, Object> variables;
}
