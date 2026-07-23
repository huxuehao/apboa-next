package com.hxh.apboa.scheduler.init;

import com.hxh.apboa.common.consts.TableConst;
import com.hxh.apboa.common.entity.JobInfo;
import com.hxh.apboa.common.entity.Tenant;
import com.hxh.apboa.common.util.JsonUtils;
import com.hxh.apboa.common.vo.AccountVO;
import com.hxh.apboa.common.wrapper.AgentJobWrapper;
import com.hxh.apboa.scheduler.consts.JobConst;
import com.hxh.apboa.scheduler.core.client.QuartzClient;
import com.hxh.apboa.scheduler.core.config.QuartzConfig;
import com.hxh.apboa.scheduler.core.config.QuartzConfigFactory;
import com.hxh.apboa.scheduler.core.job.QuartzJob;
import com.hxh.apboa.common.entity.JobInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 描述：初始化定时任务
 *
 * @author huxuehao
 **/
@Component
@Slf4j
public class JobInit implements SmartInitializingSingleton {

    private static final String LEGACY_AGENT_JOB_CLASS =
            "com.hxh.apboa.job.scheduler.AgentScheduler";
    private static final String AGENT_JOB_CLASS =
            "com.hxh.apboa.scheduler.scheduler.AgentScheduler";

    private final QuartzClient quartzClient;
    private final JdbcTemplate jdbcTemplate;
    private static JdbcTemplate staticJdbcTemplate;

    public JobInit(QuartzClient quartzClient, JdbcTemplate jdbcTemplate) {
        this.quartzClient = quartzClient;
        this.jdbcTemplate = jdbcTemplate;
        JobInit.staticJdbcTemplate = jdbcTemplate;
    }

    public void doJobInit() {
        // 使用 jdbcTemplate 绕过租户拦截器（启动时无租户上下文）
        String sql = "SELECT id, tenant_id, type, biz_id, cron, job_class, data_map, enabled, created_by FROM "+ TableConst.JOB_INFO +" WHERE enabled = true";
        List<JobInfo> list = jdbcTemplate.query(sql, (rs, rowNum) -> {
            JobInfo job = new JobInfo();
            job.setId(rs.getLong("id"));
            job.setTenantId(rs.getLong("tenant_id"));
            job.setType(rs.getString("type"));
            job.setBizId(rs.getString("biz_id"));
            job.setCron(rs.getString("cron"));
            job.setJobClass(rs.getString("job_class"));
            job.setDataMap(rs.getString("data_map"));
            job.setEnabled(rs.getBoolean("enabled"));
            job.setCreatedBy(rs.getLong("created_by"));
            return job;
        });
        for (JobInfo jobInfo : list) {
            try {
                migrateLegacyJobClass(jobInfo);
                quartzClient.create(buildConfig(jobInfo));
            } catch (ClassNotFoundException e) {
                log.error("定时任务初始化失败，执行类不存在: jobId={}, jobClass={}",
                        jobInfo.getId(), jobInfo.getJobClass(), e);
            } catch (RuntimeException e) {
                log.error("定时任务初始化失败: jobId={}, jobClass={}",
                        jobInfo.getId(), jobInfo.getJobClass(), e);
            }
        }
    }

    /**
     * 兼容旧版本写入的 Agent 调度类名，并同步修正存量数据。
     */
    private void migrateLegacyJobClass(JobInfo jobInfo) {
        if (!LEGACY_AGENT_JOB_CLASS.equals(jobInfo.getJobClass())) {
            return;
        }
        int updated = jdbcTemplate.update(
                "UPDATE quartz_job_info SET job_class = ? WHERE id = ? AND job_class = ?",
                AGENT_JOB_CLASS,
                jobInfo.getId(),
                LEGACY_AGENT_JOB_CLASS
        );
        jobInfo.setJobClass(AGENT_JOB_CLASS);
        log.info("已迁移旧版 Agent 定时任务类名: jobId={}, updated={}", jobInfo.getId(), updated);
    }

    public static QuartzConfig buildConfig(JobInfo jobInfo) throws ClassNotFoundException {
        Class<?> aClass = Class.forName(jobInfo.getJobClass());
        if (QuartzJob.class.isAssignableFrom(aClass)) {
            Class<? extends QuartzJob> jobClass = aClass.asSubclass(QuartzJob.class);

            Long createdBy = jobInfo.getCreatedBy();
            AccountVO accountVO = new AccountVO();
            // 使用 jdbcTemplate 绕过租户拦截器，避免污染当前线程的租户上下文
            String accountSql = "SELECT id, username, nickname, email, enabled, remember_last_tenant FROM " + TableConst.ACCOUNT + " WHERE id = ?";
            staticJdbcTemplate.query(accountSql, (rs) -> {
                accountVO.setId(rs.getLong("id"));
                accountVO.setUsername(rs.getString("username"));
                accountVO.setNickname(rs.getString("nickname"));
                accountVO.setEmail(rs.getString("email"));
                accountVO.setEnabled(rs.getBoolean("enabled"));
                accountVO.setRememberLastTenant(rs.getBoolean("remember_last_tenant"));
            }, createdBy);

            // 查询租户信息
            Tenant tenant = new Tenant();
            String tenantSql = "SELECT id, code FROM " + TableConst.TENANT + " WHERE id = ?";
            staticJdbcTemplate.query(tenantSql, (rs) -> {
                tenant.setId(rs.getLong("id"));
                tenant.setCode(rs.getString("code"));
            }, jobInfo.getTenantId());

            return new QuartzConfigFactory()
                    // 设置唯一标识，一般是ID
                    .identity(jobInfo.getId())
                    // 设置自定义doJob类
                    .setJobClass(jobClass)
                    // 可以再自定义job中取到在此传递的值
                    .putDataMap(
                            JobConst.DATA_MAP_KEY,
                            JsonUtils.parse(jobInfo.getDataMap(), AgentJobWrapper.class))
                    // 传递租户ID，用于Job执行时恢复租户上下文
                    .putDataMap(JobConst.TENANT_ID_KEY, tenant.getId())
                    .putDataMap(JobConst.TENANT_CODE_KEY, tenant.getCode())
                    // 用户信息
                    .putDataMap(JobConst.USER_INFO_KEY, accountVO)
                    // 设置cron表达式
                    .cron(jobInfo.getCron())
                    // 获取到config
                    .build();
        } else {
            throw new RuntimeException("类型不兼容，无法转换：" + jobInfo.getJobClass() + "未继承自QuartzJob");
        }
    }

    @Override
    public void afterSingletonsInstantiated() {
        doJobInit();
    }
}
