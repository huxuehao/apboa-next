package com.hxh.apboa.engine.tts.stream;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * WAV 容器解析：从单段合成返回的 WAV 中取出格式与裸 PCM 数据段。
 * 只支持 PCM 编码的 RIFF/WAVE（mlx-audio 与 DashScope 的输出均满足）。
 *
 * @author huxuehao
 */
public record WavPcm(int sampleRate, int bitsPerSample, int channels, byte[] pcm) {

    public static WavPcm parse(byte[] wav) {
        if (wav == null || wav.length < 44
                || !"RIFF".equals(new String(wav, 0, 4, StandardCharsets.US_ASCII))
                || !"WAVE".equals(new String(wav, 8, 4, StandardCharsets.US_ASCII))) {
            throw new RuntimeException("音频不是 WAV 格式，无法提取 PCM");
        }
        ByteBuffer buffer = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN);
        int pos = 12;
        int sampleRate = -1;
        int bitsPerSample = -1;
        int channels = -1;
        int dataOffset = -1;
        int dataSize = -1;
        while (pos + 8 <= wav.length) {
            String chunkId = new String(wav, pos, 4, StandardCharsets.US_ASCII);
            int chunkSize = buffer.getInt(pos + 4);
            if (chunkSize < 0) {
                break;
            }
            if ("fmt ".equals(chunkId) && pos + 8 + 16 <= wav.length) {
                channels = buffer.getShort(pos + 8 + 2);
                sampleRate = buffer.getInt(pos + 8 + 4);
                bitsPerSample = buffer.getShort(pos + 8 + 14);
            } else if ("data".equals(chunkId)) {
                dataOffset = pos + 8;
                dataSize = (int) Math.min(chunkSize, wav.length - pos - 8L);
            }
            if (sampleRate > 0 && dataOffset >= 0) {
                break;
            }
            pos += 8 + chunkSize + (chunkSize & 1);
        }
        if (sampleRate <= 0 || dataOffset < 0 || dataSize < 0) {
            throw new RuntimeException("无法解析 WAV 音频头");
        }
        byte[] pcm = new byte[dataSize];
        System.arraycopy(wav, dataOffset, pcm, 0, dataSize);
        return new WavPcm(sampleRate, bitsPerSample, channels, pcm);
    }
}
