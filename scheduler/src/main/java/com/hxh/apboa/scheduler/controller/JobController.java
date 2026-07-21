package com.hxh.apboa.scheduler.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hxh.apboa.common.UserDetail;
import com.hxh.apboa.common.config.auth.RoleNeed;
import com.hxh.apboa.common.entity.JobInfo;
import com.hxh.apboa.common.entity.JobRecord;
import com.hxh.apboa.common.enums.TenantRole;
import com.hxh.apboa.common.r.R;
import com.hxh.apboa.common.util.TenantUtils;
import com.hxh.apboa.common.util.UserUtils;
import com.hxh.apboa.scheduler.core.cluster.JobMessagePublisher;
import com.hxh.apboa.scheduler.core.client.QuartzClient;
import com.hxh.apboa.scheduler.init.JobInit;
import com.hxh.apboa.scheduler.service.QuartzInfoService;
import com.hxh.apboa.scheduler.service.QuartzRecordService;
import com.hxh.apboa.scheduler.vo.JobRecordVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 描述：web api
 *
 * @author huxuehao
 **/

@RestController
@RequestMapping("/runtime/job")
@RequiredArgsConstructor
public class JobController {
    private final QuartzInfoService quartzInfoService;
    private final JobMessagePublisher jobMessagePublisher;
    private final QuartzRecordService quartzRecordService;
    private final QuartzClient quartzClient;

    @GetMapping("list")
    public R<List<JobInfo>> list() {
        return R.data(quartzInfoService.list());
    }

    @PostMapping("/add")
    @RoleNeed({TenantRole.TENANT_ADMIN, TenantRole.TENANT_EDITOR})
    public R<Boolean> add(@RequestBody JobInfo jobInfo) throws ClassNotFoundException {
        quartzInfoService.addJob(jobInfo);
        // 广播消息通知其他节点
        jobMessagePublisher.publishAdd(jobInfo);
        return R.data(true);
    }

    @PostMapping("/update")
    @RoleNeed({TenantRole.TENANT_ADMIN, TenantRole.TENANT_EDITOR})
    public R<Boolean> update(@RequestBody JobInfo jobInfo) throws ClassNotFoundException {
        quartzInfoService.updateJob(jobInfo);
        // 从DB获取完整对象（含createdBy），确保集群同步消息携带完整信息
        JobInfo dbJob = quartzInfoService.getById(jobInfo.getId());
        jobMessagePublisher.publishUpdate(dbJob);
        return R.data(true);
    }

    @GetMapping("/updateCron")
    @RoleNeed({TenantRole.TENANT_ADMIN, TenantRole.TENANT_EDITOR})
    public R<Boolean> updateCron(@RequestParam("id") String id, @RequestParam("cron") String cron) throws ClassNotFoundException {
        quartzInfoService.updateJobCron(id, cron);
        // 广播消息通知其他节点
        jobMessagePublisher.publishUpdateCron(id, cron);
        return R.data(true);
    }

    @GetMapping("/delete")
    @RoleNeed({TenantRole.TENANT_ADMIN, TenantRole.TENANT_EDITOR})
    public R<Boolean> delete(@RequestParam("id") String id) throws ClassNotFoundException {
        boolean result = quartzInfoService.deleteJob(id);
        // 广播消息通知其他节点
        jobMessagePublisher.publishDelete(id);
        return R.data(result);
    }

    @GetMapping("/start")
    @RoleNeed({TenantRole.TENANT_ADMIN, TenantRole.TENANT_EDITOR})
    public R<Boolean> start(@RequestParam("id") String id) throws ClassNotFoundException {
        quartzInfoService.startJob(id);
        // 广播消息通知其他节点
        jobMessagePublisher.publishStart(id);
        return R.data(true);
    }

    @GetMapping("stop")
    @RoleNeed({TenantRole.TENANT_ADMIN, TenantRole.TENANT_EDITOR})
    public R<Boolean> stop(@RequestParam("id") String id) throws ClassNotFoundException {
        quartzInfoService.stopJob(id);
        // 广播消息通知其他节点
        jobMessagePublisher.publishStop(id);
        return R.data(true);
    }

    /**
     * 根据业务ID查询定时任务
     * GET /job/getByBizId
     *
     * @param bizId 业务ID（即agentId）
     * @return 定时任务信息
     */
    @GetMapping("/getByBizId")
    public R<JobInfo> getByBizId(@RequestParam("bizId") String bizId) {
        return R.data(quartzInfoService.lambdaQuery()
                .eq(JobInfo::getBizId, bizId)
                .eq(JobInfo::getType, "AGENT")
                .one());
    }

