package com.hxh.apboa.node.channel.sender;

import com.fasterxml.jackson.databind.JsonNode;
import com.hxh.apboa.channel.entity.Channel;
import com.hxh.apboa.common.util.FuncUtils;
import com.hxh.apboa.common.util.JsonUtils;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 企业微信机器人消息发送器
 *
 * @author huxuehao
 */
public class WecomSender implements ChannelSender {

    @Override
    public void send(Channel channel, MessageParams params) throws Exception {
        String configJson = channel.getConfig();
        if (FuncUtils.isEmpty(configJson)) {
            throw new RuntimeException("企业微信配置不能为空");
        }
        JsonNode config = JsonUtils.parse(configJson);
        String webhook = getString(config, "webhook", true);

        String content = params.getContent();
        if (FuncUtils.isEmpty(content)) {
            throw new RuntimeException("消息内容不能为空");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("msgtype", "markdown");
        Map<String, Object> markdown = new LinkedHashMap<>();
        String markdownContent = params.getSubject() != null
                ? "## " + params.getSubject() + "\n" + content
                : content;
        markdown.put("content", markdownContent);
        body.put("markdown", markdown);

        // 提及用户
        String mentionMobiles = params.getExtraString("mentionMobiles");
        String mentionUsers = params.getExtraString("mentionUsers");
        if (!FuncUtils.isEmpty(mentionMobiles) || !FuncUtils.isEmpty(mentionUsers)) {
            StringBuilder mentionedList = new StringBuilder();
            if (!FuncUtils.isEmpty(mentionUsers)) {
                for (String u : mentionUsers.split("[,;]")) {
                    String trimmed = u.trim();
                    if (!trimmed.isEmpty()) {
                        mentionedList.append("<@").append(trimmed).append("> ");
                    }
                }
            }
            if (!FuncUtils.isEmpty(mentionMobiles)) {
                for (String m : mentionMobiles.split("[,;]")) {
                    String trimmed = m.trim();
                    if (!trimmed.isEmpty()) {
                        mentionedList.append("<@").append(trimmed).append("> ");
                    }
                }
            }
            markdown.put("content", markdown.get("content") + "\n" + mentionedList);
        }

        sendHttpPost(webhook, JsonUtils.toJsonStr(body));
    }

    private String getString(JsonNode node, String key, boolean required) {
        if (!node.has(key) || node.get(key).isNull()) {
            if (required) throw new RuntimeException("企业微信配置缺少必要字段: " + key);
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
            throw new RuntimeException("企业微信发送失败, HTTP " + code + ": " + new String(resp, StandardCharsets.UTF_8));
        }
    }
}
