package com.hxh.apboa.engine.tts;

import com.hxh.apboa.common.enums.ModelProviderType;
import com.hxh.apboa.common.util.FuncUtils;
import com.hxh.apboa.common.wrapper.ModelConfigWrapper;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * OpenAI 兼容协议的语音合成实现：POST {baseUrl}/v1/audio/speech（JSON: model + input + 可选参数），
 * 响应体即音频二进制。通吃 mlx-audio server、kokoro-fastapi、OpenAI 官方等一切兼容服务，
 * 供应商配置用 OPEN_AI 类型、baseUrl 指向目标服务（本地服务 apiKey 填占位值即可）。
 * voice/speed/response_format 及协议超集字段（如 Qwen3-TTS 的 instruct、lang_code）
 * 均经 extendConfig.bodyParams 透传，与请求体顶层字段直接合并。
 *
 * @author huxuehao
 */
@Component
public class OpenAiCompatTtsProvider implements TtsProvider {

    /** 长段合成 + 本地模型冷加载都慢于转写，超时放宽到 120s */
    private static final Duration TIMEOUT = Duration.ofSeconds(120);

    /** 音频下行较大（280 字 WAV 约 3-4MB），放宽内存缓冲 */
    private static final WebClient WEB_CLIENT = WebClient.builder()
            .exchangeStrategies(ExchangeStrategies.builder()
                    .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(32 * 1024 * 1024))
                    .build())
            .build();

    @Override
    public ModelProviderType getType() {
        return ModelProviderType.OPEN_AI;
    }

    @Override
    public TtsResult synthesize(ModelConfigWrapper config, String text) {
        String url = buildUrl(config.getBaseUrl());

        Map<String, Object> body = new LinkedHashMap<>();
        // 缺省请求 WAV（浏览器与后端处理最通用），bodyParams 可覆盖为 mp3 等
        body.put("response_format", "wav");
        if (config.getBodyParams() != null) {
            body.putAll(config.getBodyParams());
        }
        // model/input 是协议骨架字段，不允许被 bodyParams 覆盖
        body.put("model", config.getModelCode());
        body.put("input", text);

        ResponseEntity<byte[]> response;
        try {
            response = WEB_CLIENT.post()
                    .uri(url)
                    .headers(headers -> {
                        if (!FuncUtils.isEmpty(config.getApiKey())) {
                            headers.setBearerAuth(config.getApiKey());
                        }
                    })
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .toEntity(byte[].class)
                    .block(TIMEOUT);
        } catch (WebClientResponseException e) {
            throw new RuntimeException("语音合成服务返回错误: " + abbreviate(e.getResponseBodyAsString()), e);
        }

        if (response == null || response.getBody() == null || response.getBody().length == 0) {
            throw new RuntimeException("语音合成服务返回空音频");
        }
        return new TtsResult(response.getBody(), resolveMime(response, body));
    }

    /**
     * baseUrl 兼容带/不带 /v1 两种配置习惯（与 OpenAiCompatAsrProvider 保持一致）
     */
    private String buildUrl(String baseUrl) {
        if (FuncUtils.isEmpty(baseUrl)) {
            throw new RuntimeException("语音合成供应商未配置 baseUrl");
        }
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return base.endsWith("/v1") ? base + "/audio/speech" : base + "/v1/audio/speech";
    }

    /**
     * MIME 优先取响应 Content-Type，服务端没标（或标成通用流）时按请求的 response_format 推断
     */
    private String resolveMime(ResponseEntity<byte[]> response, Map<String, Object> requestBody) {
        MediaType contentType = response.getHeaders().getContentType();
        if (contentType != null && "audio".equals(contentType.getType())) {
            return contentType.toString();
        }
        String format = String.valueOf(requestBody.getOrDefault("response_format", "wav"));
        return switch (format) {
            case "mp3" -> "audio/mpeg";
            case "opus" -> "audio/opus";
            case "aac" -> "audio/aac";
            case "flac" -> "audio/flac";
            default -> "audio/wav";
        };
    }

    private String abbreviate(String s) {
        if (s == null) {
            return "null";
        }
        return s.length() > 300 ? s.substring(0, 300) + "..." : s;
    }
}
