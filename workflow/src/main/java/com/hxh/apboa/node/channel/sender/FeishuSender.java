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
 * 飞书机器人消息发送器
 *
 * @author huxuehao
 */
public class FeishuSender implements ChannelSender {

    @Override
    public void send(Channel channel, MessageParams params) throws Exception {
        String configJson = channel.getConfig();
        if (FuncUtils.isEmpty(configJson)) {
            throw new RuntimeException("飞书配置不能为空");
        }
        JsonNode config = JsonUtils.parse(configJson);
        String webhook = getString(config, "webhook", true);

        String content = params.getContent();
        if (FuncUtils.isEmpty(content)) {
            throw new RuntimeException("消息内容不能为空");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("msg_type", "interactive");

        Map<String, Object> card = new LinkedHashMap<>();
        Map<String, Object> header = new LinkedHashMap<>();
        String subject = params.getSubject() != null && !params.getSubject().isBlank()
                ? params.getSubject() : "通知";
        header.put("title", Map.of("tag", "plain_text", "content", subject));
        header.put("template", "blue");
        card.put("header", header);

        Map<String, Object> element = new LinkedHashMap<>();
        element.put("tag", "markdown");
        element.put("content", content);
        card.put("elements", new Object[]{element});
        body.put("card", card);

        sendHttpPost(webhook, Objects.requireNonNull(JsonUtils.toJsonStr(body)));
    }

    private String getString(JsonNode node, String key, boolean required) {
        if (!node.has(key) || node.get(key).isNull()) {
            if (required) throw new RuntimeException("飞书配置缺少必要字段: " + key);
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
            throw new RuntimeException("飞书发送失败, HTTP " + code + ": " + new String(resp, StandardCharsets.UTF_8));
        }
    }
}
