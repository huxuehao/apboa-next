package com.hxh.apboa.model.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hxh.apboa.common.cluster.core.MessagePublisher;
import com.hxh.apboa.common.consts.RedisChannelTopic;
import com.hxh.apboa.common.consts.TableConst;
import com.hxh.apboa.common.entity.ModelConfig;
import com.hxh.apboa.common.entity.ModelProvider;
import com.hxh.apboa.common.util.TenantUtils;
import com.hxh.apboa.model.mapper.ModelConfigMapper;
import com.hxh.apboa.model.mapper.ModelProviderMapper;
import com.hxh.apboa.model.service.ModelProviderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 模型提供商Service实现
 *
 * @author huxuehao
 **/
@Service
@RequiredArgsConstructor
public class ModelProviderServiceImpl extends ServiceImpl<ModelProviderMapper, ModelProvider> implements ModelProviderService {
    private final ModelConfigMapper modelConfigMapper;
    private final JdbcTemplate jdbcTemplate;
    private final MessagePublisher messagePublisher;

    @Override
    public List<Object> usedWithModel(List<Long> ids) {
        List<Object> names = new ArrayList<>();

        LambdaQueryWrapper<ModelConfig> qw = new QueryWrapper<ModelConfig>().lambda().in(ModelConfig::getProviderId, ids);
        modelConfigMapper.selectList(qw).forEach(modelConfig -> {
            names.add(modelConfig.getName());
        });

        return names;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteByIds(List<Long> ids) {
        // 删除前先获取关联的智能体ID（两级：供应商→模型配置→智能体）
        List<Long> agentIds = getAgentIdsByProviderIds(ids);
        // 级联删除其下模型配置并置空 agent 直连列:此前只删供应商,
        // 留下 provider_id 悬空的 model_config(详情页查供应商报"不存在")。
        // 不能注入 ModelConfigService(它依赖本 service,注入成环),用 mapper + jdbc 复刻其置空逻辑
        List<Long> modelIds = modelConfigMapper.selectList(
                        new QueryWrapper<ModelConfig>().lambda().in(ModelConfig::getProviderId, ids))
                .stream()
                .map(ModelConfig::getId)
                .toList();
        if (!modelIds.isEmpty()) {
            String idList = modelIds.stream().map(String::valueOf).collect(Collectors.joining(","));
            jdbcTemplate.update("UPDATE " + TableConst.AGENT
                    + " SET model_config_id = NULL WHERE model_config_id IN (" + idList + ")");
            jdbcTemplate.update("UPDATE " + TableConst.AGENT
                    + " SET asr_model_config_id = NULL WHERE asr_model_config_id IN (" + idList + ")");
            jdbcTemplate.update("UPDATE " + TableConst.AGENT
                    + " SET tts_model_config_id = NULL WHERE tts_model_config_id IN (" + idList + ")");
            modelConfigMapper.deleteByIds(modelIds);
        }
        boolean result = removeByIds(ids);
        publishAgentReregister(agentIds);
        return result;
    }

    @Override
    public boolean doUpdate(ModelProvider entity) {
        boolean result = updateById(entity);
        List<Long> agentIds = getAgentIdsByProviderIds(List.of(entity.getId()));
        publishAgentReregister(agentIds);
        return result;
    }

    /**
     * 两级查询：供应商ID → 模型配置ID → 智能体ID
     *
     * @param providerIds 供应商ID列表
     * @return 关联的智能体ID列表
     */
    private List<Long> getAgentIdsByProviderIds(List<Long> providerIds) {
        if (providerIds == null || providerIds.isEmpty()) {
            return new ArrayList<>();
        }
        // 第一级：根据供应商ID查询模型配置ID
        LambdaQueryWrapper<ModelConfig> qw = new QueryWrapper<ModelConfig>().lambda().in(ModelConfig::getProviderId, providerIds);
        List<Long> modelConfigIds = modelConfigMapper.selectList(qw)
                .stream()
                .map(ModelConfig::getId)
                .toList();

        if (modelConfigIds.isEmpty()) {
            return new ArrayList<>();
        }

        // 第二级：根据模型配置ID查询智能体ID
        String subSql = modelConfigIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        String sql = String.format("SELECT id FROM %s WHERE model_config_id IN (%s)", TableConst.AGENT, subSql);
        // 添加租户过滤（JdbcTemplate 绕过 MyBatis-Plus 拦截器）
        if (TenantUtils.getCurrentTenantId() != null) {
            sql += " AND tenant_id = " + TenantUtils.getCurrentTenantId();
        }
        return jdbcTemplate.queryForList(sql, Long.class);
    }

    private void publishAgentReregister(List<Long> agentIds) {
        agentIds.forEach(agentId ->
                messagePublisher.publishAfterCommit(RedisChannelTopic.AGENT_REREGISTER_CHANNEL, String.valueOf(agentId)));
    }
}
