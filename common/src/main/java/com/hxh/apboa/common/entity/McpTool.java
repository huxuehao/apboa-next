package com.hxh.apboa.common.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.JsonNode;
import com.hxh.apboa.common.config.mybatis.JsonNodeTypeHandler;
import com.hxh.apboa.common.consts.TableConst;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * MCP 工具目录
 *
 * @author huxuehao
 */
@Getter
@Setter
@TableName(value = TableConst.MCP_TOOL, autoResultMap = true)
public class McpTool extends BaseTenantEntity {

    /**
     * 所属 MCP 服务 ID
     */
    private Long mcpServerId;

    /**
     * 工具名
     */
    private String toolName;

    /**
     * 工具描述
     */
    private String description;

    /**
     * 输入 Schema
     */
    @TableField(typeHandler = JsonNodeTypeHandler.class)
    private JsonNode inputSchema;

    /**
     * 输出 Schema
     */
    @TableField(typeHandler = JsonNodeTypeHandler.class)
    private JsonNode outputSchema;

    /**
     * 原始工具 Schema
     */
    @TableField(typeHandler = JsonNodeTypeHandler.class)
    private JsonNode rawSchema;

    /**
     * Schema 摘要
     */
    private String schemaHash;

    /**
     * 是否已在当前 MCP 服务中消失
     */
    private Boolean missing;

    /**
     * 排序
     */
    private Integer sort;

    /**
     * 是否需要人工确认（HITL §6.6）。调用前由 IConfirmationHook 拦截暂停，等用户允许/拒绝。
     */
    private Boolean needConfirm;

    /**
     * 首次发现时间
     */
    private LocalDateTime lastDiscoveredAt;

    /**
     * 最近一次发现时间
     */
    private LocalDateTime lastSeenAt;
}
