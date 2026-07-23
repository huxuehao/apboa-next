package com.hxh.apboa.engine.tts;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Map;

/**
 * DashScope（阿里百炼）语音合成实现：qwen3-tts 系列走 multimodal-generation 同步接口
 * （与 DashScopeAsrProvider 同一路径），请求体 input:{text, voice, language_type}，
 * 非流式响应给出 output.audio.url（OSS 上的 WAV，24h 有效），本实现二次下载转为字节返回，
 * 保证对上层与 OpenAI 兼容实现输出形态一致。voice 缺省 Cherry，
 * 可与 language_type 等一并经 extendConfig.bodyParams 覆盖（并入 input 对象）。
 *
 * @author huxuehao
 */
@Component
public class DashScopeTtsProvider implements TtsProvider {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String GENERATION_PATH = "/api/v1/services/aigc/multimodal-generation/generation";

    private static final String DEFAULT_VOICE = "Cherry";

    private static final Duration TIMEOUT = Duration.ofSeconds(120);

    /** 音频下载较大，放宽内存缓冲 */
    private static final WebClient WEB_CLIENT = WebClient.builder()
            .exchangeStrategies(ExchangeStrategies.builder()
                    .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(32 * 1024 * 1024))
                    .build())
            .build();

    @Override
    public ModelProviderType getType() {
        return ModelProviderType.DASH_SCOPE;
    }

    @Override
    public TtsResult synthesize(ModelConfigWrapper config, String text) {
        if (FuncUtils.isEmpty(config.getBaseUrl())) {
            throw new RuntimeException("语音合成供应商未配置 baseUrl");
        }
        String base = config.getBaseUrl().endsWith("/")
                ? config.getBaseUrl().substring(0, config.getBaseUrl().length() - 1)
                : config.getBaseUrl();

        String response = WEB_CLIENT.post()
                .uri(base + GENERATION_PATH)
                .headers(headers -> headers.setBearerAuth(config.getApiKey()))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(buildRequestBody(config, text))
                .retrieve()
                .bodyToMono(String.class)
                .block(TIMEOUT);

        return parseAudio(response);
    }

    private String buildRequestBody(ModelConfigWrapper config, String text) {
        ObjectNode root = OBJECT_MAPPER.createObjectNode();
        root.put("model", config.getModelCode());

        ObjectNode input = root.putObject("input");
        input.put("voice", DEFAULT_VOICE);
        // bodyParams 并入 input（可覆盖 voice、补 language_type 等），text 是骨架字段最后强制写入
        Map<String, Object> bodyParams = config.getBodyParams();
        if (bodyParams != null) {
            bodyParams.forEach((key, value) -> input.set(key, OBJECT_MAPPER.valueToTree(value)));
        }
        input.put("text", text);

        return root.toString();
    }

    /**
     * 非流式响应音频在 output.audio.url（个别形态给 base64 的 output.audio.data，一并兼容）
     */
    private TtsResult parseAudio(String response) {
        JsonNode audio;
        try {
            JsonNode root = OBJECT_MAPPER.readTree(response);
            audio = root.path("output").path("audio");
            if (audio.isMissingNode() || audio.isNull()) {
                throw new RuntimeException("响应缺少 output.audio 字段");
            }
        } catch (Exception e) {
            throw new RuntimeException("语音合成响应解析失败: " + abbreviate(response), e);
        }

        String url = audio.path("url").asText(null);
        if (!FuncUtils.isEmpty(url)) {
            byte[] bytes = WEB_CLIENT.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(byte[].class)
                    .block(TIMEOUT);
            if (bytes == null || bytes.length == 0) {
                throw new RuntimeException("音频文件下载失败: " + url);
            }
            return new TtsResult(bytes, "audio/wav");
        }

        String data = audio.path("data").asText(null);
        if (!FuncUtils.isEmpty(data)) {
            return new TtsResult(Base64.getDecoder().decode(data), "audio/wav");
        }
        throw new RuntimeException("响应 output.audio 既无 url 也无 data: " + abbreviate(response));
    }

    private String abbreviate(String s) {
        if (s == null) {
            return "null";
        }
        return s.length() > 300 ? s.substring(0, 300) + "..." : s;
    }
}
