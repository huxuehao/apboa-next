package com.hxh.apboa.channel.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hxh.apboa.channel.entity.Channel;
import com.hxh.apboa.channel.enums.ChannelType;
import com.hxh.apboa.channel.mapper.ChannelMapper;
import com.hxh.apboa.common.enums.HealthStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.LocalDateTime;
import java.util.List;
import java.util.StringJoiner;

/**
 * 通知渠道服务实现
 *
 * @author huxuehao
 */
@Service
public class ChannelServiceImpl extends ServiceImpl<ChannelMapper, Channel> implements ChannelService {
    private final JdbcTemplate jdbcTemplate;

    public ChannelServiceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteChannel(Integer force, List<String> channelIds) {
        if (channelIds == null || channelIds.isEmpty()) {
            return true;
        }
        List<String> used = usedWorkflowNames(channelIds);
        if (!Integer.valueOf(1).equals(force) && !used.isEmpty()) {
            throw new RuntimeException("Channel is used by workflow: " + String.join(",", used));
        }
        boolean removed = removeByIds(channelIds);
        if (removed) {
            jdbcTemplate.update("delete from workflow_channel where channel_id in (" + placeholders(channelIds.size()) + ")", channelIds.toArray());
        }
        return removed;
    }

    @Override
    public boolean updateChannel(Channel channel) {
        return updateById(channel);
    }

    @Override
    public boolean updateEnable(String channelId, Integer enable) {
        return lambdaUpdate().set(Channel::getEnabled, toBoolean(enable)).eq(Channel::getId, channelId).update();
    }

    @Override
    public boolean checkConnect(Channel channel) {
        if (channel.getType() == ChannelType.EMAIL) {
            return checkEmailConnect(channel);
        }
        // 企业微信、钉钉、飞书：HTTP webhook 可达性检测
        return checkWebhookConnect(channel);
    }

    @Override
    public boolean checkSavedConnect(String channelId) {
        Channel channel = getById(channelId);
        if (channel == null) {
            throw new RuntimeException("Channel not found");
        }
        try {
            boolean connected = checkConnect(channel);
            markHealth(channelId, HealthStatus.HEALTHY, null);
            return connected;
        } catch (Exception e) {
            markHealth(channelId, HealthStatus.UNHEALTHY, e.getMessage());
            throw e;
        }
    }

    @Override
    public List<Channel> listByEnabled(Integer enabled) {
        if (enabled == null) {
            return list();
        }
        return lambdaQuery().eq(Channel::getEnabled, toBoolean(enabled)).list();
    }

    /**
     * 检测邮箱 SMTP 连接
     */
    private boolean checkEmailConnect(Channel channel) {
        try {
            String config = channel.getConfig();
            if (config == null || config.isBlank()) {
                throw new RuntimeException("邮箱配置不能为空");
            }
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode node = mapper.readTree(config);
            String host = node.has("serverHost") ? node.get("serverHost").asText() : null;
            int port = node.has("serverPort") ? node.get("serverPort").asInt(25) : 25;
            if (host == null || host.isBlank()) {
                throw new RuntimeException("SMTP 服务器地址不能为空");
            }
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, port), 5000);
            }
            return true;
        } catch (Exception e) {
            throw new RuntimeException("邮箱连接测试失败: " + e.getMessage(), e);
        }
    }

    /**
     * 检测 Webhook URL 可达性
     */
    private boolean checkWebhookConnect(Channel channel) {
        try {
            String config = channel.getConfig();
            if (config == null || config.isBlank()) {
                throw new RuntimeException("渠道配置不能为空");
            }
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode node = mapper.readTree(config);
            String webhook = node.has("webhook") ? node.get("webhook").asText() : null;
            if (webhook == null || webhook.isBlank()) {
                throw new RuntimeException("Webhook 地址不能为空");
            }
            java.net.URL url = new java.net.URL(webhook);
            String host = url.getHost();
            int port = url.getPort() == -1 ? (url.getProtocol().equals("https") ? 443 : 80) : url.getPort();
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, port), 5000);
            }
            return true;
        } catch (Exception e) {
            throw new RuntimeException("Webhook 连接测试失败: " + e.getMessage(), e);
        }
    }

    private void markHealth(String channelId, HealthStatus status, String message) {
        lambdaUpdate()
                .set(Channel::getHealthStatus, status)
                .set(Channel::getLastHealthCheck, LocalDateTime.now())
                .set(Channel::getLastCheckMessage, trimMessage(message))
                .eq(Channel::getId, channelId)
                .update();
    }

    private String trimMessage(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= 500 ? message : message.substring(0, 500);
    }

    private Boolean toBoolean(Integer enabled) {
        return enabled != null && enabled == 1;
    }

    private List<String> usedWorkflowNames(List<String> ids) {
        return jdbcTemplate.queryForList(
                "select distinct w.name from workflow_channel wc join workflow w on w.id = wc.workflow_id where wc.channel_id in (" + placeholders(ids.size()) + ")",
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
