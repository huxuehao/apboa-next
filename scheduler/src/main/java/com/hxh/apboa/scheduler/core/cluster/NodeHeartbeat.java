package com.hxh.apboa.scheduler.core.cluster;

import com.hxh.apboa.scheduler.consts.JobRedisKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 描述：调度节点心跳
 * 每30秒向Redis写入心跳Key（90秒TTL），用于其他节点判断本节点是否存活。
 * 与JobDistributedLock的负载均衡逻辑配合，使负载均衡只计算活跃节点。
 *
 * @author huxuehao
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NodeHeartbeat {

    private final StringRedisTemplate stringRedisTemplate;
    private final NodeConfig nodeConfig;

    /**
     * 心跳Key的TTL（秒）
     * 90秒 = 3个心跳周期，容忍1-2次心跳丢失
     */
    private static final long HEARTBEAT_TTL_SECONDS = 90;

    /**
     * 发送心跳
     * 每30秒执行一次，在Redis中写入本节点的心跳Key并设置过期时间
     */
    @Scheduled(cron = "*/30 * * * * ?")
    public void sendHeartbeat() {
        try {
            String heartbeatKey = JobRedisKey.getNodeHeartbeatKey(nodeConfig.getNodeId());
            stringRedisTemplate.opsForValue().set(
                    heartbeatKey,
                    String.valueOf(System.currentTimeMillis()),
                    HEARTBEAT_TTL_SECONDS,
                    TimeUnit.SECONDS);
            log.debug("调度节点心跳发送成功 - nodeId: {}", nodeConfig.getNodeId());
        } catch (Exception e) {
            log.warn("调度节点心跳发送失败 - nodeId: {}", nodeConfig.getNodeId(), e);
        }
    }
}
