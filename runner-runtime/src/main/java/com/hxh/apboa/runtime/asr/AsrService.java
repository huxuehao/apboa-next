package com.hxh.apboa.runtime.asr;

import com.hxh.apboa.agent.service.AgentDefinitionService;
import com.hxh.apboa.common.entity.AgentDefinition;
import com.hxh.apboa.common.enums.ModelCategory;
import com.hxh.apboa.common.wrapper.ModelConfigWrapper;
import com.hxh.apboa.common.wrapper.ModelWrapper;
import com.hxh.apboa.engine.asr.AsrProviderHolder;
import com.github.houbb.opencc4j.util.ZhConverterUtil;
import com.hxh.apboa.model.service.ModelConfigService;
import com.hxh.apboa.params.core.ParamsAdapter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * 描述：语音识别编排——按智能体绑定的 ASR 模型装配供应商配置并分发识别。
 * 音频仅内存中转即抛，不落盘不落库。
 *
 * @author huxuehao
 **/
@Service
@RequiredArgsConstructor
public class AsrService {

    private final AgentDefinitionService agentDefinitionService;
    private final ModelConfigService modelConfigService;
    private final AsrProviderHolder asrProviderHolder;
    private final ParamsAdapter paramsAdapter;

    /**
     * 整段识别：校验智能体绑定与音频时长，路由到对应供应商实现
     */
    public String recognize(Long agentId, MultipartFile file) {
        AgentDefinition agent = agentDefinitionService.getById(agentId);
        if (agent == null || !agent.getEnabled()) {
            throw new RuntimeException("智能体不存在或已禁用");
        }
        Long asrModelConfigId = agent.getAsrModelConfigId();
        if (asrModelConfigId == null) {
            throw new RuntimeException("该智能体未启用语音输入");
        }

        if (file == null || file.isEmpty()) {
            throw new RuntimeException("音频内容为空");
        }
        byte[] audio;
        try {
            audio = file.getBytes();
        } catch (Exception e) {
            throw new RuntimeException("读取音频失败", e);
        }

        WavInfo wav = parseWav(audio);
        double duration = (double) wav.dataSize() / wav.byteRate();
        int maxDuration = Integer.parseInt(paramsAdapter.getValue("ASR_MAX_DURATION"));
        if (duration > maxDuration) {
            throw new RuntimeException("语音时长超出限制（最长 " + maxDuration + " 秒）");
        }
        ensureVoicePresent(audio, wav);

        // getModelWrapperById 已校验模型与供应商的存在/启用，并完成租户隔离
        ModelWrapper wrapper = modelConfigService.getModelWrapperById(asrModelConfigId);
        if (wrapper.getConfig().getCategory() != ModelCategory.ASR) {
            throw new RuntimeException("绑定的模型用途不是语音识别");
        }

        ModelConfigWrapper configWrapper = new ModelConfigWrapper();
        wrapper.getConfig().fillModelConfigWrapper(configWrapper);
        wrapper.getProvider().fillModelConfigWrapper(configWrapper);

        String text = asrProviderHolder.get(configWrapper.getProvider()).recognize(configWrapper, audio);

        // Qwen3-ASR 自动语种检测偶发繁体方向，模型与接口层均无简繁参数（已调研实证），
        // 出口统一繁→简（该方向词级转换几乎无损）；粤语等需要繁体的场景由参数关闭
        if (Boolean.parseBoolean(paramsAdapter.getValue("ASR_TRADITIONAL_TO_SIMPLE"))) {
            text = ZhConverterUtil.toSimple(text);
        }
        return text;
    }

    /**
     * WAV 关键信息（16bit PCM）：采样率、字节率、data 段偏移与长度
     */
    private record WavInfo(int sampleRate, int byteRate, int dataOffset, long dataSize) {
    }

    /**
     * 解析 WAV 头。一期仅接受 WAV（前端录音统一产 16kHz 16bit 单声道 WAV），
     * 其他格式明确拒绝——时长限制是计费与滥用的闸门，不能对无法解析的格式放行。
     */
    private WavInfo parseWav(byte[] wav) {
        if (wav.length < 44
                || !"RIFF".equals(new String(wav, 0, 4, StandardCharsets.US_ASCII))
                || !"WAVE".equals(new String(wav, 8, 4, StandardCharsets.US_ASCII))) {
            throw new RuntimeException("仅支持 WAV 格式音频");
        }
        ByteBuffer buffer = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN);
        int pos = 12;
        int sampleRate = -1;
        int byteRate = -1;
        int dataOffset = -1;
        long dataSize = -1;
        while (pos + 8 <= wav.length) {
            String chunkId = new String(wav, pos, 4, StandardCharsets.US_ASCII);
            int chunkSize = buffer.getInt(pos + 4);
            if (chunkSize < 0) {
                break;
            }
            if ("fmt ".equals(chunkId) && pos + 8 + 12 <= wav.length) {
                sampleRate = buffer.getInt(pos + 8 + 4);
                byteRate = buffer.getInt(pos + 8 + 8);
            } else if ("data".equals(chunkId)) {
                dataOffset = pos + 8;
                dataSize = Math.min(chunkSize, wav.length - pos - 8L);
            }
            if (byteRate > 0 && dataSize >= 0) {
                break;
            }
            pos += 8 + chunkSize + (chunkSize & 1);
        }
        if (sampleRate <= 0 || byteRate <= 0 || dataSize < 0) {
            throw new RuntimeException("无法解析 WAV 音频头");
        }
        return new WavInfo(sampleRate, byteRate, dataOffset, dataSize);
    }

    /**
     * 静音强制兜底（档 1）：分帧（30ms）算 RMS 能量，语音帧占比低于 ASR_MIN_VOICE_RATIO
     * 判定为静音录音并拒绝——生成式 ASR 对静音会输出训练先验的幻觉文本（如「语音识别」等）。
     * 前端 pcmRecorder 有同逻辑预检（阈值更保守，只拦明显静音），本层是信任边界上的权威判定，
     * 灵敏度以 ASR_SILENCE_DB / ASR_MIN_VOICE_RATIO 两个参数为准。
     * 档 3 升级位：将来前端判定层可整体替换为 Silero VAD，本层逻辑与接口不变。
     */
    private void ensureVoicePresent(byte[] wav, WavInfo info) {
        int silenceDb = Integer.parseInt(paramsAdapter.getValue("ASR_SILENCE_DB"));
        int minVoiceRatio = Integer.parseInt(paramsAdapter.getValue("ASR_MIN_VOICE_RATIO"));

        ByteBuffer buffer = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN);
        int totalSamples = (int) (info.dataSize() / 2);
        int frameSamples = Math.max(1, info.sampleRate() * 30 / 1000);
        int frameCount = totalSamples / frameSamples;
        if (frameCount == 0) {
            throw new RuntimeException("未检测到语音内容");
        }

        int voiceFrames = 0;
        for (int i = 0; i < frameCount; i++) {
            long sumSquares = 0;
            int base = info.dataOffset() + i * frameSamples * 2;
            for (int j = 0; j < frameSamples; j++) {
                short sample = buffer.getShort(base + j * 2);
                sumSquares += (long) sample * sample;
            }
            double rms = Math.sqrt((double) sumSquares / frameSamples);
            double db = 20 * Math.log10(rms / 32768.0 + 1e-10);
            if (db > silenceDb) {
                voiceFrames++;
            }
        }
        if (voiceFrames * 100.0 / frameCount < minVoiceRatio) {
            throw new RuntimeException("未检测到语音内容，请靠近麦克风重试");
        }
    }
}
