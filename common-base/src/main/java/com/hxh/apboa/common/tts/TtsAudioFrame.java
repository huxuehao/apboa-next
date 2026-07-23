package com.hxh.apboa.common.tts;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * TTS 音频帧封包：runtime 经 Redis 到 websocket 服务、再到浏览器，
 * 全链路使用同一格式，中转节点只 peek 路由头零重编码。
 *
 * 格式（大端）：[2B threadId长度][threadId UTF-8][4B 序号][PCM16 数据]
 * 序号从 0 递增，供前端检测丢帧（慢消费者被丢帧后直接跳播不补帧）。
 *
 * @author huxuehao
 */
public record TtsAudioFrame(String threadId, int seq, byte[] pcm) {

    public byte[] encode() {
        byte[] tid = threadId.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.allocate(2 + tid.length + 4 + pcm.length);
        buffer.putShort((short) tid.length);
        buffer.put(tid);
        buffer.putInt(seq);
        buffer.put(pcm);
        return buffer.array();
    }

    public static TtsAudioFrame decode(byte[] frame) {
        ByteBuffer buffer = ByteBuffer.wrap(frame);
        int tidLen = buffer.getShort() & 0xFFFF;
        byte[] tid = new byte[tidLen];
        buffer.get(tid);
        int seq = buffer.getInt();
        byte[] pcm = new byte[buffer.remaining()];
        buffer.get(pcm);
        return new TtsAudioFrame(new String(tid, StandardCharsets.UTF_8), seq, pcm);
    }

    /**
     * 只解析路由头（threadId），不复制 PCM——中转路由用
     */
    public static String peekThreadId(byte[] frame) {
        if (frame == null || frame.length < 2) {
            return null;
        }
        int tidLen = ((frame[0] & 0xFF) << 8) | (frame[1] & 0xFF);
        if (frame.length < 2 + tidLen) {
            return null;
        }
        return new String(frame, 2, tidLen, StandardCharsets.UTF_8);
    }
}
