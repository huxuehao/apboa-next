package com.hxh.apboa.params.core.impl;

import com.hxh.apboa.common.consts.SysConst;
import com.hxh.apboa.common.util.FuncUtils;
import com.hxh.apboa.params.core.ParamsAdapter;
import com.hxh.apboa.params.core.ParamsCore;
import org.springframework.stereotype.Component;

/**
 * 描述：最小语音帧占比（百分比整数 0~100），低于该比例的录音判定为静音、拒绝识别
 *
 * @author huxuehao
 **/
@Component
public class AsrMinVoiceRatioParams implements ParamsCore {
    @Override
    public String checkAndFormatValue(String value) {
        if (FuncUtils.isEmpty(value)) {
            throw new RuntimeException("value值不可为空");
        }
        String trim = value.trim();
        try {
            int ratio = Integer.parseInt(trim);
            if (ratio < 0 || ratio > 100) {
                throw new NumberFormatException("取值范围 0 ~ 100");
            }
        } catch (NumberFormatException e) {
            throw new RuntimeException("系统参数ASR_MIN_VOICE_RATIO不合法（0~100 的整数）", e);
        }
        return trim;
    }

    @Override
    public String getDefaultValue() {
        return SysConst.ASR_MIN_VOICE_RATIO;
    }

    @Override
    public void register(ParamsAdapter adapter) {
        adapter.register("ASR_MIN_VOICE_RATIO", new AsrMinVoiceRatioParams());
    }
}
