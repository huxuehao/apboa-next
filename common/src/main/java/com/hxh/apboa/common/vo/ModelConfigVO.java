package com.hxh.apboa.common.vo;

import com.fasterxml.jackson.databind.JsonNode;
import com.hxh.apboa.common.config.SerializableEnable;
import com.hxh.apboa.common.enums.ModelCategory;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 模型配置VO
 *
 * @author huxuehao
 */
@Data
@EqualsAndHashCode
public class ModelConfigVO implements SerializableEnable {
    private Long id;
    private Long providerId;
    private String name;
    private String modelId;
    private ModelCategory category;
    private JsonNode modelType;
    private String description;
    /** 展示图标（antd 图标组件名；null=前端用默认图标） */
    private String logo;
    /** 展示图标颜色（hex；null=前端用默认主题色） */
    private String logoColor;
    private Boolean streaming;
    private Boolean thinking;
    private Integer contextWindow;
    private Integer maxTokens;
    private Double temperature;
    private Double topP;
    private Integer topK;
    private Double repeatPenalty;
    private Long seed;
    private JsonNode extendConfig;
    private String connectivityStatus;
    private String connectivityMessage;
    private LocalDateTime lastConnectivityCheck;
    private Boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long createdBy;
    private Long updatedBy;
    private List<Object> used;
}
