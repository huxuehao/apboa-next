package com.hxh.apboa.scheduler.scheduler;

import com.hxh.apboa.account.service.TenantService;
import com.hxh.apboa.common.UserDetail;
import com.hxh.apboa.common.entity.Tenant;
import com.hxh.apboa.common.vo.AccountVO;
import com.hxh.apboa.common.wrapper.AgentJobWrapper;
import com.hxh.apboa.node.base.request.ParamItem;
import com.hxh.apboa.scheduler.consts.JobConst;
import com.hxh.apboa.scheduler.core.job.QuartzJob;
import com.hxh.apboa.workflowbiz.dto.WorkflowRunRequest;
import com.hxh.apboa.workflowbiz.service.WorkflowRunService;
import com.hxh.apboa.workflowbiz.vo.WorkflowRunResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.quartz.JobExecutionContext;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 工作流调度器
 * 负责执行定时工作流任务
 *
 * @author huxuehao
 */
@Slf4j
public class WorkflowScheduler extends QuartzJob {

    @Override
    public Object doJob(JobExecutionContext context) {
        // 1. 获取任务参数
        AgentJobWrapper wrapper = getDataMap(JobConst.DATA_MAP_KEY, AgentJobWrapper.class);
        AccountVO userInfo = getDataMap(JobConst.USER_INFO_KEY, AccountVO.class);
        Long tenantId = getDataMap(JobConst.TENANT_ID_KEY, Long.class);

        // 2. 参数校验
        if (!validateParameters(wrapper, userInfo, tenantId)) {
            return false;
        }

        try {
            // 3. 构建用户详情
            UserDetail userDetail = buildUserDetail(userInfo, tenantId);
            if (userDetail == null) {
                log.error("Failed to build UserDetail for tenant: {}", tenantId);
                return false;
            }

            // 4. 执行工作流
            WorkflowRunService workflowRunService = getBean(WorkflowRunService.class);
            WorkflowRunRequest request = buildRunRequest(wrapper.getParams());

            log.info("Executing workflow, workflowId: {}, tenantId: {}, userId: {}",
                    wrapper.getBizId(), tenantId, userInfo.getId());

            WorkflowRunResult runResult = workflowRunService.run(
                    Long.parseLong(wrapper.getBizId()),
                    request,
                    userDetail
            );

            // 5. 记录关联关系
            if (runResult != null && runResult.getRun() != null) {
                setRecordRelation(runResult.getRun().getId());
                log.info("Workflow executed successfully, runId: {}", runResult.getRun().getId());
            } else {
                log.warn("Workflow execution completed but run result is null or incomplete");
            }

            // 6. 返回执行结果
            return true;

        } catch (NumberFormatException e) {
            log.error("Invalid workflow ID format: {}", wrapper.getBizId(), e);
            return false;
        } catch (Exception e) {
            log.error("Failed to execute workflow, workflowId: {}, error: {}",
                    wrapper.getBizId(), e.getMessage(), e);
            return false;
        }
    }

    /**
     * 校验任务参数
     */
    private boolean validateParameters(AgentJobWrapper wrapper, AccountVO userInfo, Long tenantId) {
        if (wrapper == null) {
            log.warn("AgentJobWrapper is null");
            return false;
        }

        if (StringUtils.isBlank(wrapper.getBizId())) {
            log.warn("Workflow ID is blank");
            return false;
        }

        if (userInfo == null) {
            log.warn("UserInfo is null");
            return false;
        }

        if (tenantId == null) {
            log.warn("Tenant ID is null");
            return false;
        }

        return true;
    }

    /**
     * 构建用户详情
     */
    private UserDetail buildUserDetail(AccountVO userInfo, Long tenantId) {
        try {
            TenantService tenantService = getBean(TenantService.class);
            Tenant tenant = tenantService.getById(tenantId);

            if (tenant == null) {
                log.error("Tenant not found for id: {}", tenantId);
                return null;
            }

            return UserDetail.builder()
                    .id(userInfo.getId())
                    .name(Optional.ofNullable(userInfo.getNickname()).orElse(userInfo.getUsername()))
                    .username(userInfo.getUsername())
                    .email(userInfo.getEmail())
                    .tenantId(tenant.getId())
                    .tenantCode(tenant.getCode())
                    .tenantRole(userInfo.getTenantRole())
                    .build();

        } catch (Exception e) {
            log.error("Failed to build UserDetail, tenantId: {}", tenantId, e);
            return null;
        }
    }

    /**
     * 构建工作流运行请求
     */
    private WorkflowRunRequest buildRunRequest(Map<String, Object> params) {
        WorkflowRunRequest request = new WorkflowRunRequest();

        if (MapUtils.isEmpty(params)) {
            request.setParams(Collections.emptyList());
            return request;
        }

        // 使用Stream将Map转换为ParamItem列表
        List<ParamItem> paramItems = params.entrySet().stream()
                .map(entry -> ParamItem.builder()
                        .name(entry.getKey())
                        .value(entry.getValue())
                        .build())
                .toList();

        request.setParams(paramItems);
        return request;
    }
}
