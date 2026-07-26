package com.hxh.apboa.gateway.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.hxh.apboa.common.mp.support.PageParams;
import com.hxh.apboa.gateway.entity.GatewayClient;
import com.hxh.apboa.gateway.vo.GatewayClientVO;

import java.util.List;

/**
 * 描述：网关客户端服务
 *
 * @author huxuehao
 **/
public interface GatewayClientService extends IService<GatewayClient> {

    /**
     * 分页查询客户端（附带授权API数量）
     */
    IPage<GatewayClientVO> pageVO(GatewayClient query, PageParams pageParams);

    /**
     * 客户端详情（附带授权API集合）
     */
    GatewayClientVO detail(Long id);

    /**
     * 新建客户端（自动生成Token密钥）
     */
    boolean saveClient(GatewayClientVO vo);

    /**
     * 更新客户端及其API授权
     */
    boolean updateClient(GatewayClientVO vo);

    /**
     * 重新生成Token密钥
     *
     * @return 新密钥
     */
    String regenerateSecret(Long id);

    /**
     * 客户端上下线
     *
     * @param id 客户端ID
     * @param v  1上线、0下线
     */
    boolean updateOnline(Long id, Integer v);

    /**
     * 批量删除客户端（级联删除API授权）
     */
    boolean deleteClients(List<Long> ids);
}
