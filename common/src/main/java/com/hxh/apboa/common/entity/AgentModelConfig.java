package com.hxh.apboa.common.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.hxh.apboa.common.config.SerializableEnable;
import com.hxh.apboa.common.config.mybatis.JsonNodeTypeHandler;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hxh.apboa.common.consts.TableConst;
import lombok.*;

/**
 * 智能体与候选对话模型关联（默认模型仍在 agent_definition.model_config_id，本表只存额外候选）
 */
@Getter
@Setter
@TableName(value = TableConst.AGENT_MODEL_CONFIG, autoResultMap = true)
@AllArgsConstructor
@NoArgsConstructor
public class AgentModelConfig implements SerializableEnable {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    @TableField(fill = FieldFill.INSERT)
    private Long tenantId;
    private Long agentDefinitionId;
    private Long modelConfigId;
    /** 该候选模型的参数覆盖（结构同 agent_definition.model_params_override；null=跟随模型默认） */
    @TableField(typeHandler = JsonNodeTypeHandler.class)
    private JsonNode modelParamsOverride;
    private Integer sort;
}
