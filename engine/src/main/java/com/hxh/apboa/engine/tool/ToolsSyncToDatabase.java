package com.hxh.apboa.engine.tool;

import com.hxh.apboa.common.annotation.Scope;
import com.hxh.apboa.common.consts.RedisChannelTopic;
import com.hxh.apboa.common.enums.ScopeType;
import com.hxh.apboa.common.util.RedisUtils;
import com.hxh.apboa.common.wrapper.ToolInfoWrapper;
import com.hxh.apboa.tool.service.ToolService;
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
 * 描述：内置工具同步到数据库
 *
 * @author huxuehao
 **/
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "runtime", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ToolsSyncToDatabase implements ApplicationRunner {
    private final ToolService toolService;
    private final RedisUtils redisUtils;
    @Override
    public void run(ApplicationArguments args) {
        String lockValue = UUID.randomUUID().toString();
        if (!redisUtils.tryLock(RedisChannelTopic.LOCK_TOOLS_SYNC, lockValue, 60, TimeUnit.SECONDS)) {
            log.info("其他节点正在执行内置工具同步，本节点跳过");
            return;
        }
        try {
            log.info("IAgentTool sync to DB starting");
            ArrayList<ToolInfoWrapper> toolInfoWrappers = new ArrayList<>();
            ToolsRegister.getTools().forEach(tool -> {
                ToolInfoWrapper wrapper = tool.parseToolInfo();
                if (wrapper != null) {
                    // 携带 @Scope 注解信息
                    Scope scope = tool.getScope();
                    if (scope != null) {
                        wrapper.setScopeType(scope.value());
                        wrapper.setTenantCodes(Arrays.asList(scope.tenantCodes()));
                    } else {
                        // 未标注 @Scope 默认为 GLOBAL
                        wrapper.setScopeType(ScopeType.GLOBAL);
                    }
                    toolInfoWrappers.add(wrapper);
                }
            });

            toolService.SyncConfigToDatabase(toolInfoWrappers);
            log.info("IAgentTool sync to DB completed");
        } finally {
            redisUtils.unlock(RedisChannelTopic.LOCK_TOOLS_SYNC, lockValue);
        }
    }
}
