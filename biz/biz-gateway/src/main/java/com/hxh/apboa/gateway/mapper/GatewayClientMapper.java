package com.hxh.apboa.gateway.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hxh.apboa.gateway.entity.GatewayClient;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 描述：网关客户端Mapper
 *
 * @author huxuehao
 **/
@Mapper
public interface GatewayClientMapper extends BaseMapper<GatewayClient> {

    /**
     * 加载所有可用客户端（数据面启动预热鉴权缓存用）
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("select * from gateway_client where enabled = 1")
    List<GatewayClient> selectAllIgnoreTenant();

    /**
     * 按ID集合加载客户端（数据面同步用）
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("<script>select * from gateway_client where id in " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    List<GatewayClient> selectByIdsIgnoreTenant(@Param("ids") List<Long> ids);
}
