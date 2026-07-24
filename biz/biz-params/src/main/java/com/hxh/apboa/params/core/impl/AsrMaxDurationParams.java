package com.hxh.apboa.params.core.impl;

import com.hxh.apboa.common.consts.SysConst;
import com.hxh.apboa.common.util.FuncUtils;
import com.hxh.apboa.params.core.ParamsAdapter;
import com.hxh.apboa.params.core.ParamsCore;
import org.springframework.stereotype.Component;

/**
 * 描述：语音输入单次最长时长（秒）
 *
 * @author huxuehao
 **/
@Component
public class AsrMaxDurationParams implements ParamsCore {
    @Override
    public String checkAndFormatValue(String value) {
        if (FuncUtils.isEmpty(value)) {
            throw new RuntimeException("value值不可为空");
        }
        String trim = value.trim();
        try {
            int seconds = Integer.parseInt(trim);
            if (seconds <= 0) {
                throw new NumberFormatException("必须为正整数");
            }
        } catch (NumberFormatException e) {
            throw new RuntimeException("系统参数ASR_MAX_DURATION不合法", e);
        }
        return trim;
    }

    @Override
    public String getDefaultValue() {
        return SysConst.ASR_MAX_DURATION;
    }

    @Override
    public void register(ParamsAdapter adapter) {
        adapter.register("ASR_MAX_DURATION", new AsrMaxDurationParams());
    }
}
