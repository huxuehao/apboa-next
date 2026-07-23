package com.hxh.apboa.scheduler.scheduler;

import com.hxh.apboa.agent.service.ChatSessionService;
import com.hxh.apboa.common.consts.SysConst;
import com.hxh.apboa.common.dto.ChatSessionCreateDTO;
import com.hxh.apboa.common.enums.AgentType;
import com.hxh.apboa.common.util.AgentMetadataStore;
import com.hxh.apboa.common.util.CryptoUtils;
import com.hxh.apboa.common.util.TenantUtils;
import com.hxh.apboa.common.vo.AccountVO;
import com.hxh.apboa.common.vo.ChatSessionVO;
import com.hxh.apboa.common.wrapper.AgentJobWrapper;
import com.hxh.apboa.engine.agent.AgentBuilderWrapper;
import com.hxh.apboa.engine.agent.IAgentFactory;
import com.hxh.apboa.engine.agui.AgentContext;
import com.hxh.apboa.engine.log.telemetry.ChatChannelHolder;
import com.hxh.apboa.scheduler.consts.JobConst;
import com.hxh.apboa.scheduler.core.enums.QuartzEnum;
import com.hxh.apboa.scheduler.core.job.QuartzJob;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.Agent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.tool.ToolExecutionContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.quartz.JobExecutionContext;

import java.util.Optional;

/**
 * 智能体任务执行器
 *
 * @author huxuehao
 */
@Slf4j
public class AgentScheduler extends QuartzJob {

    private static final int MAX_TITLE_LENGTH = 50;

    @Override
    public Object doJob(JobExecutionContext context) {
        Long tenantId = getDataMap(JobConst.TENANT_ID_KEY, Long.class);
        String tenantCode = getDataMap(JobConst.TENANT_CODE_KEY, String.class);
        AgentJobWrapper wrapper = getDataMap(JobConst.DATA_MAP_KEY, AgentJobWrapper.class);
        AccountVO userInfo = getDataMap(JobConst.USER_INFO_KEY, AccountVO.class);

        // 参数校验（失败原因写入 RUN_MSG，随 job_log 落库）
        String invalidReason = validateJobParameters(wrapper, tenantId, userInfo);
        if (invalidReason != null) {
            log.warn("Agent job parameter invalid: {}", invalidReason);
            putRunMsg("参数校验失败：" + invalidReason);
            return false;
        }

        String agentId = wrapper.getBizId().trim();
        try {
            TenantUtils.setCurrentTenant(tenantId, null);
            // 2. 创建会话
            ChatSessionVO session = createChatSession(agentId, wrapper.getUserPrompt(), userInfo);
            if (session == null) {
                log.error("Failed to create chat session for agent: {}", agentId);
                putRunMsg("创建会话失败，agentId=" + agentId + "，详见服务日志");
                return false;
            }

            // 成本流水归因：登记会话触发渠道为定时调度（ChatLogHook 落 meta 时按 threadId 读取；
            // 用户后续在同一会话追聊会以其自身渠道覆盖，任务消耗与追聊消耗可区分）
            ChatChannelHolder.put(String.valueOf(session.getId()), SysConst.CHANNEL_SCHEDULED);

            // 初始化智能体上下文
            AgentContext agentContext = new AgentContext();
            agentContext.setTenantId(tenantId);
            agentContext.setThreadId(String.valueOf(session.getId()));
            agentContext.setRunId(CryptoUtils.uuid());
            agentContext.setUserInfo(userInfo);
            AgentContext.init(agentContext);

            // 1. 获取智能体构建器
            AgentBuilderWrapper agentBuilder = getAgentBuilder(agentId, tenantId);
            if (agentBuilder == null) {
                putRunMsg("获取智能体构建器失败，agentId=" + agentId + "，详见服务日志");
                return false;
            }

            // 3. 构建并执行智能体
            Agent agent = buildAgent(agentBuilder);
            Long jobId = getDataMap(QuartzEnum.IDENTITY_KEY.value(), Long.class);
            executeAgent(agent, wrapper, session.getId(), tenantId, tenantCode, jobId);

            log.info("Agent job executed successfully, agentId: {}, sessionId: {}", agentId, session.getId());
            putRunMsg("执行成功，sessionId=" + session.getId());
            return true;

        } catch (Exception e) {
            log.error("Failed to execute agent job, agentId: {}, error: {}", agentId, e.getMessage(), e);
            putRunMsg("执行异常：" + e.getMessage());
            return false;
        } finally {
            TenantUtils.setCurrentTenant(tenantId, null);
        }
    }

