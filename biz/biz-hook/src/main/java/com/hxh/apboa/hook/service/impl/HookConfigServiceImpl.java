package com.hxh.apboa.hook.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.hxh.apboa.common.cluster.core.MessagePublisher;
import com.hxh.apboa.common.consts.RedisChannelTopic;
import com.hxh.apboa.common.consts.TableConst;
import com.hxh.apboa.common.entity.AgentDefinition;
import com.hxh.apboa.common.entity.AgentHook;
import com.hxh.apboa.common.entity.HookConfig;
import com.hxh.apboa.common.enums.HookType;
import com.hxh.apboa.common.enums.ScopeType;
import com.hxh.apboa.common.util.TenantUtils;
import com.hxh.apboa.common.wrapper.HookConfigWrapper;
import com.hxh.apboa.hook.mapper.HookConfigMapper;
import com.hxh.apboa.hook.service.AgentHookService;
import com.hxh.apboa.hook.service.HookConfigService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Hook配置Service实现
 *
 * @author huxuehao
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HookConfigServiceImpl extends ServiceImpl<HookConfigMapper, HookConfig> implements HookConfigService {
    private final JdbcTemplate jdbcTemplate;
    private final AgentHookService agentHookService;
    private final MessagePublisher messagePublisher;

    @Override
    public void SyncConfigToDatabase(List<HookConfigWrapper> configWrappers) {
        // 1. 获取所有租户（供 GLOBAL 作用域使用）
        List<TenantRecord> allTenants = getAllTenants();
        List<Long> allTenantIds = allTenants.stream().map(t -> t.id).toList();
        Map<String, Long> tenantCodeToId = new HashMap<>();
        for (TenantRecord t : allTenants) {
            if (t.code != null) {
                tenantCodeToId.put(t.code, t.id);
            }
        }

        // 2. 清理已从代码中移除的内置Hook（跨所有租户）
        List<String> currentClassPaths = configWrappers.stream()
                .map(HookConfigWrapper::getClassPath).toList();
        if (currentClassPaths.isEmpty()) {
            jdbcTemplate.update("DELETE FROM " + TableConst.HOOK
                    + " WHERE hook_type = 'BUILTIN' AND class_path IS NOT NULL");
            cleanDanglingHookRefs();
            return;
        }
        List<String> existingClassPaths = jdbcTemplate.queryForList(
                "SELECT DISTINCT class_path FROM " + TableConst.HOOK
                        + " WHERE hook_type = 'BUILTIN' AND class_path IS NOT NULL",
                String.class);
        Set<String> currentSet = new HashSet<>(currentClassPaths);
        for (String existingCp : existingClassPaths) {
            if (!currentSet.contains(existingCp)) {
                jdbcTemplate.update("DELETE FROM " + TableConst.HOOK
                                + " WHERE class_path = ? AND hook_type = 'BUILTIN'",
                        existingCp);
            }
        }

        // 3. 逐Hook按租户作用域同步
        for (HookConfigWrapper configWrapper : configWrappers) {
            syncSingleHook(configWrapper, allTenantIds, tenantCodeToId);
        }

        // 4. 孤儿关联清理(启动自愈)
        cleanDanglingHookRefs();
    }

    /**
     * 清理指向已不存在 Hook 的悬空关联(agent_hooks)。
     * 同步的 DELETE 只删 hook_config 自身,不级联关联表;启动同步末尾统一自愈,
     * 手动删除路径(deleteByIds)本就有级联。同步期无租户上下文,沿用 jdbcTemplate。
     */
    private void cleanDanglingHookRefs() {
        jdbcTemplate.update("DELETE FROM " + TableConst.AGENT_HOOKS
                + " WHERE hook_config_id NOT IN (SELECT id FROM " + TableConst.HOOK + ")");
    }

    /**
     * 同步单个内置Hook：按作用域解析目标租户，diff 后 INSERT/UPDATE/DELETE
     */
    private void syncSingleHook(HookConfigWrapper configWrapper, List<Long> allTenantIds,
                                Map<String, Long> tenantCodeToId) {
        ScopeType scopeType = configWrapper.getScopeType() != null
                ? configWrapper.getScopeType() : ScopeType.GLOBAL;
        List<Long> targetTenantIds = resolveTargetTenants(scopeType, configWrapper.getTenantCodes(),
                allTenantIds, tenantCodeToId);

        String classPath = configWrapper.getClassPath();

        // 查询该类路径在所有租户中的现有记录
        List<ExistingRecord> existingRecords = jdbcTemplate.query(
                "SELECT id, tenant_id FROM " + TableConst.HOOK
                        + " WHERE class_path = ? AND hook_type = 'BUILTIN'",
                (rs, rowNum) -> new ExistingRecord(rs.getLong("id"), rs.getLong("tenant_id")),
                classPath);

        Map<Long, Long> existingTenantToId = new HashMap<>();
        for (ExistingRecord r : existingRecords) {
            existingTenantToId.put(r.tenantId, r.id);
        }

        Set<Long> existingTenants = existingTenantToId.keySet();
        Set<Long> targetSet = new HashSet<>(targetTenantIds);

        // 需要新增的租户
        Set<Long> toAdd = new HashSet<>(targetSet);
        toAdd.removeAll(existingTenants);

        // 需要更新的租户（交集）
        Set<Long> toUpdate = new HashSet<>(targetSet);
        toUpdate.retainAll(existingTenants);

        // 需要删除的租户（作用域回收）
        Set<Long> toRemove = new HashSet<>(existingTenants);
        toRemove.removeAll(targetSet);

        // 执行变更
        for (Long tenantId : toAdd) {
            insertHookConfig(configWrapper, tenantId);
        }
        for (Long tenantId : toUpdate) {
            updateHookConfig(configWrapper, existingTenantToId.get(tenantId));
        }
        for (Long tenantId : toRemove) {
            jdbcTemplate.update("DELETE FROM " + TableConst.HOOK + " WHERE id = ?",
                    existingTenantToId.get(tenantId));
        }
    }

    /**
     * 解析目标租户列表
     */
    private List<Long> resolveTargetTenants(ScopeType scopeType, List<String> tenantCodes,
                                            List<Long> allTenantIds, Map<String, Long> tenantCodeToId) {
        if (scopeType == ScopeType.GLOBAL) {
            return allTenantIds;
        }
        List<Long> targetIds = new ArrayList<>();
        if (tenantCodes != null) {
            for (String code : tenantCodes) {
                Long id = tenantCodeToId.get(code);
                if (id != null) {
                    targetIds.add(id);
                }
            }
        }
        return targetIds;
    }

    /**
     * 插入内置Hook到指定租户
     */
    private void insertHookConfig(HookConfigWrapper configWrapper, Long tenantId) {
        long id = IdWorker.getId();
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(
                "INSERT INTO " + TableConst.HOOK
                        + " (id, tenant_id, name, hook_type, description, class_path, enabled, priority,"
                        + " scope_type, created_at, updated_at)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id,
                tenantId,
                configWrapper.getName(),
                HookType.BUILTIN.name(),
                configWrapper.getDescription(),
                configWrapper.getClassPath(),
                true,
                1,
                configWrapper.getScopeType().name(),
                now,
                now
        );
    }

    /**
     * 更新内置Hook（刷新元数据，不改变租户归属）。
     * 注意：不覆盖 name —— 内置 Hook 允许用户改名（见 updateName），启动同步只维护
     * description/scope_type，避免把用户改的 name 冲掉。name 仅在首次 insert 时用代码值；
     * 改 name 不影响生效（HooksFactory 按 class_path 反查）。
     */
    private void updateHookConfig(HookConfigWrapper configWrapper, Long recordId) {
        jdbcTemplate.update(
                "UPDATE " + TableConst.HOOK
                        + " SET description = ?, scope_type = ?, updated_at = ?"
                        + " WHERE id = ?",
                configWrapper.getDescription(),
                configWrapper.getScopeType().name(),
                LocalDateTime.now(),
                recordId
        );
    }

    /**
     * 查询所有启用的租户
     */
    private List<TenantRecord> getAllTenants() {
        return jdbcTemplate.query(
                "SELECT id, code FROM " + TableConst.TENANT + " WHERE enabled = 1",
                (rs, rowNum) -> new TenantRecord(rs.getLong("id"), rs.getString("code")));
    }

    /** 租户简要记录 */
    private record TenantRecord(Long id, String code) {}

    /** 已有记录（id, tenant_id） */
    private record ExistingRecord(Long id, Long tenantId) {}

    @Override
    public List<Object> usedWithAgent(List<Long> ids) {
        List<Object> names = new ArrayList<>();
        getAgentDefinitions(agentHookService.getAgentIds(ids)).forEach(agentDefinition -> {
            names.add(agentDefinition.getName());
        });

        return names;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteByIds(List<Long> ids) {
        // 删除前先获取关联的智能体ID，以便后续触发重新注册
        List<Long> agentIds = agentHookService.getAgentIds(ids);
        removeByIds(ids);
        boolean result = agentHookService.remove(new LambdaQueryWrapper<AgentHook>().in(AgentHook::getHookConfigId, ids));
        publishAgentReregister(agentIds);
        return result;
    }

    @Override
    public boolean doUpdate(HookConfig entity) {
        boolean result = updateById(entity);
        publishAgentReregister(agentHookService.getAgentIds(List.of(entity.getId())));
        return result;
    }

    @Override
    public boolean updateName(Long id, String name) {
        // 仅改展示 name（允许内置 Hook）；name 不影响生效（HooksFactory 按 class_path 反查），
        // 故无需触发关联智能体重新注册。启动同步不会覆盖此 name（见 updateHookConfig）。
        return lambdaUpdate()
                .set(HookConfig::getName, name)
                .eq(HookConfig::getId, id)
                .update();
    }

    private void publishAgentReregister(List<Long> agentIds) {
        agentIds.forEach(agentId ->
                messagePublisher.publishAfterCommit(RedisChannelTopic.AGENT_REREGISTER_CHANNEL, String.valueOf(agentId)));
    }

    private List<AgentDefinition> getAgentDefinitions(List<Long> agentIds) {
        if (agentIds == null || agentIds.isEmpty()) {
            return new ArrayList<>();
        }

        String subSql = agentIds.stream().map(String::valueOf).collect(Collectors.joining(","));

        String sql = String.format("SELECT * FROM %s WHERE id IN (%s)", TableConst.AGENT, subSql);
        // 添加租户过滤（JdbcTemplate 绕过 MyBatis-Plus 拦截器）
        if (TenantUtils.getCurrentTenantId() != null) {
            sql += " AND tenant_id = " + TenantUtils.getCurrentTenantId();
        }
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            AgentDefinition agent = new AgentDefinition();
            // 手动映射字段
            agent.setId(rs.getLong("id"));
            agent.setName(rs.getString("name"));
            agent.setDescription(rs.getString("description"));
            return agent;
        });
    }
}
