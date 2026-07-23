package com.hxh.apboa.engine.tts.stream;

/**
 * 语音合成会话：一次 AI 回复的正文播报对应一个会话。
 * 会话内说话人条件恒定（音色一次确定，整段一致），文本增量喂入、
 * 服务端断句、音频经 {@link TtsAudioListener} 连续回调。
 *
 * 生命周期：openSession → feedText×N → finishText（自然收尾）
 * 或 abort（打断，立即静默）。两者幂等，之后 feedText 静默丢弃。
 *
 * @author huxuehao
 */
public interface TtsStreamSession {

    /**
     * 喂入正文增量（LLM token 级分片，也可整段）
     */
    void feedText(String delta);

    /**
     * 段结束（一轮回复的多段正文之间，如工具调用间隔）：冲刷断句缓冲的尾巴，
     * 会话继续存活可接续喂入。段尾无句末标点时靠喂换行字符断句不可靠——
     * 换行位于提纯文本末尾会被 trim 剥掉，尾句滞留到下段正文流入才被切出。
     */
    void flushSegment();

    /**
     * 正文结束：冲刷断句缓冲的尾巴，全部合成完后回调 onEnd
     */
    void finishText();

    /**
     * 立即打断：丢弃未合成句子、终止在途请求，不再有音频回调
     */
    void abort();

    /**
     * 会话是否仍在工作（未结束且未打断）
     */
    boolean isActive();
}
