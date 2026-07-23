package com.hxh.apboa.engine.skill;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 描述：内置技能注册类，用于注册各内置 Skill 实现（对齐 HooksRegister）。
 *
 * <p>内置 Skill 类实现 {@link IBuiltinSkill} 后经 afterSingletonsInstantiated 自注册进来，
 * key 为类全名（= skill_package.class_path），供 {@code SkillBoxFactory} 按 class_path 反查
 * 构造 {@code AgentSkill}，以及 {@code SkillsSyncToDatabase} 启动时登记到 DB。
 *
 * @author huxuehao
 **/
public class SkillsRegister {
    private static final ConcurrentHashMap<String, IBuiltinSkill> skills = new ConcurrentHashMap<>();

    public static void register(String classPath, IBuiltinSkill skill) {
        skills.put(classPath, skill);
    }

    public static IBuiltinSkill getSkill(String classPath) {
        return skills.get(classPath);
    }

    public static List<IBuiltinSkill> getSkills() {
        return skills.values().stream().toList();
    }

    public static void unregister(String classPath) {
        skills.remove(classPath);
    }

    public static void clear() {
        skills.clear();
    }
}
