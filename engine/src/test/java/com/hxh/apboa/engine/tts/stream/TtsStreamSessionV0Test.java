package com.hxh.apboa.engine.tts.stream;

import com.hxh.apboa.common.enums.ModelProviderType;
import com.hxh.apboa.common.wrapper.ModelConfigWrapper;
import com.hxh.apboa.engine.tts.OpenAiCompatTtsProvider;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 合成会话 V0 探针：真实调用本地 mlx-audio(11436) + Base 克隆音色资产，
 * 模拟 LLM token 流验证「增量喂入 → 服务端断句 → PCM 连续回调」全链路。
 * 结束后把整段 PCM 写成 WAV 供人耳验收音色一致性与衔接。
 *
 * 运行：mvn -pl engine test -Dtts.stream=true -Dtest=TtsStreamSessionV0Test -Dsurefire.useFile=false
 *
 * @author huxuehao
 */
@EnabledIfSystemProperty(named = "tts.stream", matches = "true",
        disabledReason = "TTS 会话探针：依赖本地 mlx-audio(11436) 与音色资产，默认跳过；用 -Dtts.stream=true 手动启用")
class TtsStreamSessionV0Test {

    private static final String FAKE_LLM_MARKDOWN = """
            好的，我来说明一下部署步骤。首先需要确认服务器环境，**内存至少十六G**，磁盘预留五十G以上。
            然后执行安装命令：
            ```bash
            ./install.sh --mode production
            ```
            安装完成后访问[控制台](https://console.example.com)确认服务状态。整个过程大约需要十分钟，如果遇到问题可以随时问我。""";

    @Test
    void streamSessionEndToEnd() throws Exception {
        ModelConfigWrapper config = new ModelConfigWrapper();
        config.setProvider(ModelProviderType.OPEN_AI);
        config.setBaseUrl("http://127.0.0.1:11436/v1");
        config.setApiKey("local-no-key");
        config.setModelCode("/Users/vaulka/Models/Qwen3-TTS-12Hz-1.7B-Base-8bit");
        config.setBodyParams(Map.of(
                "ref_audio", "/Users/vaulka/Models/voice-assets/enterprise_female_ref_v2.wav",
                "ref_text", "配置切换验证：这句话应该由克隆的企业女声播出。",
                "lang_code", "Chinese",
                "response_format", "wav"
        ));

        AtomicReference<int[]> format = new AtomicReference<>();
        List<byte[]> chunks = new ArrayList<>();
        List<Long> chunkArrivalMs = new ArrayList<>();
        AtomicInteger errors = new AtomicInteger();
        CountDownLatch done = new CountDownLatch(1);
        long start = System.currentTimeMillis();

        TtsAudioListener listener = new TtsAudioListener() {
            @Override
            public void onFormat(int sampleRate, int bitsPerSample, int channels) {
                format.set(new int[]{sampleRate, bitsPerSample, channels});
            }

            @Override
            public void onAudioChunk(byte[] pcm) {
                synchronized (chunks) {
                    chunks.add(pcm);
                    chunkArrivalMs.add(System.currentTimeMillis() - start);
                }
            }

            @Override
            public void onEnd() {
                done.countDown();
            }

            @Override
            public void onError(String message) {
                System.err.println("会话错误: " + message);
                errors.incrementAndGet();
                done.countDown();
            }
        };

        LocalTtsStreamProvider provider = new LocalTtsStreamProvider(new OpenAiCompatTtsProvider());
        TtsStreamSession session = provider.openSession(config, listener);

        // 模拟 LLM token 流：每 4 字一片、片间 30ms
        for (int i = 0; i < FAKE_LLM_MARKDOWN.length(); i += 4) {
            session.feedText(FAKE_LLM_MARKDOWN.substring(i, Math.min(i + 4, FAKE_LLM_MARKDOWN.length())));
            Thread.sleep(30);
        }
        session.finishText();

        Assertions.assertTrue(done.await(180, TimeUnit.SECONDS), "会话应在超时内结束");
        Assertions.assertEquals(0, errors.get(), "会话不应出错");
        Assertions.assertTrue(chunks.size() >= 3, "多句正文应产出多个音频块，实际: " + chunks.size());
        Assertions.assertNotNull(format.get(), "应收到格式声明");
        Assertions.assertEquals(24000, format.get()[0], "mlx-audio 输出应为 24kHz");

        long totalPcm = chunks.stream().mapToLong(c -> c.length).sum();
        double seconds = totalPcm / (24000.0 * 2);
        System.out.printf("首块到达: %dms, 块数: %d, 总时长: %.1fs%n",
                chunkArrivalMs.get(0), chunks.size(), seconds);
        Assertions.assertTrue(seconds > 5, "整段音频时长应明显大于 5 秒");

        Path out = Path.of("/Users/vaulka/Models/voice-assets/stream_session_v0.wav");
        writeWav(out, chunks, format.get()[0], format.get()[1], format.get()[2]);
        System.out.println("人耳验收文件: afplay " + out);
    }

    private void writeWav(Path path, List<byte[]> chunks, int sampleRate, int bits, int channels) throws IOException {
        ByteArrayOutputStream pcm = new ByteArrayOutputStream();
        for (byte[] c : chunks) {
            pcm.writeBytes(c);
        }
        byte[] data = pcm.toByteArray();
        ByteBuffer header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
        int byteRate = sampleRate * channels * bits / 8;
        header.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(36 + data.length)
                .put("WAVE".getBytes(StandardCharsets.US_ASCII))
                .put("fmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16)
                .putShort((short) 1).putShort((short) channels)
                .putInt(sampleRate).putInt(byteRate)
                .putShort((short) (channels * bits / 8)).putShort((short) bits)
                .put("data".getBytes(StandardCharsets.US_ASCII)).putInt(data.length);
        ByteArrayOutputStream wav = new ByteArrayOutputStream();
        wav.writeBytes(header.array());
        wav.writeBytes(data);
        Files.write(path, wav.toByteArray());
    }
}
