-- 修复历史 Agent 定时任务执行类路径。
-- 新部署会直接写入新路径；升级部署可重复执行本补丁。
UPDATE `quartz_job_info`
SET `job_class` = 'com.hxh.apboa.scheduler.scheduler.AgentScheduler'
WHERE `job_class` = 'com.hxh.apboa.job.scheduler.AgentScheduler';
