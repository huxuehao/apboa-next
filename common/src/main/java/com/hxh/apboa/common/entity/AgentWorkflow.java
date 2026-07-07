package com.hxh.apboa.common.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.hxh.apboa.common.config.SerializableEnable;
import com.hxh.apboa.common.consts.TableConst;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 智能体与工具关联
 *
 * @author huxuehao
 */
@Getter
@Setter
@TableName(TableConst.AGENT_WORKFLOW)
@AllArgsConstructor
@NoArgsConstructor
public class AgentWorkflow implements SerializableEnable {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    @TableField(fill = FieldFill.INSERT)
    private Long tenantId;
    private Long agentDefinitionId;
    private Long workflowId;
}
