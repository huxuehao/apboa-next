package com.hxh.apboa.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 模型用途：一条模型配置只属于一种用途，决定其可配参数与被引用的场景。
 * 与 {@link ModelType}（LLM 的输入模态能力，多选）是正交维度。
 *
 * @author huxuehao
 */
@Getter
@AllArgsConstructor
public enum ModelCategory {
    LLM("对话生成"),
    ASR("语音识别"),
    TTS("语音合成");

    private final String description;
}
