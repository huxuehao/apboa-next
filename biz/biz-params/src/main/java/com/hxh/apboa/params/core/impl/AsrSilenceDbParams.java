package com.hxh.apboa.params.core.impl;

import com.hxh.apboa.common.consts.SysConst;
import com.hxh.apboa.common.util.FuncUtils;
import com.hxh.apboa.params.core.ParamsAdapter;
import com.hxh.apboa.params.core.ParamsCore;
import org.springframework.stereotype.Component;

/**
 * 描述：语音帧能量门限（dBFS，-90~0），麦克风环境噪声特殊时调节静音判定灵敏度
 *
 * @author huxuehao
 **/
@Component
public class AsrSilenceDbParams implements ParamsCore {
    @Override
    public String checkAndFormatValue(String value) {
        if (FuncUtils.isEmpty(value)) {
            throw new RuntimeException("value值不可为空");
        }
        String trim = value.trim();
        try {
            int db = Integer.parseInt(trim);
            if (db < -90 || db > 0) {
                throw new NumberFormatException("取值范围 -90 ~ 0");
            }
        } catch (NumberFormatException e) {
            throw new RuntimeException("系统参数ASR_SILENCE_DB不合法（-90~0 的整数）", e);
        }
        return trim;
    }

    @Override
    public String getDefaultValue() {
        return SysConst.ASR_SILENCE_DB;
    }

    @Override
    public void register(ParamsAdapter adapter) {
        adapter.register("ASR_SILENCE_DB", new AsrSilenceDbParams());
    }
}
