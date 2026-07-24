package com.hxh.apboa.runtime.tts;

import com.hxh.apboa.common.util.FuncUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;

/**
 * 本地 TTS（mlx-audio 等 OpenAI 兼容克隆服务）的音色目录客户端。
 *
 * 私有协议：TTS 服务在 {baseUrl}/voices 暴露音色清单（扫描它自己项目内的音色文件夹），返回
 * [{ "name": 音色名, "refAudio": 参考音频绝对路径, "refText": 参考音频转写 }]。
 * 音色文件夹规范：{音色名}/audio.wav + ref.txt(+ design.txt 可选，仅存档不参与合成)。
 *
 * 该协议只属于 TTS，与 ASR 无关。前端配置拉列表、runtime 合成前把音色名解析成
 * ref_audio+ref_text 都走这里。
 *
 * @author vaulka
 */
@Component
public class VoiceCatalogClient {

    /** 音色目录条目（私有协议契约字段） */
    public record VoiceItem(String name, String refAudio, String refText) {
    }

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final WebClient WEB_CLIENT = WebClient.builder().build();

    /**
     * 拉取音色清单。baseUrl 为 TTS 服务地址（兼容带/不带 /v1）；失败抛异常，由调用方决定回退（前端下拉置空）。
     */
    public List<VoiceItem> list(String baseUrl) {
        VoiceItem[] items = WEB_CLIENT.get()
                .uri(buildUrl(baseUrl))
                .retrieve()
                .bodyToMono(VoiceItem[].class)
                .block(TIMEOUT);
        return items == null ? List.of() : List.of(items);
    }

    /** 按音色名查一条；找不到返回 null */
    public VoiceItem find(String baseUrl, String name) {
        if (FuncUtils.isEmpty(name)) {
            return null;
        }
        return list(baseUrl).stream()
                .filter(v -> name.equals(v.name()))
                .findFirst()
                .orElse(null);
    }

    /** {baseUrl}/voices；baseUrl 末尾的 /v1 剥掉（音色接口在服务根，不在 OpenAI 的 /v1 下） */
    private String buildUrl(String baseUrl) {
        if (FuncUtils.isEmpty(baseUrl)) {
            throw new RuntimeException("TTS 服务未配置 baseUrl，无法获取音色列表");
        }
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        if (base.endsWith("/v1")) {
            base = base.substring(0, base.length() - 3);
        }
        return base + "/voices";
    }
}
