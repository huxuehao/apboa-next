package com.hxh.apboa.engine.tts.stream;

import com.hxh.apboa.common.wrapper.ModelConfigWrapper;
import com.hxh.apboa.engine.tts.TtsResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 逐句流式合成的通用骨架（伪流式）：把文本按句切分，在会话专属单线程上逐句
 * 「同步合成 → 解 WAV 出 PCM → 推给 listener」，让合成与下游播放形成流水线。
 * 说话人条件（voice / ref_audio 经 bodyParams）在会话内恒定，逐句请求保持音色一致。
 *
 * 各供应商只在「单段文本如何合成」上有差异——子类实现 {@link #synthesizeSentence}
 * 提供单段合成即可，其余会话生命周期（切句 / 串行 / 中断 / 收尾）由本类统一承担。
 * 待某供应商具备真流式输入端点时，该供应商可不继承本类、直接实现 TtsStreamProvider
 * 走真流式，与其它伪流式实现并存。
 *
 * @author huxuehao
 */
public abstract class AbstractSentenceTtsStreamProvider implements TtsStreamProvider {

    private static final Logger log = LoggerFactory.getLogger(AbstractSentenceTtsStreamProvider.class);

    /**
     * 单段文本 → 合成音频（WAV 字节 + MIME）。会话内在专属单线程上逐句串行调用；
     * 音色等差异参数由 config.bodyParams 承载，实现方无需关心会话语义。
     */
    protected abstract TtsResult synthesizeSentence(ModelConfigWrapper config, String sentence);

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
                TtsResult result = synthesizeSentence(config, sentence);
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
                // 单句合成失败（如个别文本被供应商回绝）只跳过该句，不拖垮整段播报
                log.warn("单句合成失败，跳过: {}", e.getMessage());
            }
        }
    }
}
