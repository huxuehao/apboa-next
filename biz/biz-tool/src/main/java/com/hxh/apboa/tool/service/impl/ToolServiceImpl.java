package com.hxh.apboa.tool.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.hxh.apboa.common.cluster.core.MessagePublisher;
import com.hxh.apboa.common.consts.RedisChannelTopic;
import com.hxh.apboa.common.consts.TableConst;
import com.hxh.apboa.common.entity.AgentDefinition;
import com.hxh.apboa.common.entity.AgentTool;
import com.hxh.apboa.common.entity.SkillTool;
import com.hxh.apboa.common.entity.ToolConfig;
import com.hxh.apboa.common.enums.ScopeType;
import com.hxh.apboa.common.enums.ToolType;
import com.hxh.apboa.common.util.JsonUtils;
import com.hxh.apboa.common.util.TenantUtils;
import com.hxh.apboa.common.wrapper.ToolInfoWrapper;
import com.hxh.apboa.tool.mapper.ISkillToolMapper;
import com.hxh.apboa.tool.mapper.ToolMapper;
import com.hxh.apboa.tool.service.AgentToolService;
import com.hxh.apboa.tool.service.ToolService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 工具Service实现
 *
 * @author huxuehao
 */
@Service
@RequiredArgsConstructor
public class ToolServiceImpl extends ServiceImpl<ToolMapper, ToolConfig> implements ToolService {
    private final JdbcTemplate jdbcTemplate;
    private final ISkillToolMapper iSkillToolMapper;
    private final AgentToolService agentToolService;
    private final MessagePublisher messagePublisher;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteTools(List<Long> ids) {
        // 删除前先获取关联的智能体ID，以便后续触发重新注册
        List<Long> agentIds = agentToolService.getAgentIds(ids);
        listByIds(ids).forEach(toolConfig -> {
            if (toolConfig.getToolType() != ToolType.BUILTIN) {
                removeById(toolConfig.getId());
            }
        });

        agentToolService.remove(new LambdaQueryWrapper<AgentTool>().in(AgentTool::getToolId, ids));
        iSkillToolMapper.delete(new LambdaQueryWrapper<SkillTool>().in(SkillTool::getToolId, ids));
        publishAgentReregister(agentIds);

        return true;
    }

    @Override
    public void SyncConfigToDatabase(List<ToolInfoWrapper> toolInfos) {
        // 1. 获取所有租户（供 GLOBAL 作用域使用）
        List<TenantRecord> allTenants = getAllTenants();
        List<Long> allTenantIds = allTenants.stream().map(t -> t.id).toList();
        Map<String, Long> tenantCodeToId = new HashMap<>();
        for (TenantRecord t : allTenants) {
            if (t.code != null) {
                tenantCodeToId.put(t.code, t.id);
            }
        }

        // 2. 清理已从代码中移除的内置工具（跨所有租户）
        List<String> currentClassPaths = toolInfos.stream()
                .map(ToolInfoWrapper::getClassPath).toList();
        if (currentClassPaths.isEmpty()) {
            jdbcTemplate.update("DELETE FROM " + TableConst.TOOL + " WHERE tool_type = 'BUILTIN'");
            cleanDanglingToolRefs();
            return;
        }
        List<String> existingClassPaths = jdbcTemplate.queryForList(
                "SELECT DISTINCT class_path FROM " + TableConst.TOOL + " WHERE tool_type = 'BUILTIN'",
                String.class);
        Set<String> currentSet = new HashSet<>(currentClassPaths);
        for (String existingCp : existingClassPaths) {
            if (!currentSet.contains(existingCp)) {
                jdbcTemplate.update("DELETE FROM " + TableConst.TOOL + " WHERE class_path = ? AND tool_type = 'BUILTIN'",
                        existingCp);
            }
        }

        // 3. 逐工具按租户作用域同步
        for (ToolInfoWrapper toolInfo : toolInfos) {
            syncSingleTool(toolInfo, allTenantIds, tenantCodeToId);
        }

        // 4. 孤儿关联清理(启动自愈)
        cleanDanglingToolRefs();
    }

    /**
     * 清理指向已不存在工具的悬空关联(agent_tools / skill_tools)。
     * 上面的同步只删 tool_config 自身(类路径移除、作用域回收两类 DELETE),历史上因此遗留过
     * agent_tools 悬空引用——前端架构页按悬空 id 逐个查详情报"工具不存在"。
     * 每次启动同步末尾统一自愈,顺带清掉历史存量;手动删除路径(deleteTools)本就有级联,不受影响。
     * 同步期无租户上下文,沿用 jdbcTemplate 直删;子查询列是主键,NOT IN 无 NULL 陷阱。
     */
    private void cleanDanglingToolRefs() {
        jdbcTemplate.update("DELETE FROM " + TableConst.AGENT_TOOL
                + " WHERE tool_id NOT IN (SELECT id FROM " + TableConst.TOOL + ")");
        jdbcTemplate.update("DELETE FROM " + TableConst.SKILL_TOOL
                + " WHERE tool_id NOT IN (SELECT id FROM " + TableConst.TOOL + ")");
    }

