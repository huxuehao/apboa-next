package com.hxh.apboa.engine.asr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hxh.apboa.common.enums.ModelProviderType;
import com.hxh.apboa.common.util.FuncUtils;
import com.hxh.apboa.common.wrapper.ModelConfigWrapper;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Base64;

/**
 * DashScope（阿里百炼）语音识别实现：qwen3-asr-flash 走 multimodal-generation 同步接口，
 * 音频以 data URI base64 内联（官方限制 base64 后 ≤10MB，60 秒 16k WAV 约 2.6MB），
 * 转写文字位于 output.choices[0].message.content[0].text。
 *
 * @author huxuehao
 */
@Component
public class DashScopeAsrProvider implements AsrProvider {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String GENERATION_PATH = "/api/v1/services/aigc/multimodal-generation/generation";

    /** base64 音频内联后请求体较大，放宽内存缓冲 */
    private static final WebClient WEB_CLIENT = WebClient.builder()
            .exchangeStrategies(ExchangeStrategies.builder()
                    .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                    .build())
            .build();

    @Override
    public ModelProviderType getType() {
        return ModelProviderType.DASH_SCOPE;
    }

    @Override
    public String recognize(ModelConfigWrapper config, byte[] audioWav) {
        if (FuncUtils.isEmpty(config.getBaseUrl())) {
            throw new RuntimeException("语音识别供应商未配置 baseUrl");
        }
        String base = config.getBaseUrl().endsWith("/")
                ? config.getBaseUrl().substring(0, config.getBaseUrl().length() - 1)
                : config.getBaseUrl();

        String response = WEB_CLIENT.post()
                .uri(base + GENERATION_PATH)
                .headers(headers -> headers.setBearerAuth(config.getApiKey()))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(buildRequestBody(config.getModelCode(), audioWav))
                .retrieve()
                .bodyToMono(String.class)
                .block(Duration.ofSeconds(60));

        return parseText(response);
    }

    private String buildRequestBody(String modelCode, byte[] audioWav) {
        ObjectNode root = OBJECT_MAPPER.createObjectNode();
        root.put("model", modelCode);

        ObjectNode audioPart = OBJECT_MAPPER.createObjectNode();
        audioPart.put("audio", "data:audio/wav;base64," + Base64.getEncoder().encodeToString(audioWav));

        ObjectNode message = OBJECT_MAPPER.createObjectNode();
        message.put("role", "user");
        ArrayNode content = message.putArray("content");
        content.add(audioPart);

        ObjectNode input = root.putObject("input");
        input.putArray("messages").add(message);

        return root.toString();
    }

    private String parseText(String response) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(response);
            JsonNode text = root.path("output").path("choices").path(0)
                    .path("message").path("content").path(0).path("text");
            if (text.isMissingNode() || text.isNull()) {
                throw new RuntimeException("响应缺少转写文字字段");
            }
            return stripAsrPrefix(text.asText());
        } catch (Exception e) {
            String brief = response != null && response.length() > 300 ? response.substring(0, 300) + "..." : String.valueOf(response);
            throw new RuntimeException("语音识别响应解析失败: " + brief, e);
        }
    }
}