    /**
     * 校验任务参数
     * @return null 表示通过；否则为失败原因
     */
    private String validateJobParameters(AgentJobWrapper wrapper, Long tenantId, AccountVO userInfo) {
        if (wrapper == null) {
            return "任务执行参数（dataMap）为空或格式不兼容";
        }
        if (tenantId == null) {
            return "租户信息缺失";
        }
        if (userInfo == null) {
            return "创建人信息缺失";
        }
        if (StringUtils.isBlank(wrapper.getBizId())) {
            return "关联智能体ID为空";
        }
        if (wrapper.getInputs() == null || !wrapper.getInputs().containsKey("userPrompt")) {
            return "inputs 缺少 userPrompt（发送给智能体的消息内容）";
        }
        return null;
    }

    /**
     * 获取智能体构建器
     */
    private AgentBuilderWrapper getAgentBuilder(String agentId, Long tenantId) {
        try {
            IAgentFactory agentFactory = getBean(IAgentFactory.class);
            AgentBuilderWrapper builder = agentFactory.getAgentBuilder(Long.valueOf(agentId), tenantId);

            if (builder == null) {
                log.warn("AgentBuilderWrapper is null for agentId: {}", agentId);
                return null;
            }

            if (builder.getDefinition() == null) {
                log.warn("AgentDefinition is null for agentId: {}", agentId);
                return null;
            }

            return builder;
        } catch (NumberFormatException e) {
            log.error("Invalid agentId format: {}", agentId, e);
            return null;
        }
    }

    /**
     * 创建聊天会话
     */
    private ChatSessionVO createChatSession(String agentId, String userPrompt, AccountVO userInfo) {
        try {
            ChatSessionService chatSessionService = getBean(ChatSessionService.class);

            ChatSessionCreateDTO createDTO = new ChatSessionCreateDTO();
            createDTO.setAgentId(Long.valueOf(agentId));
            createDTO.setTitle(generateSessionTitle(userPrompt));

            return chatSessionService.createSession(createDTO, userInfo.getId());
        } catch (NumberFormatException e) {
            log.error("Invalid agentId format when creating session: {}", agentId, e);
            return null;
        } catch (Exception e) {
            log.error("Failed to create chat session for agent: {}", agentId, e);
            return null;
        }
    }

    /**
     * 生成会话标题
     */
    private String generateSessionTitle(String userPrompt) {
        return Optional.ofNullable(userPrompt)
                .map(prompt -> prompt.length() > MAX_TITLE_LENGTH
                        ? prompt.substring(0, MAX_TITLE_LENGTH) + "..."
                        : prompt)
                .orElse("新会话");
    }

    /**
     * 构建智能体
     */
    private Agent buildAgent(AgentBuilderWrapper builder) {
        if (builder.getDefinition().getAgentType() == AgentType.A2A) {
            return builder.getA2aAgentBuilder().build();
        }

        // ReActAgent构建
        ReActAgent.Builder reactBuilder = builder.getReactAgentBuilder();

        // 创建并注册执行上下文
        AgentContext context = AgentContext.get();
        context.setAgentDefinition(builder.getDefinition());

        ToolExecutionContext toolContext = ToolExecutionContext.builder()
                .register(context)
                .build();
        reactBuilder.toolExecutionContext(toolContext);

        return reactBuilder.build();
    }

    /**
     * 执行智能体
     */
    private void executeAgent(Agent agent, AgentJobWrapper wrapper, Long sessionId, Long tenantId, String tenantCode, Long jobId) {
        String agentId = agent.getAgentId();
        String userPrompt = wrapper.getInputs().get("userPrompt").toString();

        // 设置元数据
        try {
            AgentMetadataStore.put(agentId, "tenantId", tenantId);
            AgentMetadataStore.put(agentId, "tenantCode", tenantCode);
            AgentMetadataStore.put(agentId, "threadId", String.valueOf(sessionId));
            AgentMetadataStore.put(agentId, "toolProcessActive", false);
            AgentMetadataStore.put(agentId, "cleanUpOnOwn", true);
            // 成本流水场景标：ChatLogHook 落 meta 后 writeChatRun 检出，主链行
            // 记为 SCHEDULED_JOB 场景并携带任务归因（biz_id=任务ID、biz_label=任务名）
            AgentMetadataStore.put(agentId, "scheduledJobId", jobId);
            AgentMetadataStore.put(agentId, "scheduledJobName", wrapper.getJobName());

            // 执行调用
            agent.call(Msg.builder().textContent(userPrompt).build())
                    .block();
        } finally {
            // 4. 记录关联关系
            setRecordRelation(sessionId);
            // 确保清理元数据
            AgentMetadataStore.removeOnOwn(agentId);
        }
    }
}
