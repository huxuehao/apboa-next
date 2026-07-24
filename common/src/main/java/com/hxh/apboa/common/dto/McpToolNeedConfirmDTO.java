package com.hxh.apboa.common.dto;

import com.hxh.apboa.common.config.SerializableEnable;
import java.util.List;
import lombok.Data;

/**
 * MCP 工具批量设置「需要人工确认」请求（HITL §6.6）
 *
 * @author huxuehao
 */
@Data
public class McpToolNeedConfirmDTO implements SerializableEnable {
    private List<Long> toolIds;
    private Boolean needConfirm;
}
