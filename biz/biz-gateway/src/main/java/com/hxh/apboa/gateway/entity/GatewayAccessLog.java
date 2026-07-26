package com.hxh.apboa.gateway.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hxh.apboa.common.consts.TableConst;
import com.hxh.apboa.common.entity.BaseTenantEntity;
import com.hxh.apboa.common.mp.annotation.QueryDefine;
import com.hxh.apboa.common.mp.support.QueryCondition;
import lombok.Getter;
import lombok.Setter;

/**
 * 描述：网关API访问日志表
 *
 * @author huxuehao
 **/
@Getter
@Setter
@TableName(TableConst.GATEWAY_ACCESS_LOG)
public class GatewayAccessLog extends BaseTenantEntity {
    /**
     * 网关应用ID
     */
    @QueryDefine(condition = QueryCondition.EQ)
    private Long appId;
    /**
     * 网关API ID
     */
    @QueryDefine(condition = QueryCondition.EQ)
    private Long apiId;
    /**
     * 本次请求的工作流运行记录ID
     */
    private Long workflowRunId;
    /**
     * 请求方法
     */
    @QueryDefine(condition = QueryCondition.EQ)
    private String method;
    /**
     * 请求路径
     */
    @QueryDefine(condition = QueryCondition.LIKE)
    private String path;
    /**
     * 采集的Header参数
     */
    private String headerParams;
    /**
     * 采集的Path参数
     */
    private String pathParams;
    /**
     * 采集的Query参数
     */
    private String queryParams;
    /**
     * 请求体
     */
    private String requestBody;
    /**
     * 响应体
     */
    private String responseBody;
    /**
     * 访问IP
     */
    @QueryDefine(condition = QueryCondition.EQ)
    private String accessIp;
    /**
     * 结果状态：1成功、0失败
     */
    @QueryDefine(condition = QueryCondition.EQ)
    private Integer status;
    /**
     * HTTP响应状态码
     */
    private Integer httpStatus;
    /**
     * 错误信息
     */
    private String error;
    /**
     * 开始时间戳（毫秒）
     */
    private Long startTime;
    /**
     * 结束时间戳（毫秒）
     */
    private Long endTime;
}
