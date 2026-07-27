package com.hxh.apboa.gateway.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hxh.apboa.gateway.entity.GatewayApi;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 描述：网关API Mapper
 * IgnoreTenant后缀方法供网关数据面跨租户加载使用
 *
 * @author huxuehao
 **/
@Mapper
public interface GatewayApiMapper extends BaseMapper<GatewayApi> {

    /**
     * 加载指定应用下所有在线且可用的API（数据面应用上线用）
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("select * from gateway_api where app_id = #{appId} and online = 1 and enabled = 1")
    List<GatewayApi> selectOnlineByAppIgnoreTenant(@Param("appId") Long appId);

    /**
     * 按ID集合加载API（数据面同步用）
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("<script>select * from gateway_api where id in " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    List<GatewayApi> selectByIdsIgnoreTenant(@Param("ids") List<Long> ids);

    /**
     * 查询当前租户下所有已使用的分类（去重）
     */
    @Select("select distinct category from gateway_api where tenant_id = #{tenantId} " +
            "and category is not null and category != '' order by category")
    List<String> selectCategories(@Param("tenantId") Long tenantId);
}
