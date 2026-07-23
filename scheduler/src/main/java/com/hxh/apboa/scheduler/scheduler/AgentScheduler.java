package com.hxh.apboa.scheduler.scheduler;

import com.hxh.apboa.agent.service.ChatSessionService;
import com.hxh.apboa.common.consts.RedisChannelTopic;
import com.hxh.apboa.common.consts.SysConst;
import com.hxh.apboa.common.dto.ChatSessionCreateDTO;
import com.hxh.apboa.common.enums.AgentType;
import com.hxh.apboa.common.enums.ConfirmMode;
import com.hxh.apboa.common.util.AgentMetadataStore;
import com.hxh.apboa.common.util.CryptoUtils;
import com.hxh.apboa.common.util.RedisUtils;
import com.hxh.apboa.common.util.TenantUtils;
import com.hxh.apboa.common.vo.AccountVO;
import com.hxh.apboa.common.vo.ChatSessionVO;
import com.hxh.apboa.common.wrapper.AgentJobWrapper;
import com.hxh.apboa.engine.agent.AgentBuilderWrapper;
import com.hxh.apboa.engine.agent.IAgentFactory;
import com.hxh.apboa.engine.agui.AgentContext;
import com.hxh.apboa.engine.hook.builtins.IConfirmationHook;
import com.hxh.apboa.engine.log.ChatLogHook;
import com.hxh.apboa.engine.log.telemetry.ChatChannelHolder;
import com.hxh.apboa.engine.log.telemetry.MonthlyBudgetChecker;
import com.hxh.apboa.scheduler.consts.JobConst;
import com.hxh.apboa.scheduler.core.enums.QuartzEnum;
import com.hxh.apboa.scheduler.core.job.QuartzJob;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.Agent;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.GenerateReason;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.tool.ToolExecutionContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.quartz.JobExecutionContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * 智能体任务执行器
 *
 * @author huxuehao
 */
@Slf4j
public class AgentScheduler extends QuartzJob {

    private static final int MAX_TITLE_LENGTH = 50;

    /** 拒绝授权模式下注入拒绝结果续跑的轮次上限（防模型反复调用需确认工具） */
    private static final int MAX_REJECT_RESUME_ROUNDS = 3;

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

            // 月度预算熔断（与对话链路同一判定口径）：超支则不执行、不建会话
            Optional<MonthlyBudgetChecker.BudgetExceeded> exceeded = MonthlyBudgetChecker.check(
                    Long.valueOf(agentId), (JdbcTemplate) getBean(JdbcTemplate.class));
            if (exceeded.isPresent()) {
                String msg = String.format("智能体本月预算已超支（已用 ¥%.2f / 预算 ¥%.2f），本次执行取消",
                        exceeded.get().spent(), exceeded.get().budget());
                log.warn("Agent job budget exceeded, agentId: {}, {}", agentId, msg);
                putRunMsg(msg);
                return false;
            }

            // 2. 创建会话
            ChatSessionVO session = createChatSession(agentId, wrapper.getUserPrompt(), userInfo);
            if (session == null) {
                log.error("Failed to create chat session for agent: {}", agentId);
                putRunMsg("创建会话失败，agentId=" + agentId + "，详见服务日志");
                return false;
            }

            // 成本流水归因：登记会话触发渠道为定时调度（ChatLogHook 落 meta 时按 threadId 读取；
            // 用户后续在同一会话追聊会由 AguiRequestProcessor 以其认证渠道覆盖，两类消耗可区分）。
            // 刻意不在执行后清除：消息落库异步，过早清除会让末条消息渠道落空；残留仅在追聊
            // 认证渠道缺失的窄分支下沿用 SCHEDULED，影响限于渠道统计标注
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

