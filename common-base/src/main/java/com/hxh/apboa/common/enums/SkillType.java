package com.hxh.apboa.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 技能包类型
 *
 * @author huxuehao
 */
@Getter
@AllArgsConstructor
public enum SkillType {
    BUILTIN("内置"),
    CUSTOM("自定义");

    private final String description;
}
