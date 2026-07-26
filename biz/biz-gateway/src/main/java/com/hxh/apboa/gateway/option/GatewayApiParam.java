package com.hxh.apboa.gateway.option;

import com.hxh.apboa.gateway.enums.GatewayParamPosition;
import com.hxh.apboa.gateway.enums.GatewayParamType;
import lombok.Getter;
import lombok.Setter;

/**
 * 描述：网关API参数定义
 * 定义请求参数的位置、校验规则，以及到工作流开始节点参数的映射关系
 *
 * @author huxuehao
 **/
@Getter
@Setter
public class GatewayApiParam {
    /**
     * 参数位置
     */
    private GatewayParamPosition position;
    /**
     * 参数名（PATH为占位符名、QUERY为查询键、HEADER为请求头名、BODY为JSON顶层字段名）
     */
    private String key;
    /**
     * 参数类型
     */
    private GatewayParamType type = GatewayParamType.STRING;
    /**
     * 是否必填
     */
    private Boolean required = false;
    /**
     * 默认值（非必填且未传时生效）
     */
    private String defaultVal;
    /**
     * 映射到工作流开始节点的参数名（为空时使用key）
     */
    private String workflowParam;
    /**
     * 描述
     */
    private String remark;

    /**
     * 获取工作流参数名，未配置映射时回退到参数名本身
     */
    public String obtainWorkflowParam() {
        return workflowParam == null || workflowParam.isBlank() ? key : workflowParam;
    }
}
