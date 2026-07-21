package com.hxh.apboa.scheduler.init;

import com.hxh.apboa.common.consts.TableConst;
import com.hxh.apboa.common.entity.JobInfo;
import com.hxh.apboa.common.util.JsonUtils;
import com.hxh.apboa.common.vo.AccountVO;
import com.hxh.apboa.common.wrapper.AgentJobWrapper;
import com.hxh.apboa.scheduler.consts.JobConst;
import com.hxh.apboa.scheduler.core.client.QuartzClient;
import com.hxh.apboa.scheduler.core.config.QuartzConfig;
import com.hxh.apboa.scheduler.core.config.QuartzConfigFactory;
import com.hxh.apboa.scheduler.core.job.QuartzJob;
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
public class JobInit implements SmartInitializingSingleton {

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
                quartzClient.create(buildConfig(jobInfo));
            } catch (ClassNotFoundException e) {
                System.err.println(jobInfo.getJobClass() + "不存在");
            }
        }
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
                    .putDataMap(JobConst.TENANT_ID_KEY, jobInfo.getTenantId())
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
