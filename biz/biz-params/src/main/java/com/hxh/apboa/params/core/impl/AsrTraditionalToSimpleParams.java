package com.hxh.apboa.params.core.impl;

import com.hxh.apboa.common.consts.SysConst;
import com.hxh.apboa.common.util.FuncUtils;
import com.hxh.apboa.params.core.ParamsAdapter;
import com.hxh.apboa.params.core.ParamsCore;
import org.springframework.stereotype.Component;

/**
 * 描述：语音识别输出繁体转简体开关（true/false），粤语等需要繁体输出的场景可关闭
 *
 * @author huxuehao
 **/
@Component
public class AsrTraditionalToSimpleParams implements ParamsCore {
    @Override
    public String checkAndFormatValue(String value) {
        if (FuncUtils.isEmpty(value)) {
            throw new RuntimeException("value值不可为空");
        }
        String trim = value.trim().toLowerCase();
        if (!"true".equals(trim) && !"false".equals(trim)) {
            throw new RuntimeException("系统参数ASR_TRADITIONAL_TO_SIMPLE不合法，仅支持 true/false");
        }
        return trim;
    }

    @Override
    public String getDefaultValue() {
        return SysConst.ASR_TRADITIONAL_TO_SIMPLE;
    }

    @Override
    public void register(ParamsAdapter adapter) {
        adapter.register("ASR_TRADITIONAL_TO_SIMPLE", new AsrTraditionalToSimpleParams());
    }
}
