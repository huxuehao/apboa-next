package com.hxh.apboa.gateway.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hxh.apboa.gateway.entity.GatewayClientApi;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 描述：网关客户端与API授权关系Mapper
 *
 * @author huxuehao
 **/
@Mapper
public interface GatewayClientApiMapper extends BaseMapper<GatewayClientApi> {

    /**
     * 按客户端ID集合查询授权关系（数据面同步用）
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("<script>select * from gateway_client_api where client_id in " +
            "<foreach collection='clientIds' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    List<GatewayClientApi> selectByClientIdsIgnoreTenant(@Param("clientIds") List<Long> clientIds);
}
