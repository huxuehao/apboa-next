package com.hxh.apboa.gateway.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hxh.apboa.gateway.entity.GatewayApiWorkflow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 描述：网关API与工作流关联Mapper
 *
 * @author huxuehao
 **/
@Mapper
public interface GatewayApiWorkflowMapper extends BaseMapper<GatewayApiWorkflow> {

    /**
     * 按API ID查询绑定关系（数据面挂载路由用）
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("<script>select * from gateway_api_workflow where api_id in " +
            "<foreach collection='apiIds' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    List<GatewayApiWorkflow> selectByApiIdsIgnoreTenant(@Param("apiIds") List<Long> apiIds);

    /**
     * 按工作流ID查询绑定的API（工作流重新发布后触发API重注册用）
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("select * from gateway_api_workflow where workflow_id = #{workflowId}")
    List<GatewayApiWorkflow> selectByWorkflowIgnoreTenant(@Param("workflowId") Long workflowId);
}
