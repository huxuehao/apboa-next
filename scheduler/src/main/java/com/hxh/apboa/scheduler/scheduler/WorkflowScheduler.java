package com.hxh.apboa.scheduler.scheduler;

import com.hxh.apboa.account.service.TenantService;
import com.hxh.apboa.common.UserDetail;
import com.hxh.apboa.common.consts.SysConst;
import com.hxh.apboa.common.entity.Tenant;
import com.hxh.apboa.common.enums.WorkflowRunStatus;
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

        // 2. 参数校验（失败原因写入 RUN_MSG，随 job_log 落库）
        String invalidReason = validateParameters(wrapper, userInfo, tenantId);
        if (invalidReason != null) {
            log.warn("Workflow job parameter invalid: {}", invalidReason);
            putRunMsg("参数校验失败：" + invalidReason);
            return false;
        }

        try {
            // 3. 构建用户详情
            UserDetail userDetail = buildUserDetail(userInfo, tenantId);
            if (userDetail == null) {
                log.error("Failed to build UserDetail for tenant: {}", tenantId);
                putRunMsg("构建执行用户身份失败，tenantId=" + tenantId + "，详见服务日志");
                return false;
            }

            // 4. 执行工作流
            WorkflowRunService workflowRunService = getBean(WorkflowRunService.class);
            WorkflowRunRequest request = buildRunRequest(wrapper.getParams(), wrapper.getVariables());

            log.info("Executing workflow, workflowId: {}, tenantId: {}, userId: {}",
                    wrapper.getBizId(), tenantId, userInfo.getId());

            WorkflowRunResult runResult = workflowRunService.run(
                    Long.parseLong(wrapper.getBizId()),
                    request,
                    userDetail
            );

            // 5. 记录关联关系；run 业务失败（引擎已捕获、状态 FAIL）同样判定本次调度失败
            if (runResult == null || runResult.getRun() == null) {
                log.warn("Workflow execution completed but run result is null or incomplete");
                putRunMsg("工作流执行结果缺失（run 未落库），详见服务日志");
                return false;
            }
            setRecordRelation(runResult.getRun().getId());
            if (runResult.getRun().getStatus() == WorkflowRunStatus.FAIL) {
                String error = runResult.getRun().getError();
                log.warn("Workflow run failed, runId: {}, error: {}", runResult.getRun().getId(), error);
                putRunMsg("工作流执行失败，runId=" + runResult.getRun().getId()
                        + (error != null ? "，原因：" + error : "，原因见运行详情"));
                return false;
            }

            log.info("Workflow executed successfully, runId: {}", runResult.getRun().getId());
            putRunMsg("执行成功，runId=" + runResult.getRun().getId());
            return true;

        } catch (NumberFormatException e) {
            log.error("Invalid workflow ID format: {}", wrapper.getBizId(), e);
            putRunMsg("工作流ID格式非法：" + wrapper.getBizId());
            return false;
        } catch (Exception e) {
            log.error("Failed to execute workflow, workflowId: {}, error: {}",
                    wrapper.getBizId(), e.getMessage(), e);
            putRunMsg("执行异常：" + e.getMessage());
            return false;
        }
    }

    /**
     * 校验任务参数
     * @return null 表示通过；否则为失败原因
     */
    private String validateParameters(AgentJobWrapper wrapper, AccountVO userInfo, Long tenantId) {
        if (wrapper == null) {
            return "任务执行参数（dataMap）为空或格式不兼容";
        }

        if (StringUtils.isBlank(wrapper.getBizId())) {
            return "关联工作流ID为空";
        }

        if (userInfo == null) {
            return "创建人信息缺失";
        }

        if (tenantId == null) {
            return "租户信息缺失";
        }

        return null;
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
     * 构建工作流运行请求：params 转开始节点参数，variables 透传为自定义变量覆盖
     */
    private WorkflowRunRequest buildRunRequest(Map<String, Object> params, Map<String, Object> variables) {
        WorkflowRunRequest request = new WorkflowRunRequest();
        // 成本流水归因：定时触发渠道（经变量上下文下传至智能体节点记账）
        request.setChannel(SysConst.CHANNEL_SCHEDULED);

        if (MapUtils.isNotEmpty(variables)) {
            request.setVariables(variables);
        }

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
