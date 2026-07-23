package com.hxh.apboa.engine.log;

import com.hxh.apboa.common.entity.ChatMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 描述：日志消费者
 *
 * @author huxuehao
 **/
@Slf4j
@Component
public class ConcurrentLogConsumer {
    private static final Object START_LOCK = new Object();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile ExecutorService consumerExecutor;

    /** 批量消费的最大条数 */
    private static final int BATCH_SIZE = 200;
    /** 队列为空时的等待时间（毫秒），避免CPU空转 */
    private static final int POLL_TIMEOUT_MS = 500;

    private final BlockingQueue<ChatMessage> queue;
    private final JdbcTemplate jdbcTemplate;

    public ConcurrentLogConsumer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.queue = ConcurrentLogProducer.getQueue();
    }

    /**
     * 其他线程调用该类，将尝试启动日志消费者消费
     */
    public void tryStart() {
        if (!running.get()) {
            synchronized (START_LOCK) {
                if (!running.get()) {
                    consumerExecutor = Executors.newSingleThreadExecutor(r -> {
                        Thread t = new Thread(r);
                        t.setName("Apboa-Chat-Log-Consumer");
                        t.setDaemon(true);
                        return t;
                    });

                    consumerExecutor.submit(this::consumeLog);
                    running.set(true);
                }
            }
        }
    }

    /**
     * 执行日志消费 - 批量模式
     * 循环从队列中取数据，积攒到 BATCH_SIZE 或等待超时后，一次性批量插入
     */
    private void consumeLog() {
        List<ChatMessage> batch = new ArrayList<>(BATCH_SIZE);

        while (!Thread.currentThread().isInterrupted() && running.get()) {
            try {
                // 先阻塞取一条，保证队列为空时不会CPU空转
                ChatMessage first = queue.poll(POLL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                if (first != null) {
                    batch.add(first);
                }

                // 非阻塞地继续取，积攒到 BATCH_SIZE 或队列为空为止
                queue.drainTo(batch, BATCH_SIZE - batch.size());

                if (!batch.isEmpty()) {
                    doBatchInsert(batch);
                    batch.clear();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                // 退出前尝试把剩余日志入库
                if (!batch.isEmpty()) {
                    doBatchInsert(batch);
                }
                break;
            }
        }
    }

    /**
     * 逐条插入聊天消息，复刻 ChatSessionServiceImpl.appendMessage 逻辑
     * 使用 JdbcTemplate 绕过 MyBatis-Plus 租户拦截器
     */
    private void doBatchInsert(List<ChatMessage> batch) {
        if (batch.isEmpty()) {
            return;
        }

        for (ChatMessage message : batch) {
            try {
                // getAndCheckSession - 会话不存在则跳过
                Long currentMessageId = jdbcTemplate.query(
                        "SELECT current_message_id FROM chat_session WHERE id = ?",
                        rs -> rs.next() ? rs.getLong("current_message_id") : null,
                        message.getSessionId()
                );
                if (currentMessageId == null) {
                    continue;
                }

                // getMessageBy - 父消息不存在则跳过
                ParentInfo parent = jdbcTemplate.query(
                        "SELECT id, path, depth FROM chat_message WHERE id = ? AND session_id = ?",
                        rs -> {
                            if (rs.next()) {
                                return new ParentInfo(
                                        rs.getInt("id"),
                                        rs.getString("path"),
                                        (Integer) rs.getObject("depth")
                                );
                            }
                            return null;
                        },
                        currentMessageId, message.getSessionId()
                );
                if (parent == null) {
                    continue;
                }

                // saveNewMessageAndMoveCursor
                // 1. INSERT
                KeyHolder keyHolder = new GeneratedKeyHolder();
                jdbcTemplate.update(connection -> {
                    PreparedStatement ps = connection.prepareStatement(
                            "INSERT INTO chat_message (tenant_id, session_id, role, content, parent_id, meta, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                            Statement.RETURN_GENERATED_KEYS
                    );
                    ps.setLong(1, message.getTenantId());
                    ps.setLong(2, message.getSessionId());
                    ps.setString(3, message.getRole());
                    ps.setString(4, message.getContent());
                    ps.setInt(5, parent.id);
                    ps.setString(6, message.getMeta());
                    ps.setObject(7, message.getCreatedAt());
                    return ps;
                }, keyHolder);
                Integer newId = keyHolder.getKey().intValue();

                // 2. UPDATE path and depth
                String newPath = (parent.path == null || parent.path.isEmpty())
                        ? String.valueOf(newId)
                        : parent.path + "/" + newId;
                int newDepth = (parent.depth == null ? 0 : parent.depth) + 1;
                jdbcTemplate.update(
                        "UPDATE chat_message SET path = ?, depth = ? WHERE id = ?",
                        newPath, newDepth, newId
                );

                // 3. UPDATE session current_message_id
                jdbcTemplate.update(
                        "UPDATE chat_session SET current_message_id = ? WHERE id = ?",
                        newId, message.getSessionId()
                );
            } catch (Exception ex) {
                log.error("日志插入失败: {}", ex.getMessage());
            }
        }
    }

    /**
     * 父消息信息（对应 getMessageBy 返回的 ChatMessage 中所需字段）
     */
    private record ParentInfo(Integer id, String path, Integer depth) {}

    @PreDestroy
    public void destroy() {
        running.set(false);
        if (consumerExecutor != null) {
            consumerExecutor.shutdownNow();
        }
    }
}
