package com.hxh.apboa.skill.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hxh.apboa.common.cluster.core.MessagePublisher;
import com.hxh.apboa.common.consts.RedisChannelTopic;
import com.hxh.apboa.common.consts.TableConst;
import com.hxh.apboa.common.entity.AgentDefinition;
import com.hxh.apboa.common.entity.AgentSkillPackage;
import com.hxh.apboa.common.entity.SkillPackage;
import com.hxh.apboa.common.enums.ScopeType;
import com.hxh.apboa.common.enums.SkillFileType;
import com.hxh.apboa.common.enums.SkillType;
import com.hxh.apboa.common.util.TenantUtils;
import com.hxh.apboa.common.vo.SkillPackageVO;
import com.hxh.apboa.common.wrapper.SkillConfigWrapper;
import com.hxh.apboa.skill.mapper.SkillPackageMapper;
import com.hxh.apboa.skill.service.AgentSkillPackageService;
import com.hxh.apboa.skill.service.SkillPackageService;
import com.hxh.apboa.skill.service.SkillToolService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 技能包Service实现
 *
 * @author huxuehao
 */
@Service
@RequiredArgsConstructor
public class SkillPackageServiceImpl extends ServiceImpl<SkillPackageMapper, SkillPackage> implements SkillPackageService {
    private final JdbcTemplate jdbcTemplate;
    private final AgentSkillPackageService agentSkillPackageService;
    private final SkillToolService skillToolService;
    private final MessagePublisher messagePublisher;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void syncBuiltinSkills(List<SkillConfigWrapper> configWrappers) {
        // 1. 获取所有租户（供 GLOBAL 作用域使用）
        List<TenantRecord> allTenants = getAllTenantsForSync();
        List<Long> allTenantIds = allTenants.stream().map(t -> t.id).toList();
        Map<String, Long> tenantCodeToId = new HashMap<>();
        for (TenantRecord t : allTenants) {
            if (t.code != null) {
                tenantCodeToId.put(t.code, t.id);
            }
        }

        // 2. 清理已从代码中移除的内置技能包（跨所有租户）
        List<String> currentClassPaths = configWrappers.stream()
                .map(SkillConfigWrapper::getClassPath).toList();
        if (currentClassPaths.isEmpty()) {
            jdbcTemplate.update("DELETE FROM " + TableConst.SKILL
                    + " WHERE skill_type = 'BUILTIN' AND class_path IS NOT NULL");
            cleanDanglingSkillRefs();
            return;
        }
        List<String> existingClassPaths = jdbcTemplate.queryForList(
                "SELECT DISTINCT class_path FROM " + TableConst.SKILL
                        + " WHERE skill_type = 'BUILTIN' AND class_path IS NOT NULL",
                String.class);
        Set<String> currentSet = new HashSet<>(currentClassPaths);
        for (String existingCp : existingClassPaths) {
            if (!currentSet.contains(existingCp)) {
                jdbcTemplate.update("DELETE FROM " + TableConst.SKILL
                                + " WHERE class_path = ? AND skill_type = 'BUILTIN'",
                        existingCp);
            }
        }

        // 3. 逐技能按租户作用域同步
        for (SkillConfigWrapper configWrapper : configWrappers) {
            syncSingleSkill(configWrapper, allTenantIds, tenantCodeToId);
        }

        // 4. 孤儿关联清理(启动自愈)
        cleanDanglingSkillRefs();
    }

    /**
     * 清理指向已不存在技能包的悬空关联(agent_skill_packages / skill_tools / skill_file)。
     * 同步的 DELETE 只删 skill_package 自身,不级联;启动同步末尾统一自愈。
     * 手动删除路径(deleteByIds)已级联 agent 关联与 skill_tools,skill_file 表记录同样在此兜底。
     * 同步期无租户上下文,沿用 jdbcTemplate。
     */
    private void cleanDanglingSkillRefs() {
        jdbcTemplate.update("DELETE FROM " + TableConst.AGENT_SKILL
                + " WHERE skill_package_id NOT IN (SELECT id FROM " + TableConst.SKILL + ")");
        jdbcTemplate.update("DELETE FROM " + TableConst.SKILL_TOOL
                + " WHERE skill_id NOT IN (SELECT id FROM " + TableConst.SKILL + ")");
        jdbcTemplate.update("DELETE FROM " + TableConst.SKILL_FILE
                + " WHERE skill_id NOT IN (SELECT id FROM " + TableConst.SKILL + ")");
    }

