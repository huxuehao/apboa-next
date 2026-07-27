package com.hxh.apboa.gateway.log;

import com.hxh.apboa.gateway.entity.GatewayAccessLog;
import com.hxh.apboa.gateway.mapper.GatewayAccessLogMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 描述：网关日志异步写入器
 * 访问日志经有界队列异步落库，避免阻塞数据面事件循环；
 * 队列满时短暂等待后丢弃，保证网关吞吐优先
 *
 * @author huxuehao
 **/
@Slf4j
@Component
@RequiredArgsConstructor
public class GatewayLogWriter {
    private static final int QUEUE_CAPACITY = 50000;
    private static final long OFFER_TIMEOUT_MS = 3;

    private final GatewayAccessLogMapper accessLogMapper;

    private final LinkedBlockingQueue<GatewayAccessLog> queue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread consumer;

    @PostConstruct
    public void start() {
        running.set(true);
        consumer = new Thread(this::consume, "gateway-log-writer");
        consumer.setDaemon(true);
        consumer.start();
    }

    @PreDestroy
    public void stop() {
        running.set(false);
        if (consumer != null) {
            consumer.interrupt();
        }
    }

    /**
     * 提交访问日志
     */
    public void pushAccessLog(GatewayAccessLog accessLog) {
        accessLog.setCreatedAt(LocalDateTime.now());
        try {
            boolean accepted = queue.offer(accessLog, OFFER_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (!accepted) {
                log.warn("网关日志队列已满，日志被丢弃");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 单线程消费队列并落库
     */
    private void consume() {
        while (running.get() && !Thread.currentThread().isInterrupted()) {
            try {
                GatewayAccessLog accessLog = queue.take();
                try {
                    accessLogMapper.insert(accessLog);
                } catch (Exception e) {
                    log.error("网关日志写入失败: {}", e.getMessage());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
}
