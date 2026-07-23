package com.hxh.apboa.engine.skill;

import com.hxh.apboa.common.annotation.Scope;
import com.hxh.apboa.common.consts.RedisChannelTopic;
import com.hxh.apboa.common.enums.ScopeType;
import com.hxh.apboa.common.util.RedisUtils;
import com.hxh.apboa.common.wrapper.SkillConfigWrapper;
import com.hxh.apboa.skill.service.SkillPackageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 描述：内置技能包同步到数据库（对齐 HooksSyncToDatabase）。
 *
 * <p>启动时遍历 {@link SkillsRegister} 中自注册的内置 Skill，携带 @Scope 信息构造
 * {@link SkillConfigWrapper}，交由 {@code SkillPackageService.syncBuiltinSkills} 按租户作用域
 * 登记到 skill_package（skill_type=BUILTIN，class_path 指向实现类），并清理代码中已移除的内置技能。
 *
 * @author huxuehao
 **/
@Slf4j
@Component
@RequiredArgsConstructor
public class SkillsSyncToDatabase implements ApplicationRunner {
    private final SkillPackageService skillPackageService;
    private final RedisUtils redisUtils;

    @Override
    public void run(ApplicationArguments args) {
        String lockValue = UUID.randomUUID().toString();
        if (!redisUtils.tryLock(RedisChannelTopic.LOCK_SKILLS_SYNC, lockValue, 60, TimeUnit.SECONDS)) {
            log.info("其他节点正在执行内置技能同步，本节点跳过");
            return;
        }
        try {
            log.info("Builtin skills sync to DB starting");
            ArrayList<SkillConfigWrapper> wrappers = new ArrayList<>();
            SkillsRegister.getSkills().forEach(skill -> {
                // 携带 @Scope 注解信息
                Scope scope = skill.getScope();
                SkillConfigWrapper.SkillConfigWrapperBuilder builder = SkillConfigWrapper.builder()
                        .name(skill.getName())
                        .description(skill.getDescription())
                        .skillContent(skill.getAgentSkill().getSkillContent())
                        .classPath(skill.getClass().getName());
                if (scope != null) {
                    builder.scopeType(scope.value());
                    builder.tenantCodes(Arrays.asList(scope.tenantCodes()));
                } else {
                    // 未标注 @Scope 默认为 GLOBAL
                    builder.scopeType(ScopeType.GLOBAL);
                }
                wrappers.add(builder.build());
            });

            skillPackageService.syncBuiltinSkills(wrappers);
            log.info("Builtin skills sync to DB completed");
        } finally {
            redisUtils.unlock(RedisChannelTopic.LOCK_SKILLS_SYNC, lockValue);
        }
    }
}
