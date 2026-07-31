package com.hxh.apboa.dashboard.dataset.guard;

import com.hxh.apboa.common.consts.TableConst;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 描述：系统敏感表硬黑名单。无论白名单如何配置，凭证/密钥、账号与租户治理、
 * 各类连接与集成配置（含库/MQ/缓存/渠道等密码）、网关、Dashboard 元数据，
 * 以及数据库系统库一律禁止被数据集 SQL 引用。
 *
 * @author huxuehao
 **/
@Component
public class SystemTableBlacklistGuard implements SqlGuard {
    /** 敏感表清单（凭证、租户治理、连接/集成配置、网关、Dashboard 元数据） */
    private static final Set<String> BLOCKED_TABLES = Stream.of(
            // 账号与租户治理
            TableConst.ACCOUNT,
            TableConst.ACCOUNT_TENANT,
            TableConst.TENANT,
            TableConst.TENANT_JOIN_REQUEST,
            // 凭证与密钥
            TableConst.SECRET_KEY,
            TableConst.SKILL_TOKEN,
            TableConst.AGENT_CHAT_KEY,
            TableConst.AGENT_SCOPE_SESSIONS,
            // 模型/集成配置（含 API Key、鉴权信息）
            TableConst.PROVIDER,
            TableConst.MODEL,
            TableConst.MCP,
            TableConst.MCP_TOOL,
            TableConst.KNOWLEDGE,
            TableConst.HOOK,
            TableConst.STORAGE,
            TableConst.PLUGIN,
            TableConst.LONG_TERM_MEMORY_CONFIG,
            TableConst.CODE_EXECUTION_CONFIG,
            // 连接配置（含库/消息/缓存/渠道等凭证）
            TableConst.DATASOURCE,
            TableConst.MQ,
            TableConst.CACHE,
            TableConst.CHANNEL,
            TableConst.WORKFLOW_DATASOURCE,
            TableConst.WORKFLOW_MQ,
            TableConst.WORKFLOW_CACHE,
            TableConst.WORKFLOW_CHANNEL,
            TableConst.WORKFLOW_PLUGIN,
            // 网关（含 appKey/secret 与访问日志）
            TableConst.GATEWAY_APP,
            TableConst.GATEWAY_API,
            TableConst.GATEWAY_API_WORKFLOW,
            TableConst.GATEWAY_ACCESS_LOG,
            // Dashboard 元数据
            TableConst.DASHBOARD,
            TableConst.DASHBOARD_USER,
            TableConst.DASHBOARD_DATASET,
            TableConst.DASHBOARD_HISTORY
    ).map(SystemTableBlacklistGuard::normalize).collect(Collectors.toUnmodifiableSet());

    /** 数据库系统库 schema 前缀 */
    private static final Set<String> BLOCKED_SCHEMAS = Set.of(
            "mysql", "information_schema", "performance_schema", "sys"
    );

    /** 归一化：去反引号、去首尾空白、转小写（TableConst 个别常量值带尾部空格） */
    private static String normalize(String name) {
        return name == null ? "" : name.replace("`", "").trim().toLowerCase(Locale.ROOT);
    }

    @Override
    public void check(SqlGuardContext context) {
        for (String table : context.getTables()) {
            String lower = normalize(table);
            String schema = null;
            String pureName = lower;
            int dot = lower.lastIndexOf('.');
            if (dot > 0) {
                schema = lower.substring(0, dot);
                pureName = lower.substring(dot + 1);
            }
            if (schema != null && BLOCKED_SCHEMAS.contains(schema)) {
                throw new DatasetSecurityException("禁止访问系统库对象: " + table);
            }
            if (BLOCKED_TABLES.contains(pureName)) {
                throw new DatasetSecurityException("禁止访问系统敏感表: " + table);
            }
        }
    }

    @Override
    public int getOrder() {
        return 15;
    }
}
