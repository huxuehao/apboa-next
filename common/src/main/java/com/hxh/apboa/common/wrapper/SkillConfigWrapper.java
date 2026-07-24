package com.hxh.apboa.common.wrapper;

import com.hxh.apboa.common.enums.ScopeType;
import lombok.*;

import java.util.List;

/**
 * 描述：内置技能包配置包装类（对齐 HookConfigWrapper）
 *
 * @author huxuehao
 **/
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillConfigWrapper {
    String name;
    String description;
    String classPath;

    /**
     * 内置技能包的 SKILL.md 正文（来自实现类 buildSkillContent），供启动同步落入 skill_file
     */
    String skillContent;

    /**
     * 作用域类型（null 视为 GLOBAL）
     */
    ScopeType scopeType;

    /**
     * 目标租户编码列表（仅在 scopeType = TENANT 时生效）
     */
    List<String> tenantCodes;
}
