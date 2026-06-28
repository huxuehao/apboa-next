package com.hxh.apboa.workflowbiz.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hxh.apboa.common.entity.Workflow;
import com.hxh.apboa.common.entity.WorkflowCache;
import com.hxh.apboa.common.entity.WorkflowDatasource;
import com.hxh.apboa.common.entity.WorkflowMq;
import com.hxh.apboa.common.entity.WorkflowPlugin;
import com.hxh.apboa.workflowbiz.mapper.WorkflowCacheMapper;
import com.hxh.apboa.workflowbiz.mapper.WorkflowDatasourceMapper;
import com.hxh.apboa.workflowbiz.mapper.WorkflowMapper;
import com.hxh.apboa.workflowbiz.mapper.WorkflowMqMapper;
import com.hxh.apboa.workflowbiz.mapper.WorkflowPluginMapper;
import com.hxh.apboa.workflowbiz.service.WorkflowResourceBindingService;
import com.hxh.apboa.workflowbiz.vo.WorkflowResourceRefs;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class WorkflowResourceBindingServiceImpl implements WorkflowResourceBindingService {
    private final ObjectMapper objectMapper;
    private final WorkflowMapper workflowMapper;
    private final WorkflowCacheMapper workflowCacheMapper;
    private final WorkflowDatasourceMapper workflowDatasourceMapper;
    private final WorkflowMqMapper workflowMqMapper;
    private final WorkflowPluginMapper workflowPluginMapper;

    @Override
    public WorkflowResourceRefs scan(Object definition) {
        WorkflowResourceRefs refs = new WorkflowResourceRefs();
        collect(objectMapper.valueToTree(definition), refs);
        return refs;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void sync(String workflowId, Object definition) {
        removeWorkflow(workflowId);
        WorkflowResourceRefs refs = scan(definition);
        refs.getCacheIds().forEach(id -> workflowCacheMapper.insert(new WorkflowCache(workflowId, id)));
        refs.getDatasourceIds().forEach(id -> workflowDatasourceMapper.insert(new WorkflowDatasource(workflowId, id)));
        refs.getMqIds().forEach(id -> workflowMqMapper.insert(new WorkflowMq(workflowId, id)));
        refs.getPluginIds().forEach(id -> workflowPluginMapper.insert(new WorkflowPlugin(workflowId, id)));
    }

    @Override
    public WorkflowResourceRefs getRefs(String workflowId) {
        WorkflowResourceRefs refs = new WorkflowResourceRefs();
        workflowCacheMapper.selectList(new LambdaQueryWrapper<WorkflowCache>().eq(WorkflowCache::getWorkflowId, workflowId))
                .forEach(x -> refs.getCacheIds().add(x.getCacheId()));
        workflowDatasourceMapper.selectList(new LambdaQueryWrapper<WorkflowDatasource>().eq(WorkflowDatasource::getWorkflowId, workflowId))
                .forEach(x -> refs.getDatasourceIds().add(x.getDatasourceId()));
        workflowMqMapper.selectList(new LambdaQueryWrapper<WorkflowMq>().eq(WorkflowMq::getWorkflowId, workflowId))
                .forEach(x -> refs.getMqIds().add(x.getMqId()));
        workflowPluginMapper.selectList(new LambdaQueryWrapper<WorkflowPlugin>().eq(WorkflowPlugin::getWorkflowId, workflowId))
                .forEach(x -> refs.getPluginIds().add(x.getPluginId()));
        return refs;
    }

    @Override
    public List<String> usedWorkflowNames(String resourceType, List<String> resourceIds) {
        if (resourceIds == null || resourceIds.isEmpty()) {
            return List.of();
        }
        List<String> workflowIds = switch (resourceType) {
            case "cache" -> workflowCacheMapper.selectList(new LambdaQueryWrapper<WorkflowCache>().in(WorkflowCache::getCacheId, resourceIds))
                    .stream().map(WorkflowCache::getWorkflowId).toList();
            case "datasource" -> workflowDatasourceMapper.selectList(new LambdaQueryWrapper<WorkflowDatasource>().in(WorkflowDatasource::getDatasourceId, resourceIds))
                    .stream().map(WorkflowDatasource::getWorkflowId).toList();
            case "mq" -> workflowMqMapper.selectList(new LambdaQueryWrapper<WorkflowMq>().in(WorkflowMq::getMqId, resourceIds))
                    .stream().map(WorkflowMq::getWorkflowId).toList();
            default -> List.of();
        };
        if (workflowIds.isEmpty()) {
            return List.of();
        }
        return workflowMapper.selectBatchIds(workflowIds).stream()
                .map(Workflow::getName)
                .filter(Objects::nonNull)
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeWorkflow(String workflowId) {
        workflowCacheMapper.delete(new LambdaQueryWrapper<WorkflowCache>().eq(WorkflowCache::getWorkflowId, workflowId));
        workflowDatasourceMapper.delete(new LambdaQueryWrapper<WorkflowDatasource>().eq(WorkflowDatasource::getWorkflowId, workflowId));
        workflowMqMapper.delete(new LambdaQueryWrapper<WorkflowMq>().eq(WorkflowMq::getWorkflowId, workflowId));
        workflowPluginMapper.delete(new LambdaQueryWrapper<WorkflowPlugin>().eq(WorkflowPlugin::getWorkflowId, workflowId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeResourceRefs(String resourceType, List<String> resourceIds) {
        if (resourceIds == null || resourceIds.isEmpty()) {
            return;
        }
        switch (resourceType) {
            case "cache" -> workflowCacheMapper.delete(new LambdaQueryWrapper<WorkflowCache>().in(WorkflowCache::getCacheId, resourceIds));
            case "datasource" -> workflowDatasourceMapper.delete(new LambdaQueryWrapper<WorkflowDatasource>().in(WorkflowDatasource::getDatasourceId, resourceIds));
            case "mq" -> workflowMqMapper.delete(new LambdaQueryWrapper<WorkflowMq>().in(WorkflowMq::getMqId, resourceIds));
            default -> {
            }
        }
    }

    private void collect(JsonNode node, WorkflowResourceRefs refs) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isObject()) {
            addIfPresent(node, "cacheId", refs.getCacheIds());
            addIfPresent(node, "datasourceId", refs.getDatasourceIds());
            addIfPresent(node, "mqId", refs.getMqIds());
            addIfPresent(node, "pluginId", refs.getPluginIds());
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                collect(fields.next().getValue(), refs);
            }
        } else if (node.isArray()) {
            List<JsonNode> children = new ArrayList<>();
            node.forEach(children::add);
            children.forEach(child -> collect(child, refs));
        }
    }

    private void addIfPresent(JsonNode node, String field, java.util.Set<String> target) {
        JsonNode value = node.get(field);
        if (value != null && value.isValueNode() && !value.asText().isBlank()) {
            target.add(value.asText());
        }
    }
}
