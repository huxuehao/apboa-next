package com.hxh.apboa.node.channel.sender;

import com.fasterxml.jackson.databind.JsonNode;
import com.hxh.apboa.channel.entity.Channel;
import com.hxh.apboa.common.util.FuncUtils;
import com.hxh.apboa.common.util.JsonUtils;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 钉钉机器人消息发送器
 *
 * @author huxuehao
 */
public class DingTalkSender implements ChannelSender {

    @Override
    public void send(Channel channel, MessageParams params) throws Exception {
        String configJson = channel.getConfig();
        if (FuncUtils.isEmpty(configJson)) {
            throw new RuntimeException("钉钉配置不能为空");
        }
        JsonNode config = JsonUtils.parse(configJson);
        String webhook = getString(config, "webhook", true);

        String content = params.getContent();
        if (FuncUtils.isEmpty(content)) {
            throw new RuntimeException("消息内容不能为空");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("msgtype", "text");
        Map<String, Object> text = new LinkedHashMap<>();

        StringBuilder textContent = new StringBuilder();
        if (params.getSubject() != null && !params.getSubject().isBlank()) {
            textContent.append("【").append(params.getSubject()).append("】\n");
        }
        textContent.append(content);
        text.put("content", textContent.toString());
        body.put("text", text);

        // @人配置
        Map<String, Object> at = new LinkedHashMap<>();
        String atMobiles = params.getExtraString("atMobiles");
        String atUserIds = params.getExtraString("atUserIds");
        boolean isAtAll = Boolean.TRUE.equals(params.getExtras() != null
                ? params.getExtras().get("isAtAll") : null);

        String[] mobileArr = !FuncUtils.isEmpty(atMobiles) ? atMobiles.split("[,;]") : new String[0];
        String[] userIdArr = !FuncUtils.isEmpty(atUserIds) ? atUserIds.split("[,;]") : new String[0];
        at.put("atMobiles", mobileArr);
        at.put("atUserIds", userIdArr);
        at.put("isAtAll", isAtAll);
        body.put("at", at);

        sendHttpPost(webhook, Objects.requireNonNull(JsonUtils.toJsonStr(body)));
    }

    private String getString(JsonNode node, String key, boolean required) {
        if (!node.has(key) || node.get(key).isNull()) {
            if (required) throw new RuntimeException("钉钉配置缺少必要字段: " + key);
            return null;
        }
        return node.get(key).asText();
    }

    private void sendHttpPost(String url, String body) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        conn.setDoOutput(true);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }
        int code = conn.getResponseCode();
        if (code < 200 || code >= 300) {
            byte[] resp = conn.getErrorStream() != null ? conn.getErrorStream().readAllBytes() : new byte[0];
            throw new RuntimeException("钉钉发送失败, HTTP " + code + ": " + new String(resp, StandardCharsets.UTF_8));
        }
    }
}