    /**
     * 同步单个内置工具：按作用域解析目标租户，diff 后 INSERT/UPDATE/DELETE
     */
    private void syncSingleTool(ToolInfoWrapper toolInfo, List<Long> allTenantIds,
                                Map<String, Long> tenantCodeToId) {
        ScopeType scopeType = toolInfo.getScopeType() != null ? toolInfo.getScopeType() : ScopeType.GLOBAL;
        List<Long> targetTenantIds = resolveTargetTenants(scopeType, toolInfo.getTenantCodes(),
                allTenantIds, tenantCodeToId);

        String classPath = toolInfo.getClassPath();

        // 查询该类路径在所有租户中的现有记录
        List<ExistingRecord> existingRecords = jdbcTemplate.query(
                "SELECT id, tenant_id FROM " + TableConst.TOOL
                        + " WHERE class_path = ? AND tool_type = 'BUILTIN'",
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
            insertToolConfig(toolInfo, tenantId);
        }
        for (Long tenantId : toUpdate) {
            updateToolConfig(toolInfo, existingTenantToId.get(tenantId));
        }
        for (Long tenantId : toRemove) {
            jdbcTemplate.update("DELETE FROM " + TableConst.TOOL + " WHERE id = ?",
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
        // TENANT 作用域：按编码匹配
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
     * 插入内置工具到指定租户
     */
    private void insertToolConfig(ToolInfoWrapper toolInfo, Long tenantId) {
        long id = IdWorker.getId();
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(
                "INSERT INTO " + TableConst.TOOL
                        + " (id, tenant_id, name, tool_id, description, category, tool_type,"
                        + " need_confirm, input_schema, class_path, enabled, scope_type, created_at, updated_at)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id,
                tenantId,
                toolInfo.getName(),
                toolInfo.getName(),
                toolInfo.getDescription(),
                "内置",
                ToolType.BUILTIN.name(),
                false,
                JsonUtils.toJsonStr(toolInfo.getParams()),
                toolInfo.getClassPath(),
                true,
                toolInfo.getScopeType().name(),
                now,
                now
        );
    }

    /**
     * 更新内置工具（刷新元数据，不改变租户归属）。
     * 注意：不覆盖 name —— 内置工具允许用户改名（UI 通用编辑，见 doUpdate 的 BUILTIN 分支），启动同步
     * 只维护 tool_id/description/input_schema/scope_type，避免把用户改的 name 冲掉。name 仅在首次
     * insert 时用代码值；tool_id 是调用/匹配用的稳定名（= @Tool 原始 name），随代码升级刷新。
     * 改 name 不影响生效（ToolkitFactory 按 class_path 反查、need_confirm 按 tool_id 匹配）。
     */
    private void updateToolConfig(ToolInfoWrapper toolInfo, Long recordId) {
        jdbcTemplate.update(
                "UPDATE " + TableConst.TOOL
                        + " SET tool_id = ?, description = ?, input_schema = ?, scope_type = ?, updated_at = ?"
                        + " WHERE id = ?",
                toolInfo.getName(),
                toolInfo.getDescription(),
                JsonUtils.toJsonStr(toolInfo.getParams()),
                toolInfo.getScopeType().name(),
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
        getAgentDefinitions(agentToolService.getAgentIds(ids)).forEach(agentDefinition -> {
            names.add(agentDefinition.getName());
        });

        return names;
    }

    @Override
    public List<String> listCategories() {
        return this.lambdaQuery()
                .select(ToolConfig::getCategory)
                .isNotNull(ToolConfig::getCategory)
                .groupBy(ToolConfig::getCategory)
                .list()
                .stream()
                .map(ToolConfig::getCategory)
                .filter(category -> category != null && !category.isEmpty())
                .collect(Collectors.toList());
    }

    @Override
    public Boolean doUpdate(ToolConfig toolConfig) {
        boolean result;
        if (toolConfig.getToolType() != ToolType.BUILTIN) {
            result = updateById(toolConfig);
        } else {
            result = lambdaUpdate()
                    .eq(ToolConfig::getId, toolConfig.getId())
                    .set(ToolConfig::getName, toolConfig.getName())
                    .set(ToolConfig::getCategory, toolConfig.getCategory())
                    .set(ToolConfig::getDescription, toolConfig.getDescription())
                    .set(ToolConfig::getNeedConfirm, toolConfig.getNeedConfirm())
                    .set(ToolConfig::getVersion, toolConfig.getVersion())
                    .update();
        }
        publishAgentReregister(agentToolService.getAgentIds(List.of(toolConfig.getId())));
        return result;
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