    /**
     * 同步单个内置技能：按作用域解析目标租户，diff 后 INSERT/UPDATE/DELETE
     */
    private void syncSingleSkill(SkillConfigWrapper configWrapper, List<Long> allTenantIds,
                                 Map<String, Long> tenantCodeToId) {
        ScopeType scopeType = configWrapper.getScopeType() != null
                ? configWrapper.getScopeType() : ScopeType.GLOBAL;
        List<Long> targetTenantIds = resolveTargetTenants(scopeType, configWrapper.getTenantCodes(),
                allTenantIds, tenantCodeToId);

        String classPath = configWrapper.getClassPath();

        List<ExistingRecord> existingRecords = jdbcTemplate.query(
                "SELECT id, tenant_id FROM " + TableConst.SKILL
                        + " WHERE class_path = ? AND skill_type = 'BUILTIN'",
                (rs, rowNum) -> new ExistingRecord(rs.getLong("id"), rs.getLong("tenant_id")),
                classPath);

        Map<Long, Long> existingTenantToId = new HashMap<>();
        for (ExistingRecord r : existingRecords) {
            existingTenantToId.put(r.tenantId, r.id);
        }

        Set<Long> existingTenants = existingTenantToId.keySet();
        Set<Long> targetSet = new HashSet<>(targetTenantIds);

        Set<Long> toAdd = new HashSet<>(targetSet);
        toAdd.removeAll(existingTenants);
        Set<Long> toUpdate = new HashSet<>(targetSet);
        toUpdate.retainAll(existingTenants);
        Set<Long> toRemove = new HashSet<>(existingTenants);
        toRemove.removeAll(targetSet);

        for (Long tenantId : toAdd) {
            insertBuiltinSkill(configWrapper, tenantId);
        }
        for (Long tenantId : toUpdate) {
            updateBuiltinSkill(configWrapper, existingTenantToId.get(tenantId));
        }
        for (Long tenantId : toRemove) {
            jdbcTemplate.update("DELETE FROM " + TableConst.SKILL + " WHERE id = ?",
                    existingTenantToId.get(tenantId));
        }
    }

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

    private void insertBuiltinSkill(SkillConfigWrapper configWrapper, Long tenantId) {
        long id = IdWorker.getId();
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(
                "INSERT INTO " + TableConst.SKILL
                        + " (id, tenant_id, name, description, skill_type, class_path, scope_type, enabled,"
                        + " created_at, updated_at)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id,
                tenantId,
                configWrapper.getName(),
                configWrapper.getDescription(),
                SkillType.BUILTIN.name(),
                configWrapper.getClassPath(),
                configWrapper.getScopeType().name(),
                true,
                now,
                now
        );
        upsertSkillMdFile(id, configWrapper.getSkillContent());
    }

    private void updateBuiltinSkill(SkillConfigWrapper configWrapper, Long recordId) {
        jdbcTemplate.update(
                "UPDATE " + TableConst.SKILL
                        + " SET name = ?, description = ?, scope_type = ?, updated_at = ?"
                        + " WHERE id = ?",
                configWrapper.getName(),
                configWrapper.getDescription(),
                configWrapper.getScopeType().name(),
                LocalDateTime.now(),
                recordId
        );
        upsertSkillMdFile(recordId, configWrapper.getSkillContent());
    }

    /**
     * 幂等写入内置技能包的 SKILL.md 到 skill_file（供 console 侧 InitLoadSkillScript 落盘、前端只读查看）。
     * 不存在则插入，存在则更新内容。file_type 存枚举 name（"SKILL_MD"），与 MyBatis 读取一致。
     */
    private void upsertSkillMdFile(long skillId, String content) {
        String md = content != null ? content : "";
        List<Long> existing = jdbcTemplate.queryForList(
                "SELECT id FROM " + TableConst.SKILL_FILE
                        + " WHERE skill_id = ? AND file_path = ?",
                Long.class, skillId, "SKILL.md");
        if (existing.isEmpty()) {
            Long tenantId = jdbcTemplate.queryForObject(
                    "SELECT tenant_id FROM " + TableConst.SKILL + " WHERE id = ?",
                    Long.class, skillId);
            jdbcTemplate.update(
                    "INSERT INTO " + TableConst.SKILL_FILE
                            + " (id, skill_id, file_type, file_name, file_path, content, sort, tenant_id)"
                            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    IdWorker.getId(), skillId, SkillFileType.SKILL_MD.name(),
                    "SKILL.md", "SKILL.md", md, 0, tenantId);
        } else {
            jdbcTemplate.update(
                    "UPDATE " + TableConst.SKILL_FILE + " SET content = ? WHERE id = ?",
                    md, existing.get(0));
        }
    }

