package com.hxh.apboa.engine.hitl;

import com.hxh.apboa.engine.model.HttpTransportHelper;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.Event;
import io.agentscope.core.agent.EventType;
import io.agentscope.core.agent.StreamOptions;
import io.agentscope.core.formatter.openai.OpenAIChatFormatter;
import io.agentscope.core.hook.Hook;
import io.agentscope.core.hook.HookEvent;
import io.agentscope.core.hook.PostReasoningEvent;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.OpenAIChatModel;
import io.agentscope.core.session.InMemorySession;
import io.agentscope.core.session.Session;
import io.agentscope.core.session.mysql.MysqlSession;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import io.agentscope.core.state.StatePersistence;
import io.agentscope.core.tool.AgentTool;
import io.agentscope.core.tool.ToolCallParam;
import io.agentscope.core.tool.Toolkit;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * HITL V0 前置验证（见 docs/hitl-confirmation-refactor.md §6.0）。
 *
 * <p>目标：在 apboa 实际使用的 <b>stream</b> 模式下，验证官方 HITL「暂停 → 持久化 → 换实例加载 →
 * 继续执行 / 喂入拒绝结果」整条链路是否走得通，并验证 jar 反编译挖到的官方开关
 * {@code enablePendingToolRecovery} 是否能直接支撑。
 *
 * <p>本测试<b>真实调用本地 Ollama</b>（OpenAI 兼容端点），模型
 * {@code hauhaucs-qwen3.6-35b-a3b-q8kp}（capabilities：tools + thinking）。
 * 因此它是一个集成探针、且较慢（首 token 取决于本地推理）；不接入 Spring 上下文，直接 new。
 *
 * <p>默认跳过（依赖本地 Ollama + 真实 MySQL），需显式启用。运行示例：
 * {@code mvn -pl engine test -Dhitl.v0=true -Dtest=HitlV0StreamRecoveryTest#e2e_approveAll -Dsurefire.useFile=false}
 */
@EnabledIfSystemProperty(named = "hitl.v0", matches = "true",
        disabledReason = "HITL V0 探针：依赖本地 Ollama + 真实 MySQL，默认跳过；用 -Dhitl.v0=true 手动启用")
class HitlV0StreamRecoveryTest {

    /** 本地 Ollama 的 OpenAI 兼容端点（用 OpenAI 协议是为了能拿到思维链 reasoning/thinking）。 */
    static final String BASE_URL = "http://localhost:11434/v1";
    static final String MODEL = "hauhaucs-qwen3.6-35b-a3b-q8kp:latest";
    static final String API_KEY = "ollama"; // Ollama 不校验，占位即可
    static final String THREAD_ID = "v0-thread-1";
    static final Duration RUN_TIMEOUT = Duration.ofSeconds(240);

    /** 记录每个工具<b>实际被执行</b>的次数（订阅 callAsync 时才计入）。用于断言「被拒绝的工具不执行」。 */
    static final List<String> EXECUTED = new CopyOnWriteArrayList<>();

    @BeforeEach
    void reset() {
        EXECUTED.clear();
    }

    // ====================== 测试用最小组件 ======================

    /** 假工具：被执行时记录工具名并返回固定文本。 */
    static class EchoTool implements AgentTool {
        private final String name;
        private final String description;
        private final String result;
        private final Map<String, Object> parameters;

        EchoTool(String name, String description, String result, Map<String, Object> parameters) {
            this.name = name;
            this.description = description;
            this.result = result;
            this.parameters = parameters;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getDescription() {
            return description;
        }

        @Override
        public Map<String, Object> getParameters() {
            return parameters;
        }

        @Override
        public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
            ToolUseBlock tub = param.getToolUseBlock();
            final String id = tub != null ? tub.getId() : null;
            return Mono.fromCallable(() -> {
                EXECUTED.add(name);
                System.out.println("[TOOL-EXEC] " + name + " id=" + id + " input=" + param.getInput());
                return ToolResultBlock.of(id, name, TextBlock.builder().text(result).build());
            });
        }
    }

    /** 暂停 hook：任何一轮推理只要产出了 ToolUseBlock，就 stopAgent（模拟「全部工具都要确认」）。 */
    static class ConfirmAllHook implements Hook {
        @Override
        public <T extends HookEvent> Mono<T> onEvent(T event) {
            if (event instanceof PostReasoningEvent e) {
                Msg m = e.getReasoningMessage();
                if (m != null) {
                    List<ToolUseBlock> tools = m.getContentBlocks(ToolUseBlock.class);
                    if (!tools.isEmpty()) {
                        System.out.println("[HOOK] stopAgent，pending="
                                + tools.stream().map(ToolUseBlock::getName).toList());
                        e.stopAgent();
                    }
                }
            }
            return Mono.just(event);
        }
    }

    private ReActAgent buildAgent() {
        Model model = OpenAIChatModel.builder()
                .apiKey(API_KEY)
                .modelName(MODEL)
                .baseUrl(BASE_URL)
                .stream(true)
                .httpTransport(HttpTransportHelper.createOkHttpTransport())
                .formatter(new OpenAIChatFormatter())
                .build();

        Toolkit toolkit = new Toolkit();
        toolkit.registerAgentTool(new EchoTool(
                "query_time", "查询当前日期时间，无需任何参数", "2026-06-30 12:00:00", schemaNoArgs()));
        toolkit.registerAgentTool(new EchoTool(
                "query_weather", "查询指定城市的当天天气", "泉州：晴，28℃", schemaCity()));

        return ReActAgent.builder()
                .name("V0_AGENT")
                .model(model)
                .toolkit(toolkit)
                .hook(new ConfirmAllHook())
                .sysPrompt("你是一个助手。用户询问时间时调用 query_time；询问天气时调用 query_weather（参数 city=城市名）。"
                        + "当用户在一句话里同时询问多个事项时，请在同一轮内一次性调用所有需要的工具（例如同时调用 query_time 和 query_weather）。"
                        + "必须通过调用工具来回答，不要凭空编造时间或天气。"
                        + "重要约束：如果某次工具调用的返回结果是『用户已拒绝执行』，表示用户明确拒绝了该操作，"
                        + "你必须接受这一事实并立即停止调用该工具，本轮内绝对不要再次调用它；"
                        + "直接基于现有信息回复用户，并说明该操作已被用户拒绝。")
                .maxIters(6)
                // 关键：开启官方「待确认工具恢复」+ 状态持久化（含工具态），V0 重点验证它
                .enablePendingToolRecovery(true)
                .statePersistence(StatePersistence.builder()
                        .memoryManaged(true)
                        .statefulToolsManaged(true)
                        .build())
                .build();
    }

    private static Map<String, Object> schemaNoArgs() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", new LinkedHashMap<>());
        return schema;
    }

    private static Map<String, Object> schemaCity() {
        Map<String, Object> city = new LinkedHashMap<>();
        city.put("type", "string");
        city.put("description", "城市名，例如 泉州");
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("city", city);
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of("city"));
        return schema;
    }

    private static StreamOptions newOpts() {
        return StreamOptions.builder()
                .eventTypes(EventType.ALL)
                .incremental(true)
                .includeReasoningChunk(true)   // 思维链增量
                .includeReasoningResult(true)  // 思维链最终
                .build();
    }

    private static Msg userMsg() {
        return Msg.builder()
                .role(MsgRole.USER)
                .textContent("现在几点了？另外泉州今天天气怎么样？")
                .build();
    }

    /** 消费 stream，逐事件打印（type / isLast / generateReason / content blocks），返回事件列表。 */
    private List<Event> drain(ReActAgent agent, List<Msg> input, String tag) {
        System.out.println("\n======== [" + tag + "] stream start，input.size=" + input.size() + " ========");
        List<Event> events = agent.stream(input, newOpts()).collectList().block(RUN_TIMEOUT);
        int n = events == null ? 0 : events.size();
        System.out.println("-------- [" + tag + "] events=" + n + " --------");
        if (events != null) {
            for (Event ev : events) {
                Msg m = ev.getMessage();
                List<String> blocks = new ArrayList<>();
                String thinkingPeek = null;
                if (m != null && m.getContent() != null) {
                    for (ContentBlock b : m.getContent()) {
                        blocks.add(b.getClass().getSimpleName());
                    }
                }
                System.out.println(String.format("  type=%-12s last=%-5s reason=%-26s blocks=%s",
                        ev.getType(), ev.isLast(),
                        m != null ? m.getGenerateReason() : null,
                        blocks));
            }
        }
        return events;
    }

    /** 从事件流里收集去重后的 pending ToolUseBlock（按 id 去重）。 */
    private List<ToolUseBlock> collectPending(List<Event> events) {
        Map<String, ToolUseBlock> byId = new LinkedHashMap<>();
        if (events != null) {
            for (Event ev : events) {
                Msg m = ev.getMessage();
                if (m == null) continue;
                for (ToolUseBlock t : m.getContentBlocks(ToolUseBlock.class)) {
                    String key = t.getId() != null ? t.getId() : (t.getName() + "@" + byId.size());
                    byId.putIfAbsent(key, t);
                }
            }
        }
        return new ArrayList<>(byId.values());
    }

    private List<Event> pauseAndSave(Session session) {
        ReActAgent agent = buildAgent();
        List<Event> ev1 = drain(agent, List.of(userMsg()), "RUN1-pause");
        List<ToolUseBlock> pending = collectPending(ev1);
        System.out.println("[OBSERVE] pending tools=" + pending.stream().map(ToolUseBlock::getName).toList());
        System.out.println("[OBSERVE] EXECUTED after RUN1（期望为空，工具不应在确认前执行）=" + EXECUTED);
        agent.saveTo(session, THREAD_ID);
        System.out.println("[SAVE] saveTo(session, " + THREAD_ID + ") 完成；session.listSessionKeys="
                + session.listSessionKeys());
        return ev1;
    }

    // ====================== 三种恢复组合 ======================

    /** 组合一：全部允许 → loadFrom 后用空输入继续，期望 pending 工具被真正执行。 */
    @Test
    void e2e_approveAll() {
        Session session = new InMemorySession();
        pauseAndSave(session);

        ReActAgent restored = buildAgent(); // 换一个全新实例，模拟跨请求/跨 runtime
        restored.loadFrom(session, THREAD_ID);
        System.out.println("\n[RESUME approveAll] loadFrom 完成，用空输入 stream 继续……");
        List<Event> ev2 = drain(restored, List.of(), "RUN2-approveAll");

        System.out.println("[RESULT approveAll] EXECUTED=" + EXECUTED);
        Assertions.assertNotNull(ev2, "恢复后的事件流不应为 null");
    }

    /** 组合二：全部拒绝 → 喂「用户已拒绝执行」的 ToolResultBlock，期望工具不执行、不重调。 */
    @Test
    void e2e_rejectAll() {
        Session session = new InMemorySession();
        List<Event> ev1 = pauseAndSave(session);
        List<ToolUseBlock> pending = collectPending(ev1);

        List<ContentBlock> rejects = new ArrayList<>();
        for (ToolUseBlock t : pending) {
            rejects.add(ToolResultBlock.of(t.getId(), t.getName(),
                    TextBlock.builder().text("用户已拒绝执行").build()));
        }
        Msg rejectMsg = Msg.builder().role(MsgRole.TOOL).content(rejects).build();

        ReActAgent restored = buildAgent();
        restored.loadFrom(session, THREAD_ID);
        System.out.println("\n[RESUME rejectAll] 喂入 " + rejects.size() + " 个「用户已拒绝执行」结果继续……");
        List<Event> ev2 = drain(restored, List.of(rejectMsg), "RUN2-rejectAll");

        System.out.println("[RESULT rejectAll] EXECUTED（期望为空）=" + EXECUTED);
        Assertions.assertNotNull(ev2);
    }

    /** 组合三：部分（拒绝第一个、其余留给 agent 执行）→ 验证协议完整性与混合恢复行为。 */
    @Test
    void e2e_partial() {
        Session session = new InMemorySession();
        List<Event> ev1 = pauseAndSave(session);
        List<ToolUseBlock> pending = collectPending(ev1);
        Assertions.assertFalse(pending.isEmpty(), "需要至少一个 pending 工具才能测部分组合");

        // 仅对第一个工具喂「已拒绝」，其余不喂（期望由 agent 自己执行）
        ToolUseBlock rejected = pending.get(0);
        Msg rejectMsg = Msg.builder().role(MsgRole.TOOL)
                .content(ToolResultBlock.of(rejected.getId(), rejected.getName(),
                        TextBlock.builder().text("用户已拒绝执行").build()))
                .build();

        ReActAgent restored = buildAgent();
        restored.loadFrom(session, THREAD_ID);
        System.out.println("\n[RESUME partial] 拒绝=" + rejected.getName()
                + "，其余留给 agent 执行……");
        List<Event> ev2 = drain(restored, List.of(rejectMsg), "RUN2-partial");

        System.out.println("[RESULT partial] EXECUTED=" + EXECUTED
                + "（期望不含被拒的 " + rejected.getName() + "）");
        Assertions.assertNotNull(ev2);
    }

    /** 极简 DataSource：每次新建连接，零连接池依赖，仅用于 V0 阶段2 验证。 */
    static class SimpleDataSource implements DataSource {
        private final String url;
        private final String user;
        private final String password;

        SimpleDataSource(String url, String user, String password) {
            this.url = url;
            this.user = user;
            this.password = password;
        }

        @Override
        public Connection getConnection() throws SQLException {
            return DriverManager.getConnection(url, user, password);
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return DriverManager.getConnection(url, username, password);
        }

        @Override
        public java.io.PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(java.io.PrintWriter out) {
        }

        @Override
        public void setLoginTimeout(int seconds) {
        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public java.util.logging.Logger getParentLogger() {
            return java.util.logging.Logger.getGlobal();
        }

        @Override
        public <T> T unwrap(Class<T> iface) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return false;
        }
    }

    /**
     * 阶段2：把暂停态保存到<b>真实 MySQL</b>，再用<b>全新的 MysqlSession 实例 + 全新 agent</b>
     * 仅靠 MySQL 加载恢复（模拟 run 落 A 实例、resume 落 B 实例的跨 runtime 场景）。
     */
    @Test
    void e2e_mysql_crossInstance() {
        String url = "jdbc:mysql://localhost:3306/apboa_next"
                + "?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true&characterEncoding=utf8";
        String tid = "v0-mysql-thread";

        // 实例 A：暂停 → 保存到 MySQL
        DataSource dsA = new SimpleDataSource(url, "root", "123456");
        Session sessionA = new MysqlSession(dsA, "apboa_next", "agentscope_sessions", true);
        ReActAgent agentA = buildAgent();
        List<Event> ev1 = drain(agentA, List.of(userMsg()), "MYSQL-RUN1-pause");
        List<ToolUseBlock> pending = collectPending(ev1);
        System.out.println("[MYSQL] RUN1 pending=" + pending.stream().map(ToolUseBlock::getName).toList()
                + "，EXECUTED=" + EXECUTED);
        agentA.saveTo(sessionA, tid);
        System.out.println("[MYSQL] saveTo 完成，listSessionKeys=" + sessionA.listSessionKeys());

        // 实例 B：全新 DataSource + 全新 MysqlSession + 全新 agent，仅靠 MySQL 恢复
        DataSource dsB = new SimpleDataSource(url, "root", "123456");
        Session sessionB = new MysqlSession(dsB, "apboa_next", "agentscope_sessions", true);
        ReActAgent agentB = buildAgent();
        agentB.loadFrom(sessionB, tid);
        System.out.println("[MYSQL] 新实例 loadFrom 完成，全允许恢复……");
        List<Event> ev2 = drain(agentB, List.of(), "MYSQL-RUN2-approveAll");

        System.out.println("[MYSQL RESULT] EXECUTED=" + EXECUTED);
        Assertions.assertNotNull(ev2);
        Assertions.assertFalse(EXECUTED.isEmpty(),
                "跨 MysqlSession 实例恢复后 pending 工具应被执行（否则说明暂停态未正确落库/读回）");
    }
}
