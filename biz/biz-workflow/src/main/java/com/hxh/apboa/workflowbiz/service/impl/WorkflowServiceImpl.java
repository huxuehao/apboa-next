package com.hxh.apboa.workflowbiz.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hxh.apboa.common.consts.TableConst;
import com.hxh.apboa.common.entity.AgentDefinition;
import com.hxh.apboa.common.entity.Workflow;
import com.hxh.apboa.common.entity.WorkflowRun;
import com.hxh.apboa.common.entity.WorkflowVersion;
import com.hxh.apboa.common.enums.workflow.WorkflowStatus;
import com.hxh.apboa.common.util.RedisUtils;
import com.hxh.apboa.common.util.TenantUtils;
import com.hxh.apboa.common.util.UserUtils;
import com.hxh.apboa.workflow.run.cache.RunWorkflowCache;
import com.hxh.apboa.workflowbiz.core.WorkflowDefinitionCompiler;
import com.hxh.apboa.workflowbiz.mapper.WorkflowMapper;
import com.hxh.apboa.workflowbiz.mapper.WorkflowRunMapper;
import com.hxh.apboa.workflowbiz.mapper.WorkflowVersionMapper;
import com.hxh.apboa.workflowbiz.service.AgentWorkflowService;
import com.hxh.apboa.workflowbiz.service.WorkflowResourceBindingService;
import com.hxh.apboa.workflowbiz.service.WorkflowService;
import com.hxh.apboa.workflowbiz.service.WorkflowValidator;
import com.hxh.apboa.workflowbiz.vo.WorkflowDetailVO;
import com.hxh.apboa.workflowbiz.vo.WorkflowValidationResult;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WorkflowServiceImpl extends ServiceImpl<WorkflowMapper, Workflow> implements WorkflowService {
    private final WorkflowVersionMapper workflowVersionMapper;
    private final WorkflowRunMapper workflowRunMapper;
    private final WorkflowValidator workflowValidator;
    private final WorkflowResourceBindingService resourceBindingService;
    private final WorkflowDefinitionCompiler compiler;
    private final AgentWorkflowService agentWorkflowService;
    private final RedisUtils redisUtils;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public WorkflowDetailVO detail(Long id) {
        Workflow workflow = getById(id);
        WorkflowDetailVO detail = new WorkflowDetailVO();
        detail.setWorkflow(workflow);
        detail.setResources(workflow == null ? null : resourceBindingService.getRefs(String.valueOf(id)));
        return detail;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean saveWorkflow(Workflow workflow) {
        if (workflow.getStatus() == null) {
            workflow.setStatus(WorkflowStatus.DRAFT);
        }
        if (workflow.getVersion() == null) {
            workflow.setVersion("0");
        }
        if (workflow.getLocked() == null) {
            workflow.setLocked(0);
        }
        boolean saved = save(workflow);
        if (saved && workflow.getConfig() != null) {
            resourceBindingService.sync(String.valueOf(workflow.getId()), workflow.getConfig());
        }
        return saved;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateWorkflow(Workflow workflow) {
        Workflow existing = getById(workflow.getId());
        if (existing == null) {
            throw new RuntimeException("workflow not found");
        }
        if (Integer.valueOf(1).equals(existing.getLocked()) && !isLockOwner(existing)) {
            throw new RuntimeException("workflow is locked");
        }
        boolean updated = updateById(workflow);
        Object config = workflow.getConfig() != null ? workflow.getConfig() : existing.getConfig();
        resourceBindingService.sync(String.valueOf(workflow.getId()), config);
        RunWorkflowCache.remove(String.valueOf(workflow.getId()));
        return updated;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteWorkflow(Integer force, List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return true;
        }
        if (!Integer.valueOf(1).equals(force)) {
            Long runCount = workflowRunMapper.selectCount(new LambdaQueryWrapper<WorkflowRun>()
                    .in(WorkflowRun::getWorkflowId, ids.stream().map(String::valueOf).toList()));
            if (runCount != null && runCount > 0) {
                throw new RuntimeException("workflow has run history, choose force delete");
            }
            List<Long> agentIds = agentWorkflowService.getAgentIds(ids);
            if (!agentIds.isEmpty()) {
                List<Object> agentNames = usedWithAgent(ids);
                throw new RuntimeException("workflow is used by agents: " + agentNames + ", please unbind first");
            }
        }
        ids.forEach(id -> {
            resourceBindingService.removeWorkflow(String.valueOf(id));
            workflowVersionMapper.delete(new LambdaQueryWrapper<WorkflowVersion>().eq(WorkflowVersion::getWorkflowId, String.valueOf(id)));
            RunWorkflowCache.remove(String.valueOf(id));
        });
        // force 删除跳过绑定检查，须级联清 agent_workflows，否则留悬空关联
        agentWorkflowService.deleteByWorkflowIds(ids);
        return removeByIds(ids);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Workflow copyWorkflow(Long id) {
        Workflow source = getById(id);
        if (source == null) {
            throw new RuntimeException("workflow not found");
        }
        Workflow copy = new Workflow();
        copy.setName(source.getName() + " Copy");
        copy.setRemark(source.getRemark());
        copy.setRouteId(null);
        copy.setStatus(WorkflowStatus.DRAFT);
        copy.setVersion("0");
        copy.setConfig(source.getConfig());
        copy.setLocked(0);
        saveWorkflow(copy);
        return copy;
    }

    @Override
    public boolean lockWorkflow(Long id, Integer locked) {
        Workflow workflow = getById(id);
        if (workflow == null) {
            throw new RuntimeException("workflow not found");
        }
        Long currentUserId = UserUtils.getId();
        // 检查业务锁状态：若已被锁定，则锁定失败
        // 分布式锁，防止并发重复锁定
        String lockKey = "workflow:lock:" + id;
        String lockValue = UUID.randomUUID().toString();
        boolean acquired = redisUtils.tryLock(lockKey, lockValue, 10, TimeUnit.SECONDS);
        if (!acquired) {
            return false;
        }
        try {
            Workflow latest = getById(id);
            if (latest == null) {
                throw new RuntimeException("workflow not found");
            }
            if (Integer.valueOf(1).equals(locked)) {
                if (Integer.valueOf(1).equals(latest.getLocked())) {
                    return isLockOwner(latest, currentUserId);
                }
                return lambdaUpdate()
                        .set(Workflow::getLocked, 1)
                        .set(Workflow::getUpdatedBy, currentUserId)
                        .set(Workflow::getUpdatedAt, LocalDateTime.now())
                        .eq(Workflow::getId, id)
                        .and(wrapper -> wrapper.ne(Workflow::getLocked, 1).or().isNull(Workflow::getLocked))
                        .update();
            }
            if (!Integer.valueOf(1).equals(latest.getLocked())) {
                return true;
            }
            if (!isLockOwner(latest, currentUserId)) {
                return false;
            }
            return lambdaUpdate()
                    .set(Workflow::getLocked, 0)
                    .set(Workflow::getUpdatedBy, currentUserId)
                    .set(Workflow::getUpdatedAt, LocalDateTime.now())
                    .eq(Workflow::getId, id)
                    .eq(Workflow::getUpdatedBy, currentUserId)
                    .update();
        } finally {
            redisUtils.unlock(lockKey, lockValue);
        }
    }

    private boolean isLockOwner(Workflow workflow) {
        return isLockOwner(workflow, UserUtils.getId());
    }

    private boolean isLockOwner(Workflow workflow, Long currentUserId) {
        return workflow != null
                && currentUserId != null
                && workflow.getUpdatedBy() != null
                && currentUserId.equals(workflow.getUpdatedBy());
    }

    @Override
    public WorkflowValidationResult validateWorkflow(Long id) {
        Workflow workflow = getById(id);
        if (workflow == null) {
            throw new RuntimeException("workflow not found");
        }
        return workflowValidator.validate(workflow.getConfig());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkflowVersion publish(Long id, String remark) {
        Workflow workflow = getById(id);
        if (workflow == null) {
            throw new RuntimeException("workflow not found");
        }
        WorkflowValidationResult validation = workflowValidator.validate(workflow.getConfig());
        if (!validation.isValid()) {
            throw new RuntimeException("workflow validation failed: " + validation.getErrors());
        }
        String nextVersion = nextVersion(id);
        WorkflowVersion version = new WorkflowVersion();
        version.setName(workflow.getName());
        version.setWorkflowId(String.valueOf(id));
        version.setRouteId(workflow.getRouteId());
        version.setRemark(remark);
        version.setConfig(workflow.getConfig());
        version.setVersion(nextVersion);
        workflowVersionMapper.insert(version);

        workflow.setStatus(WorkflowStatus.PUBLISHED);
        workflow.setVersion(nextVersion);
        updateById(workflow);
        resourceBindingService.sync(String.valueOf(id), workflow.getConfig());
        RunWorkflowCache.set(compiler.compile(String.valueOf(id), workflow.getConfig()));
        return version;
    }

    @Override
    public List<WorkflowVersion> versions(Long id) {
        return workflowVersionMapper.selectList(new LambdaQueryWrapper<WorkflowVersion>()
                .eq(WorkflowVersion::getWorkflowId, String.valueOf(id))
                .orderByDesc(WorkflowVersion::getCreatedAt)
                .orderByDesc(WorkflowVersion::getId));
    }

    @Override
    public WorkflowVersion latestPublishedVersion(Long workflowId) {
        WorkflowVersion version = workflowVersionMapper.selectOne(new LambdaQueryWrapper<WorkflowVersion>()
                .eq(WorkflowVersion::getWorkflowId, String.valueOf(workflowId))
                .orderByDesc(WorkflowVersion::getId)
                .last("limit 1"));
        if (version == null) {
            throw new RuntimeException("workflow has no published version");
        }
        return version;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Workflow rollback(Long id, String version) {
        WorkflowVersion snapshot = workflowVersionMapper.selectOne(new LambdaQueryWrapper<WorkflowVersion>()
                .eq(WorkflowVersion::getWorkflowId, String.valueOf(id))
                .eq(WorkflowVersion::getVersion, version)
                .last("limit 1"));
        if (snapshot == null) {
            throw new RuntimeException("workflow version not found");
        }
        Workflow workflow = getById(id);
        workflow.setConfig(snapshot.getConfig());
        workflow.setStatus(WorkflowStatus.DRAFT);
        workflow.setVersion(version);
        updateById(workflow);
        resourceBindingService.sync(String.valueOf(id), snapshot.getConfig());
        RunWorkflowCache.remove(String.valueOf(id));
        return workflow;
    }

    @Override
    public List<Object> usedWithAgent(List<Long> ids) {
        List<Object> names = new ArrayList<>();
        getAgentDefinitions(agentWorkflowService.getAgentIds(ids)).forEach(agentDefinition ->
                names.add(agentDefinition.getName()));
        return names;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteVersion(Long id, String version) {
        Workflow workflow = getById(id);
        if (workflow == null) {
            throw new RuntimeException("workflow not found");
        }
        if (WorkflowStatus.PUBLISHED.equals(workflow.getStatus()) && version.equals(workflow.getVersion())) {
            throw new RuntimeException("current published version cannot be deleted");
        }
        WorkflowVersion snapshot = workflowVersionMapper.selectOne(new LambdaQueryWrapper<WorkflowVersion>()
                .eq(WorkflowVersion::getWorkflowId, String.valueOf(id))
                .eq(WorkflowVersion::getVersion, version)
                .last("limit 1"));
        if (snapshot == null) {
            throw new RuntimeException("workflow version not found");
        }
        return workflowVersionMapper.deleteById(snapshot.getId()) > 0;
    }

    private String nextVersion(Long id) {
        Long count = workflowVersionMapper.selectCount(new LambdaQueryWrapper<WorkflowVersion>()
                .eq(WorkflowVersion::getWorkflowId, String.valueOf(id)));
        return String.valueOf((count == null ? 0 : count) + 1);
    }

    private List<AgentDefinition> getAgentDefinitions(List<Long> agentIds) {
        if (agentIds == null || agentIds.isEmpty()) {
            return new ArrayList<>();
        }

        String subSql = agentIds.stream().map(String::valueOf).collect(Collectors.joining(","));

        String sql = String.format("SELECT * FROM %s WHERE id IN (%s)", TableConst.AGENT, subSql);
        if (TenantUtils.getCurrentTenantId() != null) {
            sql += " AND tenant_id = " + TenantUtils.getCurrentTenantId();
        }
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            AgentDefinition agent = new AgentDefinition();
            agent.setId(rs.getLong("id"));
            agent.setName(rs.getString("name"));
            agent.setDescription(rs.getString("description"));
            return agent;
        });
    }
}
