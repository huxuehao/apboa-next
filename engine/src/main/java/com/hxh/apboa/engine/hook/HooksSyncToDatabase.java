package com.hxh.apboa.engine.hook;

import com.hxh.apboa.common.annotation.Scope;
import com.hxh.apboa.common.consts.RedisChannelTopic;
import com.hxh.apboa.common.enums.ScopeType;
import com.hxh.apboa.common.util.RedisUtils;
import com.hxh.apboa.common.wrapper.HookConfigWrapper;
import com.hxh.apboa.hook.service.HookConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 描述：内置钩子同步到数据库
 *
 * @author huxuehao
 **/
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "runtime", name = "enabled", havingValue = "true", matchIfMissing = true)
public class HooksSyncToDatabase implements ApplicationRunner {
    private final HookConfigService hookConfigService;
    private final RedisUtils redisUtils;
    @Override
    public void run(ApplicationArguments args) {
        String lockValue = UUID.randomUUID().toString();
        if (!redisUtils.tryLock(RedisChannelTopic.LOCK_HOOKS_SYNC, lockValue, 60, TimeUnit.SECONDS)) {
            log.info("其他节点正在执行内置Hook同步，本节点跳过");
            return;
        }
        try {
            log.info("IAgentHooks sync to DB starting");
            ArrayList<HookConfigWrapper> hookConfigWrappers = new ArrayList<>();
            HooksRegister.getHooks().forEach(hook -> {
                IAgentHook agentHook = (IAgentHook) hook;
                // 携带 @Scope 注解信息
                Scope scope = agentHook.getScope();
                HookConfigWrapper.HookConfigWrapperBuilder builder = HookConfigWrapper.builder()
                        .name(agentHook.getName())
                        .description(agentHook.getDescription())
                        .classPath(hook.getClass().getName());
                if (scope != null) {
                    builder.scopeType(scope.value());
                    builder.tenantCodes(Arrays.asList(scope.tenantCodes()));
                } else {
                    // 未标注 @Scope 默认为 GLOBAL
                    builder.scopeType(ScopeType.GLOBAL);
                }
                hookConfigWrappers.add(builder.build());
            });

            hookConfigService.SyncConfigToDatabase(hookConfigWrappers);
            log.info("IAgentHooks sync to DB completed");
        } finally {
            redisUtils.unlock(RedisChannelTopic.LOCK_HOOKS_SYNC, lockValue);
        }
    }
}