    /**
     * 根据任务ID查询详情
     * GET /job/{id}
     */
    @GetMapping("/{id}")
    public R<JobInfo> detail(@PathVariable("id") String id) {
        return R.data(quartzInfoService.getById(id));
    }

    /**
     * 根据业务ID删除定时任务（用于解绑）
     * GET /job/deleteByBizId
     *
     * @param bizId 业务ID（即agentId）
     * @return 是否删除成功
     */
    @GetMapping("/deleteByBizId")
    @RoleNeed({TenantRole.TENANT_ADMIN, TenantRole.TENANT_EDITOR})
    public R<Boolean> deleteByBizId(@RequestParam("bizId") String bizId) throws ClassNotFoundException {
        JobInfo jobInfo = quartzInfoService.lambdaQuery()
                .eq(JobInfo::getBizId, bizId)
                .eq(JobInfo::getType, "AGENT")
                .one();
        if (jobInfo != null) {
            String jobId = String.valueOf(jobInfo.getId());
            // 如果任务正在运行，先停止
            if (jobInfo.getEnabled()) {
                quartzInfoService.stopJob(jobId);
            }
            boolean result = quartzInfoService.deleteJob(jobId);
            // 广播消息通知其他节点
            jobMessagePublisher.publishDelete(jobId);
            return R.data(result);
        }
        return R.data(true);
    }

    /**
     * 手动触发一次执行
     * GET /job/trigger
     */
    @GetMapping("/trigger")
    @RoleNeed({TenantRole.TENANT_ADMIN, TenantRole.TENANT_EDITOR, TenantRole.TENANT_OWNER})
    public R<Boolean> trigger(@RequestParam("id") String id) throws ClassNotFoundException {
        JobInfo jobInfo = quartzInfoService.getById(id);
        if (jobInfo == null) {
            throw new RuntimeException("任务实例不存在");
        }
        quartzClient.createOnce(JobInit.buildConfig(jobInfo), true);
        return R.data(true);
    }

    /**
     * 查询任务运行记录
     * GET /job/records
     */
    @GetMapping("/records")
    public R<List<JobRecordVO>> records(@RequestParam("jobId") Long jobId) {
        List<JobRecord> records = quartzRecordService.lambdaQuery()
                .eq(JobRecord::getJobId, jobId)
                .orderByDesc(JobRecord::getCreateTime)
                .list();
        List<JobRecordVO> result = records.stream()
                .map(r -> JobRecordVO.builder()
                        .jobId(r.getJobId())
                        .recordId(r.getRecordId())
                        .createTime(r.getCreateTime())
                        .build())
                .collect(Collectors.toList());
        return R.data(result);
    }

    /**
     * 分页查询自动化任务
     * GET /job/page
     *
     * @param page     页码
     * @param size     每页数量
     * @param type     任务类型（可选）
     * @param keyword  关键字搜索（可选）
     * @return 分页结果
     */
    @GetMapping("/page")
    public R<IPage<JobInfo>> page(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "50") int size,
            @RequestParam(name = "type", required = false) String type,
            @RequestParam(name = "keyword", required = false) String keyword) {
        UserDetail userDetail = UserUtils.getUserDetail();
        Long currentTenantId = TenantUtils.getCurrentTenantId();
        IPage<JobInfo> result = quartzInfoService.pageJobs(
                page, size, type, keyword,
                currentTenantId, userDetail.getId(), TenantRole.valueOf(userDetail.getTenantRole()));
        return R.data(result);
    }

    /**
     * 启用/禁用切换
     * GET /job/toggle
     *
     * @param id 任务ID
     * @return 操作结果
     */
    @GetMapping("/toggle")
    @RoleNeed({TenantRole.TENANT_ADMIN, TenantRole.TENANT_EDITOR, TenantRole.TENANT_OWNER})
    public R<Boolean> toggle(@RequestParam("id") String id) throws ClassNotFoundException {
        JobInfo jobInfo = quartzInfoService.getById(id);
        if (jobInfo == null) {
            throw new RuntimeException("任务实例不存在");
        }
        boolean newEnabled = !jobInfo.getEnabled();
        jobInfo.setEnabled(newEnabled);
        quartzInfoService.updateStatus(jobInfo);
        if (newEnabled) {
            quartzInfoService.startJob(id);
        } else {
            quartzInfoService.stopJob(id);
        }
        // 广播消息通知其他节点
        if (newEnabled) {
            jobMessagePublisher.publishStart(id);
        } else {
            jobMessagePublisher.publishStop(id);
        }
        return R.data(true);
    }
}
