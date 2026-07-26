package com.hxh.apboa.gateway.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hxh.apboa.common.mp.support.MP;
import com.hxh.apboa.common.mp.support.PageParams;
import com.hxh.apboa.gateway.cluster.GatewaySyncPublisher;
import com.hxh.apboa.gateway.cluster.GatewaySyncType;
import com.hxh.apboa.gateway.entity.GatewayClient;
import com.hxh.apboa.gateway.entity.GatewayClientApi;
import com.hxh.apboa.gateway.mapper.GatewayClientApiMapper;
import com.hxh.apboa.gateway.mapper.GatewayClientMapper;
import com.hxh.apboa.gateway.service.GatewayClientService;
import com.hxh.apboa.gateway.vo.GatewayClientVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 描述：网关客户端服务实现
 *
 * @author huxuehao
 **/
@Service
@RequiredArgsConstructor
public class GatewayClientServiceImpl extends ServiceImpl<GatewayClientMapper, GatewayClient> implements GatewayClientService {
    /** 客户端编号格式：字母数字下划线中划线 */
    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{4,64}$");
    /** 默认Token有效期：2小时 */
    private static final long DEFAULT_TOKEN_TTL = 2 * 60 * 60 * 1000L;

    private final GatewayClientApiMapper clientApiMapper;
    private final GatewaySyncPublisher syncPublisher;

    @Override
    public IPage<GatewayClientVO> pageVO(GatewayClient query, PageParams pageParams) {
        IPage<GatewayClient> page = page(MP.getPage(pageParams), MP.getQueryWrapper(query));
        Page<GatewayClientVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(enrich(page.getRecords()));
        return result;
    }

    @Override
    public GatewayClientVO detail(Long id) {
        GatewayClient client = getById(id);
        if (client == null) {
            throw new RuntimeException("网关客户端不存在");
        }
        GatewayClientVO vo = new GatewayClientVO();
        BeanUtils.copyProperties(client, vo);
        List<Long> apiIds = clientApiMapper
                .selectList(new LambdaQueryWrapper<GatewayClientApi>().eq(GatewayClientApi::getClientId, id))
                .stream().map(GatewayClientApi::getApiId).toList();
        vo.setApiIds(apiIds);
        vo.setApiCount(apiIds.size());
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean saveClient(GatewayClientVO vo) {
        validateClient(vo);
        GatewayClient client = new GatewayClient();
        BeanUtils.copyProperties(vo, client);
        client.setId(null);
        client.setTokenSecret(generateSecret());
        client.setOnline(1);
        if (client.getTokenTtl() == null || client.getTokenTtl() <= 0) {
            client.setTokenTtl(DEFAULT_TOKEN_TTL);
        }
        boolean saved;
        try {
            saved = save(client);
        } catch (DuplicateKeyException e) {
            throw new RuntimeException("客户端编号 " + vo.getCode() + " 已存在");
        }
        if (saved) {
            saveAuths(client, vo.getApiIds());
            syncPublisher.publish(GatewaySyncType.CLIENT_REFRESH, List.of(client.getId()));
        }
        return saved;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateClient(GatewayClientVO vo) {
        GatewayClient exists = getById(vo.getId());
        if (exists == null) {
            throw new RuntimeException("网关客户端不存在");
        }
        validateClient(vo);
        if (!exists.getCode().equals(vo.getCode())) {
            throw new RuntimeException("客户端编号不允许修改");
        }
        boolean updated = lambdaUpdate()
                .eq(GatewayClient::getId, vo.getId())
                .set(GatewayClient::getName, vo.getName())
                .set(GatewayClient::getConsumer, vo.getConsumer())
                .set(GatewayClient::getExpireAt, vo.getExpireAt())
                .set(GatewayClient::getTokenTtl, vo.getTokenTtl() == null || vo.getTokenTtl() <= 0
                        ? DEFAULT_TOKEN_TTL : vo.getTokenTtl())
                .update();
        if (updated) {
            // 重建API授权关系
            clientApiMapper.delete(new LambdaQueryWrapper<GatewayClientApi>().eq(GatewayClientApi::getClientId, vo.getId()));
            saveAuths(exists, vo.getApiIds());
            syncPublisher.publish(GatewaySyncType.CLIENT_REFRESH, List.of(vo.getId()));
        }
        return updated;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String regenerateSecret(Long id) {
        GatewayClient client = getById(id);
        if (client == null) {
            throw new RuntimeException("网关客户端不存在");
        }
        String secret = generateSecret();
        lambdaUpdate().eq(GatewayClient::getId, id).set(GatewayClient::getTokenSecret, secret).update();
        syncPublisher.publish(GatewaySyncType.CLIENT_REFRESH, List.of(id));
        return secret;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateOnline(Long id, Integer v) {
        GatewayClient client = getById(id);
        if (client == null) {
            throw new RuntimeException("网关客户端不存在");
        }
        boolean updated = lambdaUpdate()
                .eq(GatewayClient::getId, id)
                .set(GatewayClient::getOnline, Integer.valueOf(1).equals(v) ? 1 : 0)
                .update();
        if (updated) {
            syncPublisher.publish(GatewaySyncType.CLIENT_REFRESH, List.of(id));
        }
        return updated;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteClients(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return true;
        }
        List<String> codes = lambdaQuery().in(GatewayClient::getId, ids).list()
                .stream().map(GatewayClient::getCode).toList();
        boolean removed = removeByIds(ids);
        if (removed) {
            clientApiMapper.delete(new LambdaQueryWrapper<GatewayClientApi>().in(GatewayClientApi::getClientId, ids));
            syncPublisher.publish(GatewaySyncType.CLIENT_REMOVE, ids, codes);
        }
        return removed;
    }

    /**
     * 附加授权API数量
     */
    private List<GatewayClientVO> enrich(List<GatewayClient> clients) {
        if (clients == null || clients.isEmpty()) {
            return List.of();
        }
        List<Long> clientIds = clients.stream().map(GatewayClient::getId).toList();
        Map<Long, Long> countMap = clientApiMapper
                .selectList(new LambdaQueryWrapper<GatewayClientApi>().in(GatewayClientApi::getClientId, clientIds))
                .stream().collect(Collectors.groupingBy(GatewayClientApi::getClientId, Collectors.counting()));
        return clients.stream().map(client -> {
            GatewayClientVO vo = new GatewayClientVO();
            BeanUtils.copyProperties(client, vo);
            // 列表不暴露签名密钥，仅详情可见
            vo.setTokenSecret(null);
            vo.setApiCount(countMap.getOrDefault(client.getId(), 0L).intValue());
            return vo;
        }).toList();
    }

    /**
     * 保存API授权关系
     */
    private void saveAuths(GatewayClient client, List<Long> apiIds) {
        if (apiIds == null || apiIds.isEmpty()) {
            return;
        }
        for (Long apiId : apiIds.stream().distinct().toList()) {
            GatewayClientApi auth = new GatewayClientApi();
            auth.setClientId(client.getId());
            auth.setApiId(apiId);
            clientApiMapper.insert(auth);
        }
    }

    /**
     * 客户端基础字段校验
     */
    private void validateClient(GatewayClientVO vo) {
        if (vo.getCode() == null || !CODE_PATTERN.matcher(vo.getCode()).matches()) {
            throw new RuntimeException("客户端编号必须为4-64位字母、数字、下划线或中划线");
        }
        if (vo.getName() == null || vo.getName().isBlank()) {
            throw new RuntimeException("客户端名称不能为空");
        }
    }

    /**
     * 生成Token签名密钥
     */
    private String generateSecret() {
        return UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
    }
}
