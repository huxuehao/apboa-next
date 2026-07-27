package com.hxh.apboa.gateway.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hxh.apboa.gateway.entity.GatewayApp;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 描述：网关应用Mapper
 * IgnoreTenant后缀方法供网关数据面跨租户加载使用
 *
 * @author huxuehao
 **/
@Mapper
public interface GatewayAppMapper extends BaseMapper<GatewayApp> {

    /**
     * 加载所有在线且可用的应用（数据面启动/同步用）
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("select * from gateway_app where online = 1 and enabled = 1")
    List<GatewayApp> selectOnlineIgnoreTenant();

    /**
     * 按ID集合加载应用（数据面同步用）
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("<script>select * from gateway_app where id in " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    List<GatewayApp> selectByIdsIgnoreTenant(@Param("ids") List<Long> ids);
}
