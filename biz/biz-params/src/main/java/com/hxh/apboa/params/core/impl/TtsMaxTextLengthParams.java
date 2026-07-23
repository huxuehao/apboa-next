package com.hxh.apboa.params.core.impl;

import com.hxh.apboa.common.consts.SysConst;
import com.hxh.apboa.common.util.FuncUtils;
import com.hxh.apboa.params.core.ParamsAdapter;
import com.hxh.apboa.params.core.ParamsCore;
import org.springframework.stereotype.Component;

/**
 * 描述：语音合成单次最大字符数
 *
 * @author huxuehao
 **/
@Component
public class TtsMaxTextLengthParams implements ParamsCore {
    @Override
    public String checkAndFormatValue(String value) {
        if (FuncUtils.isEmpty(value)) {
            throw new RuntimeException("value值不可为空");
        }
        String trim = value.trim();
        try {
            int length = Integer.parseInt(trim);
            if (length <= 0) {
                throw new NumberFormatException("必须为正整数");
            }
        } catch (NumberFormatException e) {
            throw new RuntimeException("系统参数TTS_MAX_TEXT_LENGTH不合法", e);
        }
        return trim;
    }

    @Override
    public String getDefaultValue() {
        return SysConst.TTS_MAX_TEXT_LENGTH;
    }

    @Override
    public void register(ParamsAdapter adapter) {
        adapter.register("TTS_MAX_TEXT_LENGTH", new TtsMaxTextLengthParams());
    }
}
