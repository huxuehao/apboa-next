package com.hxh.apboa.common.vo;

import com.hxh.apboa.common.config.SerializableEnable;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 工具调试调用结果（结构对齐 McpToolDebugResultVO，前端调试面板共用同一套展示逻辑）
 *
 * @author vaulka
 */
@Data
public class ToolDebugResultVO implements SerializableEnable {

    /**
     * 是否成功
     */
    private boolean success;

    /**
     * 工具名称
     */
    private String toolName;

    /**
     * 工具返回内容
     */
    private Object content;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 执行耗时（毫秒）
     */
    private Long durationMs;

    /**
     * 执行时间
     */
    private LocalDateTime executedAt;
}
