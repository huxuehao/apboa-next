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
    // 智能体用户提示词
    private String userPrompt;
    // 流程输入配置
    private Map<String, Object> params;
    private Map<String, Object> variables;
}
