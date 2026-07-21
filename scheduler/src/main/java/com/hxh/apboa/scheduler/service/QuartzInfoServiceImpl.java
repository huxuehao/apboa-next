package com.hxh.apboa.scheduler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hxh.apboa.agent.service.AgentDefinitionService;
import com.hxh.apboa.common.entity.AgentDefinition;
import com.hxh.apboa.common.entity.JobInfo;
import com.hxh.apboa.common.enums.TenantRole;
import com.hxh.apboa.scheduler.core.client.QuartzClient;
import com.hxh.apboa.scheduler.init.JobInit;
import com.hxh.apboa.scheduler.mapper.JobInfoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 描述：定时任务管理服务实现
 *
 * @author huxuehao
 **/
@Service
@RequiredArgsConstructor
public class QuartzInfoServiceImpl extends ServiceImpl<JobInfoMapper, JobInfo> implements QuartzInfoService {

    private final QuartzClient quartzClient;
    private final AgentDefinitionService agentDefinitionService;

    @Override
    public void updateStatus(JobInfo jobStatus) {
        lambdaUpdate()
                .eq(JobInfo::getId, jobStatus.getId())
                .set(JobInfo::getEnabled, jobStatus.getEnabled())
                .update();
    }

    @Override
    public void addJob(JobInfo jobInfo) {
        checkTarget(jobInfo);
        try {
            save(jobInfo);
            if (jobInfo.getEnabled()) {
                quartzClient.create(JobInit.buildConfig(jobInfo));
            }
        } catch (Exception e) {
            removeById(jobInfo.getId());
            throw new RuntimeException(e);
        }
    }

    @Override
    public void updateJob(JobInfo jobInfo) throws ClassNotFoundException {
        JobInfo job = getById(jobInfo.getId());
        if (job == null) {
            throw new RuntimeException("任务实例不存在");
        }
        checkTarget(job);
        // 用前端传入的字段更新 job，保持 createdBy 等非前端字段不变
        job.setType(jobInfo.getType());
        job.setBizId(jobInfo.getBizId());
        job.setCron(jobInfo.getCron());
        job.setJobClass(jobInfo.getJobClass());
        job.setDataMap(jobInfo.getDataMap());
        job.setEnabled(jobInfo.getEnabled());
        if (job.getEnabled()) {
            quartzClient.remove(JobInit.buildConfig(job));
            quartzClient.create(JobInit.buildConfig(job));
        } else {
            quartzClient.remove(JobInit.buildConfig(job));
        }
        updateById(job);
    }

    @Override
    public void updateJobCron(String id, String cron) throws ClassNotFoundException {
        JobInfo job = getById(id);
        if (job == null) {
            throw new RuntimeException("任务实例不存在");
        }
        checkTarget(job);
        if (cron.equals(job.getCron())) {
            return;
        }
        job.setCron(cron);
        if (job.getEnabled()) {
            quartzClient.remove(JobInit.buildConfig(job));
            quartzClient.create(JobInit.buildConfig(job));
        }
        updateById(job);
    }

    @Override
    public boolean deleteJob(String id) throws ClassNotFoundException {
        JobInfo jobInfo = getById(id);
        if (jobInfo == null) {
            return true;
        }
        if (jobInfo.getEnabled()) {
            quartzClient.remove(JobInit.buildConfig(jobInfo));
        }
        return removeById(id);
    }

    @Override
    public void startJob(String id) throws ClassNotFoundException {
        JobInfo jobInfo = getById(id);
        if (jobInfo == null) {
            throw new RuntimeException("任务实例不存在");
        }
        checkTarget(jobInfo);
        quartzClient.remove(JobInit.buildConfig(jobInfo));
        quartzClient.create(JobInit.buildConfig(jobInfo));
    }

    @Override
    public void stopJob(String id) throws ClassNotFoundException {
        JobInfo jobInfo = getById(id);
        if (jobInfo == null) {
            throw new RuntimeException("任务实例不存在");
        }
        if (!jobInfo.getEnabled()) {
            return;
        }
        quartzClient.remove(JobInit.buildConfig(jobInfo));
    }

    @Override
    public IPage<JobInfo> pageJobs(int page, int size, String type, String keyword,
                                   Long tenantId, Long userId, TenantRole userRole) {
        LambdaQueryWrapper<JobInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(JobInfo::getTenantId, tenantId);

        // 权限过滤：非管理员/拥有者只看自己创建的
        if (userRole == TenantRole.TENANT_EDITOR || userRole == TenantRole.TENANT_VIEWER) {
            wrapper.eq(JobInfo::getCreatedBy, userId);
        }

        // 类型过滤
        if (type != null && !type.isEmpty()) {
            wrapper.eq(JobInfo::getType, type);
        }

        // 关键字搜索（搜索 dataMap 中的目标信息）
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.like(JobInfo::getDataMap, keyword);
        }

        wrapper.orderByDesc(JobInfo::getCreatedAt);
        return page(new Page<>(page, size), wrapper);
    }

    /**
     * 校验目标有效性（仅 AGENT 类型需校验智能体是否已禁用）
     */
    private void checkTarget(JobInfo jobInfo) {
        if (!"AGENT".equals(jobInfo.getType())) {
            return;
        }
        String agentId = jobInfo.getBizId();
        if (agentId == null) {
            return;
        }
        AgentDefinition agentDefinition = agentDefinitionService.getById(agentId);
        if (agentDefinition != null && !agentDefinition.getEnabled()) {
            throw new RuntimeException("智能体无效，不可设置定时");
        }
    }
}
