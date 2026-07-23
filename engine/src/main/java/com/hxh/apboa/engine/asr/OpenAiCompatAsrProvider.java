package com.hxh.apboa.engine.asr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hxh.apboa.common.enums.ModelProviderType;
import com.hxh.apboa.common.util.FuncUtils;
import com.hxh.apboa.common.wrapper.ModelConfigWrapper;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

/**
 * OpenAI 兼容协议的语音识别实现：POST {baseUrl}/v1/audio/transcriptions（multipart: file + model）。
 * 通吃本地 llama.cpp（llama-server）、FunASR funasr-server、OpenAI 官方等一切兼容服务，
 * 供应商配置用 OPEN_AI 类型、baseUrl 指向目标服务（本地服务 apiKey 填占位值即可）。
 *
 * @author huxuehao
 */
@Component
public class OpenAiCompatAsrProvider implements AsrProvider {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** 响应是小 JSON，但音频上行较大，放宽内存缓冲 */
    private static final WebClient WEB_CLIENT = WebClient.builder()
            .exchangeStrategies(ExchangeStrategies.builder()
                    .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                    .build())
            .build();

    @Override
    public ModelProviderType getType() {
        return ModelProviderType.OPEN_AI;
    }

    @Override
    public String recognize(ModelConfigWrapper config, byte[] audioWav) {
        String url = buildUrl(config.getBaseUrl());

        MultipartBodyBuilder body = new MultipartBodyBuilder();
        body.part("file", new ByteArrayResource(audioWav) {
                    @Override
                    public String getFilename() {
                        return "audio.wav";
                    }
                })
                .contentType(MediaType.parseMediaType("audio/wav"));
        body.part("model", config.getModelCode());

        String response = WEB_CLIENT.post()
                .uri(url)
                .headers(headers -> {
                    if (!FuncUtils.isEmpty(config.getApiKey())) {
                        headers.setBearerAuth(config.getApiKey());
                    }
                })
                .body(BodyInserters.fromMultipartData(body.build()))
                .retrieve()
                .bodyToMono(String.class)
                .block(Duration.ofSeconds(60));

        return parseText(response);
    }

    /**
     * baseUrl 兼容带/不带 /v1 两种配置习惯（现有 OPEN_AI 供应商 baseUrl 多以 /v1 结尾）
     */
    private String buildUrl(String baseUrl) {
        if (FuncUtils.isEmpty(baseUrl)) {
            throw new RuntimeException("语音识别供应商未配置 baseUrl");
        }
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return base.endsWith("/v1") ? base + "/audio/transcriptions" : base + "/v1/audio/transcriptions";
    }

    /**
     * 标准响应含顶层 text 字段（llama.cpp 额外带 type/usage 字段，不影响取值）
     */
    private String parseText(String response) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(response);
            JsonNode text = root.get("text");
            if (text == null || text.isNull()) {
                throw new RuntimeException("响应缺少 text 字段");
            }
            return stripAsrPrefix(text.asText());
        } catch (Exception e) {
            throw new RuntimeException("语音识别响应解析失败: " + abbreviate(response), e);
        }
    }

    private String abbreviate(String s) {
        if (s == null) {
            return "null";
        }
        return s.length() > 300 ? s.substring(0, 300) + "..." : s;
    }
}
