package com.hxh.apboa.console.schedule;

import com.hxh.apboa.common.consts.RedisChannelTopic;
import com.hxh.apboa.common.consts.TableConst;
import com.hxh.apboa.common.router.MessageTableRouter;
import com.hxh.apboa.common.util.RedisUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 聊天消息归档定时任务：每天凌晨 3:30 执行
 * 将超过 1 个月未活跃的会话的消息从 chat_message 迁移到 chat_message_yyyyMM 归档表
 * 使用 Redis 分布式锁确保多节点部署时只有一个节点执行
 *
 * @author huxuehao
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatMessageArchiveTask {

    private static final String CHAT_MESSAGE_HIS = TableConst.CHAT_MESSAGE + "_";

    private final RedisUtils redisUtils;
    private final JdbcTemplate jdbcTemplate;
    private final MessageTableRouter messageTableRouter;
    private final TransactionTemplate transactionTemplate;

    /** 锁超时 10 分钟，归档操作一般秒级完成 */
    private static final long LOCK_TIMEOUT_SEC = 600;

    /** 每批处理的会话数 */
    private static final int BATCH_SIZE = 200;

    @Scheduled(cron = "0 30 3 * * ?")
    public void archiveOldMessages() {
        String lockValue = UUID.randomUUID().toString();
        if (!redisUtils.tryLock(RedisChannelTopic.LOCK_MESSAGE_ARCHIVE, lockValue,
                LOCK_TIMEOUT_SEC, TimeUnit.SECONDS)) {
            log.info("消息归档锁被其他节点持有，跳过本次执行");
            return;
        }

        try {
            doArchive();
        } catch (Exception e) {
            log.error("消息归档执行失败", e);
        } finally {
            redisUtils.unlock(RedisChannelTopic.LOCK_MESSAGE_ARCHIVE, lockValue);
        }
    }

    /** 归档失败标记，挂在此标记的会话不会被 where 条件选中，需人工排查后清理 */
    private static final String ARCHIVE_FAILED = "ARCHIVE_FAILED";

    /**
     * 执行归档逻辑
     */
    private void doArchive() {
        // 1. 确定目标月份：归档上个月的数据
        LocalDate archiveMonth = LocalDate.now().minusMonths(1);
        String targetTable = CHAT_MESSAGE_HIS + archiveMonth.format(DateTimeFormatter.ofPattern("yyyyMM"));
        // 截止时间：恰好 1 个月前
        LocalDateTime cutoff = LocalDateTime.now().minusMonths(1);

        // 2. 创建归档表（每月只创建一次，已存在则跳过）
        messageTableRouter.createArchiveTableIfNotExists(targetTable);

        // 3. 游标分页迁移：WHERE id > lastMaxId 确保即使某会话失败，游标也会前移，不会无限重试
        long lastMaxId = 0L;
        int totalMigrated = 0;

        while (true) {
            List<Long> sessionIds = jdbcTemplate.query(
                    "SELECT id FROM chat_session " +
                            "WHERE id > ? " +
                            "AND (message_table IS NULL OR message_table = '') " +
                            "AND updated_at < ? " +
                            "ORDER BY id ASC LIMIT ?",
                    (rs, rowNum) -> rs.getLong("id"),
                    lastMaxId, cutoff, BATCH_SIZE);

            if (sessionIds.isEmpty()) {
                break;
            }

            // 逐会话在事务中迁移，保证原子性
            for (Long sessionId : sessionIds) {
                lastMaxId = Math.max(lastMaxId, sessionId);
                try {
                    transactionTemplate.executeWithoutResult(status -> {
                        // 3a. 校验主表消息数，防止空迁移或归档表结构不匹配导致静默丢数据
                        Integer count = jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM chat_message WHERE session_id = ?",
                                Integer.class, sessionId);
                        if (count == null || count == 0) {
                            log.warn("会话 {} 无消息可归档，跳过", sessionId);
                            return;
                        }

                        // 3b. 将消息从主表插入到归档表
                        int moved = jdbcTemplate.update(
                                "INSERT INTO " + targetTable +
                                        " (id, tenant_id, session_id, role, content, parent_id, path, depth, created_at) " +
                                        "SELECT id, tenant_id, session_id, role, content, parent_id, path, depth, created_at " +
                                        "FROM chat_message WHERE session_id = ?",
                                sessionId);

                        if (moved != count) {
                            throw new RuntimeException(
                                    String.format("归档数据不一致: 预期 %d 条，实际插入 %d 条", count, moved));
                        }

                        // 3c. 从主表删除已迁移的消息
                        jdbcTemplate.update(
                                "DELETE FROM chat_message WHERE session_id = ?",
                                sessionId);

                        // 3d. 更新会话的 message_table 指向归档表
                        jdbcTemplate.update(
                                "UPDATE chat_session SET message_table = ? WHERE id = ?",
                                targetTable, sessionId);

                        // 4e. 删除 agentscope_sessions 中会话的消息
                        jdbcTemplate.update(
                                "DELETE FROM agentscope_sessions WHERE session_id = ?",
                                sessionId);

                        log.debug("归档会话 {} 的 {} 条消息 -> {}", sessionId, moved, targetTable);
                    });
                    totalMigrated++;
                } catch (Exception e) {
                    log.error("归档会话 {} 失败: {}", sessionId, e.getMessage());
                    // 标记失败，防止下次执行重复抓取，需人工排查后清空该标记
                    markFailed(sessionId);
                }
            }
        }

        log.info("消息归档完成，共迁移 {} 个会话到 {}", totalMigrated, targetTable);
    }

    /**
     * 标记归档失败的会话，避免每次执行都重试导致日志轰炸或死循环。
     * 标记后 message_table = 'ARCHIVE_FAILED'，不会再被 where 条件选中。
     * 运维排查问题后，将该字段设回 NULL 即可触发重新归档。
     */
    private void markFailed(Long sessionId) {
        try {
            jdbcTemplate.update(
                    "UPDATE chat_session SET message_table = ? WHERE id = ?",
                    ARCHIVE_FAILED, sessionId);
        } catch (Exception ex) {
            log.error("标记会话 {} 归档失败时出错: {}", sessionId, ex.getMessage());
        }
    }
}
