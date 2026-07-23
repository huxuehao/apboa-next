package com.hxh.apboa.runtime.tts;

import com.hxh.apboa.agent.service.AgentDefinitionService;
import com.hxh.apboa.common.entity.AgentDefinition;
import com.hxh.apboa.common.enums.ModelCategory;
import com.hxh.apboa.common.enums.ModelProviderType;
import com.hxh.apboa.common.util.ExtendConfigHelper;
import com.hxh.apboa.common.wrapper.ModelConfigWrapper;
import com.hxh.apboa.common.wrapper.ModelWrapper;
import com.hxh.apboa.model.service.ModelConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 描述：语音合成编排——按智能体绑定的 TTS 模型装配供应商配置并分发合成。
 * 音频仅内存中转即回，不落盘不落库（参照 AsrService 模式）。
 *
 * @author huxuehao
 **/
@Service
@RequiredArgsConstructor
public class TtsService {

    private final AgentDefinitionService agentDefinitionService;
    private final ModelConfigService modelConfigService;
    private final VoiceCatalogClient voiceCatalogClient;

    /**
     * 按智能体解析语音合成的模型配置（校验 agent 启用、TTS 绑定与模型用途），
     * 单段合成与流式播报会话共用这条装配链。
     */
    public ModelConfigWrapper resolveConfigWrapper(Long agentId) {
        AgentDefinition agent = agentDefinitionService.getById(agentId);
        if (agent == null || !agent.getEnabled()) {
            throw new RuntimeException("智能体不存在或已禁用");
        }
        Long ttsModelConfigId = agent.getTtsModelConfigId();
        if (ttsModelConfigId == null) {
            throw new RuntimeException("该智能体未启用语音播报");
        }

        // getModelWrapperById 已校验模型与供应商的存在/启用，并完成租户隔离
        ModelWrapper wrapper = modelConfigService.getModelWrapperById(ttsModelConfigId);
        if (wrapper.getConfig().getCategory() != ModelCategory.TTS) {
            throw new RuntimeException("绑定的模型用途不是语音合成");
        }

        ModelConfigWrapper configWrapper = new ModelConfigWrapper();
        wrapper.getConfig().fillModelConfigWrapper(configWrapper);
        wrapper.getProvider().fillModelConfigWrapper(configWrapper);
        // agent 级音色覆盖：非空则并入 bodyParams.voice，压过模型层默认音色（DashScopeTtsProvider 再兜底 Cherry）
        ExtendConfigHelper.mergeBodyParams(configWrapper, agent.getTtsParamsOverride());
        // OPEN_AI 本地克隆 TTS：把音色名解析成 mlx-audio 真正要的 ref_audio+ref_text
        resolveOpenAiCloneVoice(configWrapper);
        return configWrapper;
    }

    /**
     * OPEN_AI 本地克隆 TTS：bodyParams.voice 存的是音色名（私有协议），合成前调 {baseUrl}/voices
     * 换成 mlx-audio 真正要的 ref_audio+ref_text（+ lang_code 固定 Chinese）。
     * DASH_SCOPE 的 voice 直接透传、不进此分支——两类 TTS 音色互不干扰。
     */
    private void resolveOpenAiCloneVoice(ModelConfigWrapper config) {
        if (config.getProvider() != ModelProviderType.OPEN_AI || config.getBodyParams() == null) {
            return;
        }
        Object voiceName = config.getBodyParams().get("voice");
        if (voiceName == null || voiceName.toString().isBlank()) {
            return;
        }
        VoiceCatalogClient.VoiceItem v = voiceCatalogClient.find(config.getBaseUrl(), voiceName.toString());
        if (v == null) {
            throw new RuntimeException("音色未找到（TTS 服务 /voices 无此音色）: " + voiceName);
        }
        Map<String, Object> merged = new HashMap<>(config.getBodyParams());
        merged.remove("voice");
        merged.put("ref_audio", v.refAudio());
        merged.put("ref_text", v.refText());
        merged.putIfAbsent("lang_code", "Chinese");
        config.setBodyParams(merged);
    }

    /**
     * 轻量取智能体绑定的 TTS 模型配置 id（供朗读缓存 key 使用；命中时无需解析整份 config）。
     * 未绑定返回 null，调用方据此决定是否走缓存。
     */
    public Long getTtsModelConfigId(Long agentId) {
        AgentDefinition agent = agentDefinitionService.getById(agentId);
        return agent == null ? null : agent.getTtsModelConfigId();
    }
}
