package com.hxh.apboa.gateway.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.hxh.apboa.common.mp.support.PageParams;
import com.hxh.apboa.gateway.entity.GatewayApi;
import com.hxh.apboa.gateway.vo.GatewayApiVO;

import java.util.List;

/**
 * 描述：网关API服务
 *
 * @author huxuehao
 **/
public interface GatewayApiService extends IService<GatewayApi> {

    /**
     * 分页查询API（附带应用与工作流信息）
     */
    IPage<GatewayApiVO> pageVO(GatewayApi query, PageParams pageParams);

    /**
     * API详情（附带应用与工作流信息）
     */
    GatewayApiVO detail(Long id);

    /**
     * 新建API并绑定工作流
     */
    boolean saveApi(GatewayApiVO vo);

    /**
     * 更新API与工作流绑定
     */
    boolean updateApi(GatewayApiVO vo);

    /**
     * 批量删除API（级联删除工作流绑定与客户端授权）
     */
    boolean deleteApis(List<Long> ids);

    /**
     * API上下线
     *
     * @param id API ID
     * @param v  1上线、0下线
     */
    boolean updateOnline(Long id, Integer v);

    /**
     * 查询当前租户下自维护分类列表
     */
    List<String> categories();

    /**
     * 查询当前租户下可供客户端授权的API简要列表
     */
    List<GatewayApiVO> listBrief();
}
