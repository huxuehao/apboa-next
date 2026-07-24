package com.hxh.apboa.common.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hxh.apboa.common.consts.TableConst;
import com.hxh.apboa.common.enums.ScopeType;
import com.hxh.apboa.common.enums.SkillType;
import lombok.Getter;
import lombok.Setter;

/**
 * 技能包
 *
 * @author huxuehao
 */
@Getter
@Setter
@TableName(value = TableConst.SKILL)
public class SkillPackage extends BaseTenantEntity {

    /**
     * 技能包名称
     */
    private String name;

    /**
     * 展示别名（仅展示层用，不影响 name 与发送给 agent 的值）
     */
    private String alias;

    /**
     * 技能描述
     */
    private String description;

    /**
     * 技能分类
     */
    private String category;

    /**
     * 技能类型: 内置/自定义
     */
    private SkillType skillType;

    /**
     * 技能类路径（skill_type 为 BUILTIN 时使用，指向内置 Skill 实现类，供 SkillsRegister 反查）
     */
    private String classPath;

    /**
     * 作用域类型: GLOBAL=全局, TENANT=指定租户（仅内置技能包有意义）
     */
    private ScopeType scopeType;
}
