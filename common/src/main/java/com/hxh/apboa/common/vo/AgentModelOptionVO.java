package com.hxh.apboa.common.vo;

import lombok.Data;

/**
 * 智能体候选模型选项（对话页模型切换下拉的数据源，detail 时拼装，含默认模型）
 */
@Data
public class AgentModelOptionVO {

    private Long id;

    /** 模型显示名（model_config.name） */
    private String name;

    /** 供应商类型（展示分组/图标用） */
    private String providerType;

    /** 模型描述（下拉菜单第二行） */
    private String description;

    /** 展示图标（antd 图标组件名；null=前端用默认 DeploymentUnitOutlined） */
    private String logo;

    /** 展示图标颜色（hex；null=前端用默认主题色） */
    private String logoColor;

    /** 是否 agent 默认模型（agent_definition.model_config_id） */
    private Boolean isDefault;

    /** 该模型是否支持会话级思考开关（切模型后前端据此显隐思考按钮） */
    private Boolean thinkingSwitchSupported;
}