    private List<TenantRecord> getAllTenantsForSync() {
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
        getAgentDefinitions(agentSkillPackageService.getAgentIds(ids)).forEach(agentDefinition -> {
            names.add(agentDefinition.getName());
        });

        return names;
    }

    @Override
    public List<String> listCategories() {
        return this.lambdaQuery()
                .select(SkillPackage::getCategory)
                .isNotNull(SkillPackage::getCategory)
                .groupBy(SkillPackage::getCategory)
                .list()
                .stream()
                .map(SkillPackage::getCategory)
                .filter(category -> category != null && !category.isEmpty())
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteByIds(List<Long> ids) {
        // 删除前查询技能包信息，用于发布文件删除事件
        List<SkillPackage> skills = listByIds(ids);
        // 删除前先获取关联的智能体ID，以便后续触发重新注册
        List<Long> agentIds = agentSkillPackageService.getAgentIds(ids);
        removeByIds(ids);
        // 删除技能包与智能体的关联
        agentSkillPackageService.remove(new LambdaQueryWrapper<AgentSkillPackage>().in(AgentSkillPackage::getSkillPackageId, ids));
        // 删除技能包与工具的关联
        skillToolService.deleteSkillTool(ids);
        // 删除技能包文件表记录(此前只发文件系统清理事件、漏了表记录,skill_file 会悬空;
        // ids 是 Long 列表,拼接无注入风险,风格同 ModelConfigServiceImpl.getAgentDefinitions)
        jdbcTemplate.update("DELETE FROM " + TableConst.SKILL_FILE + " WHERE skill_id IN ("
                + ids.stream().map(String::valueOf).collect(Collectors.joining(",")) + ")");
        publishAgentReregister(agentIds);
        // 发布文件删除同步事件
        skills.forEach(skill -> publishSkillFileDelete(skill.getName(), skill.getTenantId()));
        return true;
    }

    @Override
    public boolean doUpdate(SkillPackage entity) {
        boolean result = updateById(entity);
        publishAgentReregister(agentSkillPackageService.getAgentIds(List.of(entity.getId())));
        return result;
    }

    @Override
    public SkillPackageVO getDetail(Long id) {
        SkillPackage entity = getById(id);
        if (entity == null) {
            return null;
        }
        SkillPackageVO vo = new SkillPackageVO();
        vo.setId(entity.getId());
        vo.setName(entity.getName());
        vo.setAlias(entity.getAlias());
        vo.setDescription(entity.getDescription());
        vo.setCategory(entity.getCategory());
        vo.setSkillType(entity.getSkillType());
        vo.setClassPath(entity.getClassPath());
        vo.setScopeType(entity.getScopeType());
        vo.setEnabled(entity.getEnabled());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        vo.setCreatedBy(entity.getCreatedBy());
        vo.setUpdatedBy(entity.getUpdatedBy());
        // 查询关联的工具ID列表
        vo.setTools(skillToolService.getToolIds(id));
        return vo;
    }

    @Override
    public boolean updateAlias(Long id, String alias) {
        return lambdaUpdate()
                .set(SkillPackage::getAlias, alias)
                .eq(SkillPackage::getId, id)
                .update();
    }

    private void publishAgentReregister(List<Long> agentIds) {
        agentIds.forEach(agentId ->
                messagePublisher.publishAfterCommit(RedisChannelTopic.AGENT_REREGISTER_CHANNEL, String.valueOf(agentId)));
    }

    /**
     * 发布技能文件删除事件（action=delete）
     */
    private void publishSkillFileDelete(String skillName, Long tenantId) {
        String tenantCode = queryTenantCode(tenantId);
        if (tenantCode == null) {
            return;
        }
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("action", "delete");
        payload.put("skillName", skillName);
        payload.put("tenantCode", tenantCode);
        try {
            String json = objectMapper.writeValueAsString(payload);
            messagePublisher.publishAfterCommit(RedisChannelTopic.SKILL_FILE_SYNC_CHANNEL, json);
        } catch (Exception e) {
            // JSON 序列化失败（理论上不会发生）
        }
    }

    /**
     * 根据租户ID查询租户编码
     */
    private String queryTenantCode(Long tenantId) {
        if (tenantId == null) {
            return null;
        }
        List<String> codes = jdbcTemplate.queryForList(
                "SELECT code FROM tenant WHERE id = ?", String.class, tenantId);
        return codes.isEmpty() ? null : codes.get(0);
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
