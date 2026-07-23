package com.hxh.apboa.engine.skill;

import com.hxh.apboa.common.annotation.Scope;
import com.hxh.apboa.common.enums.ScopeType;
import io.agentscope.core.skill.AgentSkill;
import org.springframework.beans.factory.SmartInitializingSingleton;

/**
 * 描述：内置技能接口（对齐 IAgentHook）。
 *
 * <p>内置 Skill 实现类 {@code @Component} 后经 {@link #afterSingletonsInstantiated} 自注册到
 * {@link SkillsRegister}（key = 类全名 = skill_package.class_path）。启动时
 * {@code SkillsSyncToDatabase} 遍历注册表登记内置技能包到 DB，运行时 {@code SkillBoxFactory}
 * 按 class_path 反查并 {@link #getAgentSkill()} 构造 AgentSkill 注册到 SkillBox。
 *
 * @author huxuehao
 **/
public interface IBuiltinSkill extends SmartInitializingSingleton {

    /**
     * 构造该内置技能对应的 AgentSkill（运行时注册到 SkillBox）
     */
    AgentSkill getAgentSkill();

    default String getName() {
        return getAgentSkill().getName();
    }

    default String getDescription() {
        return getAgentSkill().getDescription();
    }

    /**
     * 获取内置技能的作用域配置，未标注 @Scope 时返回 null（视为 GLOBAL）
     */
    default Scope getScope() {
        Class<?> clazz = this.getClass();
        if (clazz.isAnnotationPresent(Scope.class)) {
            return clazz.getAnnotation(Scope.class);
        }
        return null;
    }

    /**
     * 获取内置技能的实际作用域类型，未标注 @Scope 时默认为 GLOBAL
     */
    default ScopeType getScopeType() {
        Scope scope = getScope();
        return scope != null ? scope.value() : ScopeType.GLOBAL;
    }

    default void afterSingletonsInstantiated() {
        SkillsRegister.register(this.getClass().getName(), this);
    }
}
