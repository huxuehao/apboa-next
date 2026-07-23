package com.hxh.apboa.engine.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hxh.apboa.engine.tool.ToolProgressBridge;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ToolSchema;
import io.agentscope.core.model.transport.HttpTransportException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

class WorkflowProgressModelTest {

    private static final String TOOL_USE_ID = "workflow-tool-test";

    @AfterEach
    void cleanup() {
        ToolProgressBridge.unregister(TOOL_USE_ID);
        ToolProgressBridge.clearCurrent();
    }

    @Test
    void shouldExposeEachRetryAndPreserveRequestOptions() {
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<GenerateOptions> delegatedOptions = new AtomicReference<>();
        ChatResponse response = new ChatResponse(
                "response",
                List.of(TextBlock.builder().text("done").build()),
                null,
                Map.of(),
                "stop");
        Model flakyModel = new Model() {
            @Override
            public Flux<ChatResponse> stream(
                    List<Msg> messages, List<ToolSchema> tools, GenerateOptions options) {
                delegatedOptions.set(options);
                return calls.incrementAndGet() < 3
                        ? Flux.error(new HttpTransportException("temporary failure"))
                        : Flux.just(response);
            }

            @Override
            public String getModelName() {
                return "fake";
            }
        };

        List<ToolProgressBridge.Progress> progress = new ArrayList<>();
        ToolProgressBridge.register(TOOL_USE_ID, progress::add);
        WorkflowProgressModel model = new WorkflowProgressModel(
                flakyModel, TOOL_USE_ID, Duration.ofMillis(1), Duration.ofMillis(5));

        ChatResponse actual = model.stream(
                        List.of(),
                        List.of(),
                        GenerateOptions.builder().temperature(0.25).build())
                .single()
                .block(Duration.ofSeconds(30));

        assertThat(actual).isSameAs(response);
        assertThat(calls).hasValue(3);
        assertThat(delegatedOptions.get().getTemperature()).isEqualTo(0.25);
        assertThat(delegatedOptions.get().getExecutionConfig().getMaxAttempts()).isEqualTo(1);
        assertThat(progress)
                .extracting(ToolProgressBridge.Progress::getPhase)
                .containsExactly(
                        "MODEL_WAITING",
                        "MODEL_RETRYING",
                        "MODEL_WAITING",
                        "MODEL_RETRYING",
                        "MODEL_WAITING",
                        "MODEL_GENERATING",
                        "MODEL_SUCCEEDED");
        assertThat(progress)
                .filteredOn(item -> "MODEL_RETRYING".equals(item.getPhase()))
                .extracting(ToolProgressBridge.Progress::getAttempt)
                .containsExactly(2, 3);

        WorkflowProgressModel.RequestTiming timing = model.snapshotRequestTimings().getFirst();
        assertThat(timing.status()).isEqualTo("SUCCESS");
        assertThat(timing.finishReason()).isEqualTo("stop");
        assertThat(timing.durationMs()).isNotNull().isNotNegative();
        assertThat(timing.ttftMs()).isNotNull().isNotNegative();
        assertThat(timing.attempts())
                .extracting(WorkflowProgressModel.AttemptTiming::status)
                .containsExactly("FAIL", "FAIL", "SUCCESS");
    }

    @Test
    void shouldNotRetryNonRetryableError() {
        AtomicInteger calls = new AtomicInteger();
        Model invalidRequestModel = new Model() {
            @Override
            public Flux<ChatResponse> stream(
                    List<Msg> messages, List<ToolSchema> tools, GenerateOptions options) {
                calls.incrementAndGet();
                return Flux.error(new IllegalArgumentException("invalid request"));
            }

            @Override
            public String getModelName() {
                return "fake";
            }
        };

        List<ToolProgressBridge.Progress> progress = new ArrayList<>();
        ToolProgressBridge.register(TOOL_USE_ID, progress::add);
        WorkflowProgressModel model = new WorkflowProgressModel(invalidRequestModel, TOOL_USE_ID);

        assertThatThrownBy(() -> model.stream(List.of(), List.of(), null)
                        .then()
                        .block(Duration.ofSeconds(5)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("invalid request");

        assertThat(calls).hasValue(1);
        assertThat(progress)
                .extracting(ToolProgressBridge.Progress::getPhase)
                .containsExactly("MODEL_WAITING", "MODEL_FAILED");
        assertThat(model.snapshotRequestTimings().getFirst().status()).isEqualTo("FAIL");
    }

    @Test
    void shouldKeepWhitelistedProviderMetrics() {
        ChatResponse response = new ChatResponse(
                "response",
                List.of(),
                null,
                Map.of(
                        "load_duration", 12L,
                        "prompt_eval_duration", 34L,
                        "eval_duration", 56L,
                        "secret", "must-not-persist"),
                "stop");
        Model modelDelegate = new Model() {
            @Override
            public Flux<ChatResponse> stream(
                    List<Msg> messages, List<ToolSchema> tools, GenerateOptions options) {
                return Flux.just(response);
            }

            @Override
            public String getModelName() {
                return "fake";
            }
        };
        WorkflowProgressModel model = new WorkflowProgressModel(modelDelegate, null);

        model.stream(List.of(), List.of(), null).blockLast(Duration.ofSeconds(5));

        assertThat(model.snapshotRequestTimings().getFirst().providerMetrics())
                .containsEntry("load_duration", 12L)
                .containsEntry("prompt_eval_duration", 34L)
                .containsEntry("eval_duration", 56L)
                .doesNotContainKey("secret");
    }

    @Test
    void shouldIgnoreEmptyProtocolChunkWhenMeasuringFirstToken() {
        ChatResponse emptyProtocolChunk =
                new ChatResponse("response", List.of(), null, Map.of(), null);
        ChatResponse firstContentChunk = new ChatResponse(
                "response",
                List.of(TextBlock.builder().text("first token").build()),
                null,
                Map.of(),
                "stop");
        Model modelDelegate = new Model() {
            @Override
            public Flux<ChatResponse> stream(
                    List<Msg> messages, List<ToolSchema> tools, GenerateOptions options) {
                return Flux.just(emptyProtocolChunk, firstContentChunk);
            }

            @Override
            public String getModelName() {
                return "fake";
            }
        };
        List<ToolProgressBridge.Progress> progress = new ArrayList<>();
        ToolProgressBridge.register(TOOL_USE_ID, progress::add);
        WorkflowProgressModel model = new WorkflowProgressModel(modelDelegate, TOOL_USE_ID);

        model.stream(List.of(), List.of(), null).blockLast(Duration.ofSeconds(5));

        assertThat(progress)
                .filteredOn(item -> "MODEL_GENERATING".equals(item.getPhase()))
                .hasSize(1);
        assertThat(model.snapshotRequestTimings().getFirst().ttftMs())
                .isNotNull()
                .isNotNegative();
    }
}
