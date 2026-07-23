package com.hxh.apboa.engine.tts.stream;

import com.hxh.apboa.common.enums.ModelProviderType;
import com.hxh.apboa.common.wrapper.ModelConfigWrapper;
import com.hxh.apboa.engine.tts.OpenAiCompatTtsProvider;
import com.hxh.apboa.engine.tts.TtsResult;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * OpenAI 兼容服务的合成会话实现（本地 mlx-audio 等）。
 *
 * 会话语义的实现方式：说话人条件（ref_audio/ref_text 或 voice，经 bodyParams）
 * 在整个会话内恒定，配合引擎侧的参考编码缓存，逐句请求也保持音色向量级一致；
 * 句子在会话专属单线程上串行合成（引擎本身串行，客户端并发只会排队），
 * 合成与下游播放天然形成流水线。待引擎具备输入流式端点时，仅替换本实现。
 *
 * @author huxuehao
 */
@Component
@RequiredArgsConstructor
public class LocalTtsStreamProvider implements TtsStreamProvider {

    private static final Logger log = LoggerFactory.getLogger(LocalTtsStreamProvider.class);

    private final OpenAiCompatTtsProvider openAiCompatTtsProvider;

    @Override
    public ModelProviderType getType() {
        return ModelProviderType.OPEN_AI;
    }

    @Override
    public TtsStreamSession openSession(ModelConfigWrapper config, TtsAudioListener listener) {
        return new Session(config, listener);
    }

    private class Session implements TtsStreamSession {

        private final ModelConfigWrapper config;
        private final TtsAudioListener listener;
        private final SpeechSentenceFeeder feeder = new SpeechSentenceFeeder();
        /** 会话专属单线程：句子按序合成，abort 时立刻丢弃排队任务 */
        private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "tts-stream-session");
            t.setDaemon(true);
            return t;
        });
        private final AtomicBoolean aborted = new AtomicBoolean(false);
        private final AtomicBoolean finished = new AtomicBoolean(false);
        private volatile boolean formatSent = false;

        Session(ModelConfigWrapper config, TtsAudioListener listener) {
            this.config = config;
            this.listener = listener;
        }

        @Override
        public void feedText(String delta) {
            if (!isActive()) {
                return;
            }
            submitSentences(feeder.feed(delta));
        }

        @Override
        public void flushSegment() {
            if (!isActive()) {
                return;
            }
            submitSentences(feeder.flush());
        }

        @Override
        public void finishText() {
            if (aborted.get() || !finished.compareAndSet(false, true)) {
                return;
            }
            submitSentences(feeder.flush());
            worker.execute(() -> {
                if (!aborted.get()) {
                    listener.onEnd();
                }
            });
            worker.shutdown();
        }

        @Override
        public void abort() {
            if (aborted.compareAndSet(false, true)) {
                // shutdownNow 丢弃排队句子并中断在途合成线程（WebClient block 响应中断）
                worker.shutdownNow();
            }
        }

        @Override
        public boolean isActive() {
            return !aborted.get() && !finished.get();
        }

        private void submitSentences(List<String> sentences) {
            for (String sentence : sentences) {
                worker.execute(() -> synthesize(sentence));
            }
        }

        private void synthesize(String sentence) {
            if (aborted.get()) {
                return;
            }
            try {
                TtsResult result = openAiCompatTtsProvider.synthesize(config, sentence);
                if (aborted.get()) {
                    return;
                }
                WavPcm wav = WavPcm.parse(result.audio());
                if (!formatSent) {
                    formatSent = true;
                    listener.onFormat(wav.sampleRate(), wav.bitsPerSample(), wav.channels());
                }
                listener.onAudioChunk(wav.pcm());
            } catch (Exception e) {
                if (aborted.get()) {
                    return;
                }
                log.warn("会话合成失败，终止播报: {}", e.getMessage());
                abort();
                listener.onError(e.getMessage());
            }
        }
    }
}
