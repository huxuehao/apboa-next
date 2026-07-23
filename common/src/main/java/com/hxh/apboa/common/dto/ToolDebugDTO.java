package com.hxh.apboa.common.dto;

import com.hxh.apboa.common.config.SerializableEnable;
import java.util.Map;
import lombok.Data;

/**
 * 工具调试调用请求
 *
 * @author vaulka
 */
@Data
public class ToolDebugDTO implements SerializableEnable {

    /**
     * 工具主键 ID（tool 表主键，非业务编号 toolId）
     */
    private Long id;

    /**
     * 工具调用参数
     */
    private Map<String, Object> input;
}
