package com.hxh.apboa.workflowbiz.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hxh.apboa.common.entity.Workflow;
import com.hxh.apboa.common.entity.WorkflowRun;
import com.hxh.apboa.common.entity.WorkflowVersion;
import com.hxh.apboa.common.enums.workflow.WorkflowStatus;
import com.hxh.apboa.workflow.run.cache.RunWorkflowCache;
import com.hxh.apboa.workflowbiz.core.WorkflowDefinitionCompiler;
import com.hxh.apboa.workflowbiz.mapper.WorkflowMapper;
import com.hxh.apboa.workflowbiz.mapper.WorkflowRunMapper;
import com.hxh.apboa.workflowbiz.mapper.WorkflowVersionMapper;
import com.hxh.apboa.workflowbiz.service.WorkflowResourceBindingService;
import com.hxh.apboa.workflowbiz.service.WorkflowService;
import com.hxh.apboa.workflowbiz.service.WorkflowValidator;
import com.hxh.apboa.workflowbiz.vo.WorkflowDetailVO;
import com.hxh.apboa.workflowbiz.vo.WorkflowValidationResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class WorkflowServiceImpl extends ServiceImpl<WorkflowMapper, Workflow> implements WorkflowService {
    private final WorkflowVersionMapper workflowVersionMapper;
    private final WorkflowRunMapper workflowRunMapper;
    private final WorkflowValidator workflowValidator;
    private final WorkflowResourceBindingService resourceBindingService;
    private final WorkflowDefinitionCompiler compiler;

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
        if (Integer.valueOf(1).equals(existing.getLocked())) {
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
        }
        ids.forEach(id -> {
            resourceBindingService.removeWorkflow(String.valueOf(id));
            workflowVersionMapper.delete(new LambdaQueryWrapper<WorkflowVersion>().eq(WorkflowVersion::getWorkflowId, String.valueOf(id)));
            RunWorkflowCache.remove(String.valueOf(id));
        });
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
        return lambdaUpdate().set(Workflow::getLocked, locked).eq(Workflow::getId, id).update();
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
                .orderByDesc(WorkflowVersion::getId));
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
        updateById(workflow);
        resourceBindingService.sync(String.valueOf(id), snapshot.getConfig());
        RunWorkflowCache.remove(String.valueOf(id));
        return workflow;
    }

    private String nextVersion(Long id) {
        Long count = workflowVersionMapper.selectCount(new LambdaQueryWrapper<WorkflowVersion>()
                .eq(WorkflowVersion::getWorkflowId, String.valueOf(id)));
        return String.valueOf((count == null ? 0 : count) + 1);
    }
}
