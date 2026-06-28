package com.hxh.apboa.mq.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hxh.apboa.mq.entity.Mq;
import com.hxh.apboa.mq.enums.MqType;
import com.hxh.apboa.mq.mapper.MqMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;
import java.util.StringJoiner;

@Service
public class MqServiceImpl extends ServiceImpl<MqMapper, Mq> implements MqService {
    private final JdbcTemplate jdbcTemplate;

    public MqServiceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteMq(Integer force, List<String> mqIds) {
        if (mqIds == null || mqIds.isEmpty()) {
            return true;
        }
        List<String> used = usedWorkflowNames(mqIds);
        if (!Integer.valueOf(1).equals(force) && !used.isEmpty()) {
            throw new RuntimeException("MQ is used by workflow: " + String.join(",", used));
        }
        boolean removed = removeByIds(mqIds);
        if (removed) {
            jdbcTemplate.update("delete from workflow_mq where mq_id in (" + placeholders(mqIds.size()) + ")", mqIds.toArray());
        }
        return removed;
    }

    @Override
    public boolean updateEnable(String mqId, Integer enable) {
        return lambdaUpdate().set(Mq::getEnabled, toBoolean(enable)).eq(Mq::getId, mqId).update();
    }

    @Override
    public boolean checkConnect(Mq mq) {
        int port = mq.getPort() == null ? defaultPort(mq.getType()) : mq.getPort();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(mq.getAddress(), port), 5000);
            return true;
        } catch (Exception e) {
            throw new RuntimeException("MQ connection failed: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Mq> listByEnabled(Integer enabled) {
        if (enabled == null) {
            return list();
        }
        return lambdaQuery().eq(Mq::getEnabled, toBoolean(enabled)).list();
    }

    private Boolean toBoolean(Integer enabled) {
        return enabled != null && enabled == 1;
    }

    private int defaultPort(MqType type) {
        if (type == MqType.RABBITMQ) {
            return 5672;
        }
        if (type == MqType.ROCKETMQ) {
            return 9876;
        }
        return 9092;
    }

    private List<String> usedWorkflowNames(List<String> ids) {
        return jdbcTemplate.queryForList(
                "select distinct w.name from workflow_mq wm join workflow w on w.id = wm.workflow_id where wm.mq_id in (" + placeholders(ids.size()) + ")",
                String.class,
                ids.toArray()
        );
    }

    private String placeholders(int size) {
        StringJoiner joiner = new StringJoiner(",");
        for (int i = 0; i < size; i++) {
            joiner.add("?");
        }
        return joiner.toString();
    }
}
