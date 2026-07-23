package com.hxh.apboa.runtime.tts;

import com.hxh.apboa.agent.service.AgentDefinitionService;
import com.hxh.apboa.common.entity.AgentDefinition;
import com.hxh.apboa.common.enums.ModelCategory;
import com.hxh.apboa.common.util.FuncUtils;
import com.hxh.apboa.common.wrapper.ModelConfigWrapper;
import com.hxh.apboa.common.wrapper.ModelWrapper;
import com.hxh.apboa.engine.tts.TtsProviderHolder;
import com.hxh.apboa.engine.tts.TtsResult;
import com.hxh.apboa.model.service.ModelConfigService;
import com.hxh.apboa.params.core.ParamsAdapter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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
    private final TtsProviderHolder ttsProviderHolder;
    private final ParamsAdapter paramsAdapter;

    /**
     * 单段合成：校验智能体绑定与文本长度，路由到对应供应商实现
     */
    public TtsResult speak(Long agentId, String text) {
        if (FuncUtils.isEmpty(text) || text.isBlank()) {
            throw new RuntimeException("合成文本为空");
        }
        // 长度限制是计费与滥用的闸门（会话内按句切段后单段远小于该值）
        int maxLength = Integer.parseInt(paramsAdapter.getValue("TTS_MAX_TEXT_LENGTH"));
        if (text.length() > maxLength) {
            throw new RuntimeException("合成文本超出限制（最长 " + maxLength + " 字符）");
        }

        ModelConfigWrapper configWrapper = resolveConfigWrapper(agentId);
        return ttsProviderHolder.get(configWrapper.getProvider()).synthesize(configWrapper, text.trim());
    }

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
        return configWrapper;
    }
}
