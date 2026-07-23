package com.hxh.apboa.common.config.mybatis;

import com.hxh.apboa.common.consts.TableConst;
import com.hxh.apboa.common.util.TenantUtils;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

/**
 * 描述：MyBatisPlus 配置
 *
 * @author huxuehao
 **/
@Configuration
public class MybatisPlusConfig {

    private static final Set<String> IGNORE_TABLES = Set.of(
            TableConst.TENANT,         // 租户元数据表（B类）
            TableConst.ACCOUNT,        // 账号表（B类，跨租户共享）
            TableConst.ACCOUNT_TENANT,  // 账号-租户关联表（自管理）
            TableConst.AGENT_SCOPE_SESSIONS, // 会话表（B类，跨租户共享）由agentscope提供
            TableConst.SKILL_TOKEN,    // 内部服务鉴权 token（跨服务共享）
            TableConst.AGENT_CHAT_KEY, // 会话密钥表（B类，跨租户共享）
            TableConst.IDENTITY_SIGNING_KEY, // 身份断言签名密钥（全局资源，无 tenant_id 列）
            TableConst.JOB_RECORD      // 任务记录关联表（job_id↔record_id，无 tenant_id 列）
    );

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 添加租户隔离拦截器（最先执行）
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantLineHandler() {
            @Override
            public Expression getTenantId() {
                Long tenantId = TenantUtils.getCurrentTenantId();
                if (tenantId == null) {
                    return null;
                }
                return new LongValue(tenantId);
            }

            @Override
            public String getTenantIdColumn() {
                return "tenant_id";
            }

            @Override
            public boolean ignoreTable(String tableName) {
                // B类表不添加租户过滤
                return IGNORE_TABLES.contains(tableName.toLowerCase());
            }
        }));

        // 添加分页拦截器
        interceptor.addInnerInterceptor(InterceptorFactory.createPaginationInnerInterceptor());
        return interceptor;
    }
}
