package com.hxh.apboa.scheduler.consts;

import com.hxh.apboa.common.util.RedisKeyBuilder;

/**
 * 描述：Redis消息通道常量
 * 用于集群节点间通信的频道定义
 *
 * @author huxuehao
 **/
public class JobRedisKey {

    /**
     * 节点心跳通道
     * 用于节点状态同步
     */
    public static final String JOB_CLUSTER_HEARTBEAT = "apboa:job:cluster:heartbeat";

    /**
     * 获取任务执行锁的key
     *
     * @param jobId 任务ID
     * @return 锁key
     */
    public static String getJobLockKey(String jobId) {
        return RedisKeyBuilder.tenantKey("job:lock:" + jobId);
    }

    /**
     * 获取任务执行历史的key
     *
     * @param jobId 任务ID
     * @return 历史key
     */
    public static String getJobExecHistoryKey(String jobId) {
        return RedisKeyBuilder.tenantKey("job:exec:history:" + jobId);
    }

    /**
     * 节点心跳Key前缀，用于 SCAN 查找所有活跃节点
     */
    private static final String NODE_HEARTBEAT_PREFIX = RedisKeyBuilder.globalKey("job:heartbeat:");

    /**
     * 获取节点心跳Key
     *
     * @param nodeId 节点ID
     * @return 心跳key
     */
    public static String getNodeHeartbeatKey(String nodeId) {
        return NODE_HEARTBEAT_PREFIX + nodeId;
    }

    /**
     * 获取心跳Key匹配模式，用于 redisTemplate.keys() 查找所有活跃节点
     *
     * @return 匹配模式
     */
    public static String getNodeHeartbeatPattern() {
        return NODE_HEARTBEAT_PREFIX + "*";
    }

    /**
     * 获取心跳Key前缀，用于从完整Key中提取nodeId
     *
     * @return 心跳Key前缀
     */
    public static String getNodeHeartbeatPrefix() {
        return NODE_HEARTBEAT_PREFIX;
    }
}