            // 工具授权模式（任务级配置，缺省一键授权）：AUTO_APPROVE 写入会话授权 Redis
            // 让 IConfirmationHook 直接放行；AUTO_REJECT 保持暂停语义、由本类注入拒绝续跑。
            // 带 TTL 写入——进程崩溃 finally 未跑时授权自动过期，不会永久绕过该会话 HITL；
            // finally 恢复执行前原值（而非无条件删）——用户经前端开关设置的会话授权不被抹掉。
            // 已知窄窗口：执行期间（秒~分钟级）用户恰好在该新会话追聊会读到任务授权，属
            // 会话级授权键的共享语义，创建者本人低频场景可接受
            ConfirmMode confirmMode = ConfirmMode.AUTO_REJECT.name().equalsIgnoreCase(wrapper.getConfirmMode())
                    ? ConfirmMode.AUTO_REJECT : ConfirmMode.AUTO_APPROVE;
            RedisUtils redisUtils = (RedisUtils) getBean(RedisUtils.class);
            String approveKey = RedisChannelTopic.CHAT_AUTO_APPROVE_KEY_PREFIX + session.getId();
            boolean finished;
            String prevAuthValue = null;
            try {
                prevAuthValue = redisUtils.get(approveKey);
            } catch (Exception e) {
                log.warn("读取会话授权原值失败 sessionId={}: {}", session.getId(), e.getMessage());
            }
            try {
                redisUtils.setEx(approveKey, confirmMode.getRedisValue(), 30, TimeUnit.MINUTES);
                finished = executeAgent(agent, wrapper, session.getId(), tenantId, tenantCode, jobId, confirmMode);
            } finally {
                try {
                    if (prevAuthValue != null) {
                        // 前端开关口径为 30 天 TTL，恢复时对齐
                        redisUtils.setEx(approveKey, prevAuthValue, 30, TimeUnit.DAYS);
                    } else {
                        redisUtils.delete(approveKey);
                    }
                } catch (Exception e) {
                    log.warn("恢复会话授权模式失败 sessionId={}: {}", session.getId(), e.getMessage());
                }
            }
            if (!finished) {
                // 失败原因已由 executeAgent 写入 RUN_MSG
                return false;
            }

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
    /**
     * 执行智能体（含 HITL 授权处理）。
     *
     * @return true=执行完成；false=停在待确认工具上未能收尾（原因已写 RUN_MSG）
     */
    private boolean executeAgent(Agent agent, AgentJobWrapper wrapper, Long sessionId, Long tenantId,
                                 String tenantCode, Long jobId, ConfirmMode confirmMode) {
        String agentId = agent.getAgentId();
        String userPrompt = wrapper.getInputs().get("userPrompt").toString();

        // 设置元数据
        try {
            AgentMetadataStore.put(agentId, "tenantId", tenantId);
            AgentMetadataStore.put(agentId, "tenantCode", tenantCode);
            AgentMetadataStore.put(agentId, "threadId", String.valueOf(sessionId));
            // 定时执行无人值守，执行记录是事后唯一审计入口：工具过程恒落库
            // （对话链路的该开关是"用户实时观看偏好"，不适用于无人场景）
            AgentMetadataStore.put(agentId, "toolProcessActive", true);
            AgentMetadataStore.put(agentId, "cleanUpOnOwn", true);
            // 成本流水场景标：ChatLogHook 落 meta 后 writeChatRun 检出，主链行
            // 记为 SCHEDULED_JOB 场景并携带任务归因（biz_id=任务ID、biz_label=任务名）
            AgentMetadataStore.put(agentId, "scheduledJobId", jobId);
            AgentMetadataStore.put(agentId, "scheduledJobName", wrapper.getJobName());

            // 执行调用。需确认工具触发 IConfirmationHook.stopAgent 后 call 正常返回
            // （GenerateReason=REASONING_STOP_REQUESTED）：一键授权下 Hook 已放行不会暂停；
            // 拒绝授权下注入拒绝结果续跑（对齐人工全拒语义），上限防模型反复调用
            Msg response = agent.call(Msg.builder().textContent(userPrompt).build())
                    .block();
            int rounds = 0;
            while (response != null
                    && response.getGenerateReason() == GenerateReason.REASONING_STOP_REQUESTED
                    && rounds < MAX_REJECT_RESUME_ROUNDS) {
                List<Msg> resumeInput = rejectPendingTools(agent, response, sessionId);
                if (resumeInput.isEmpty()) {
                    // 暂停但无需确认工具命中（理论不可达），防死循环
                    break;
                }
                rounds++;
                response = agent.call(resumeInput).block();
            }
            if (response != null && response.getGenerateReason() == GenerateReason.REASONING_STOP_REQUESTED) {
                // 防御兜底：一键授权下不应出现暂停；拒绝授权续跑超上限也到这——如实记失败
                putRunMsg(String.format("执行未完成：智能体停在待人工确认的工具上（授权模式=%s，已续跑 %d 轮），"
                        + "请检查工具授权配置", confirmMode, rounds));
                return false;
            }
            return true;
        } finally {
            // 4. 记录关联关系
            setRecordRelation(sessionId);
            // 确保清理元数据
            AgentMetadataStore.removeOnOwn(agentId);
        }
    }

    /**
     * 拒绝授权处理：提取本轮需确认工具，落库补偿 rejected 工具步（与人工点「禁止」同构，
     * 回放可见），构造拒绝结果续跑输入。同轮被 stopAgent 连累的普通工具不注入结果，
     * 续跑时由 agent 自行执行（对齐主 resume 语义）。
     */
    private List<Msg> rejectPendingTools(Agent agent, Msg pausedMsg, Long sessionId) {
        List<ContentBlock> rejects = new ArrayList<>();
        for (ToolUseBlock toolUse : pausedMsg.getContentBlocks(ToolUseBlock.class)) {
            if (!IConfirmationHook.isNeedConfirm(toolUse.getName())) {
                continue;
            }
            log.info("Scheduled job auto-rejected tool: {} (confirm mode AUTO_REJECT), sessionId: {}",
                    toolUse.getName(), sessionId);
            ChatLogHook.completeMainToolRejected(
                    String.valueOf(sessionId), toolUse.getId(), ConfirmMode.REJECT_RESULT_TEXT);
            rejects.add(ToolResultBlock.of(
                    toolUse.getId(),
                    toolUse.getName(),
                    TextBlock.builder().text(ConfirmMode.REJECT_RESULT_TEXT).build()));
        }
        if (rejects.isEmpty()) {
            return List.of();
        }
        return List.of(Msg.builder().role(MsgRole.TOOL).content(rejects).build());
    }
}
