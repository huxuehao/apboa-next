package com.hxh.apboa.engine.tts.stream;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 断句装配器纯逻辑单测（无外部依赖）
 *
 * @author huxuehao
 */
class SpeechSentenceFeederTest {

    /**
     * token 级增量喂入：首句到句末标点立即出（不受最小长度限制），后续短句向后合并
     */
    @Test
    void tokenStreamBasicSentences() {
        SpeechSentenceFeeder feeder = new SpeechSentenceFeeder();
        List<String> out = new ArrayList<>();
        String text = "好的。我来帮您查询今天的天气情况，请稍等片刻。查询完成了，今天晴。";
        for (int i = 0; i < text.length(); i += 3) {
            out.addAll(feeder.feed(text.substring(i, Math.min(i + 3, text.length()))));
        }
        out.addAll(feeder.flush());

        assertEquals("好的。", out.get(0), "首句应在第一个句号立即输出");
        String joined = String.join("", out);
        assertEquals(text, joined, "全部输出拼回应等于原文");
        for (String seg : out.subList(1, out.size())) {
            assertTrue(seg.length() >= 10 || out.indexOf(seg) == out.size() - 1,
                    "非首句（除末尾）应达到最小长度: " + seg);
        }
    }

    /**
     * markdown 提纯：代码块整块跳过、行内标记去符号、链接读文字、标题去井号
     */
    @Test
    void markdownPurified() {
        SpeechSentenceFeeder feeder = new SpeechSentenceFeeder();
        List<String> out = new ArrayList<>(feeder.feed(
                "# 结论\n这是**重点**内容，参考[官方文档](https://example.com)。\n```java\nint a = 1;\n```\n代码之后的正文。"));
        out.addAll(feeder.flush());
        String joined = String.join("", out);
        assertFalse(joined.contains("int a"), "代码块内容不应被朗读");
        assertFalse(joined.contains("#"), "标题符号应剥离");
        assertFalse(joined.contains("**"), "加粗符号应剥离");
        assertFalse(joined.contains("https://"), "链接地址不应被朗读");
        assertTrue(joined.contains("重点"), "加粗内容应保留");
        assertTrue(joined.contains("官方文档"), "链接文字应保留");
        assertTrue(joined.contains("代码之后的正文"), "代码块后正文应保留");
    }

    /**
     * 流式期间未闭合的代码块整体暂扣，闭合后其后正文继续输出且不重复
     */
    @Test
    void unclosedFenceHeldBack() {
        SpeechSentenceFeeder feeder = new SpeechSentenceFeeder();
        List<String> out = new ArrayList<>(feeder.feed("先看这句话。\n```python\nprint('hi')\n"));
        String earlyJoined = String.join("", out);
        assertFalse(earlyJoined.contains("print"), "未闭合代码块内容不应输出");

        out.addAll(feeder.feed("```\n代码讲完了，继续说结论。"));
        out.addAll(feeder.flush());
        String joined = String.join("", out);
        assertFalse(joined.contains("print"), "闭合后的代码块内容也不应输出");
        assertTrue(joined.contains("先看这句话。"), "代码块前的句子应输出");
        assertTrue(joined.contains("代码讲完了，继续说结论。"), "代码块后的句子应输出");
        assertEquals(joined.indexOf("先看这句话"), joined.lastIndexOf("先看这句话"), "前缀回缩不应造成句子重复");
    }

    /**
     * 渐进式颗粒度：首句遇逗号即发（首声最快），第二句逗号级但有最小长度，
     * 第三句起逗号不再是边界（恢复正常颗粒度）
     */
    @Test
    void progressiveGranularity() {
        SpeechSentenceFeeder feeder = new SpeechSentenceFeeder();
        List<String> out = new ArrayList<>();
        String text = "你好，我来详细说明这个问题的处理方式，需要一点时间。首先第一步，我们先检查配置文件，再确认参数设置是否正确。";
        for (int i = 0; i < text.length(); i += 2) {
            out.addAll(feeder.feed(text.substring(i, Math.min(i + 2, text.length()))));
        }
        out.addAll(feeder.flush());

        assertEquals("你好，", out.get(0), "首句应在第一个逗号立即输出（首声最短路径）");
        assertTrue(out.get(1).endsWith("，") && out.get(1).length() >= 6,
                "第二句应为逗号级边界且达到爬坡门槛: " + out.get(1));
        boolean laterHasNormal = out.subList(3, out.size()).stream()
                .anyMatch(seg -> seg.contains("，") && (seg.endsWith("。") || seg.endsWith("？")));
        assertTrue(out.size() >= 4 && laterHasNormal,
                "第四句起应恢复句末断句（句内允许逗号）: " + out);
        assertEquals(text, String.join("", out), "全部输出拼回应等于原文");
    }

    /**
     * 段结束语义：段尾无句末标点的尾句靠喂换行切不出来（换行在提纯文本末尾
     * 被 trim 剥掉——历史 bug：尾句滞留到下段正文流入才被吐出），
     * 必须显式 flush；flush 后 feeder 跨段续用，第二段正常断句且尾句不重复
     */
    @Test
    void segmentEndFlushesTrailingSentence() {
        SpeechSentenceFeeder feeder = new SpeechSentenceFeeder();
        List<String> first = new ArrayList<>(feeder.feed(
                "你好！我是智能系统助手，具备导航、位置查询、天气查询等多种能力。让我帮你查一下这些信息~\n\n"));
        first.addAll(feeder.feed("\n"));
        assertFalse(String.join("", first).contains("让我帮你查一下"),
                "段尾换行会被提纯 trim 剥掉，不能依赖它切出尾句: " + first);

        List<String> tail = feeder.flush();
        assertTrue(String.join("", tail).contains("让我帮你查一下这些信息~"),
                "段结束 flush 应立即吐出无句末标点的尾句: " + tail);

        List<String> second = new ArrayList<>(feeder.feed("好的，信息都帮你查到了！让我给你详细说一下~"));
        second.addAll(feeder.flush());
        String joined = String.join("", second);
        assertTrue(joined.contains("好的，信息都帮你查到了！"), "flush 后第二段应正常断句输出: " + second);
        assertFalse(joined.contains("这些信息"), "第一段尾句不应在第二段重复输出: " + second);
    }

    /**
     * 超长无标点内容按上限强切，不会无限积压
     */
    @Test
    void overlongForcedSplit() {
        SpeechSentenceFeeder feeder = new SpeechSentenceFeeder();
        String longText = "很长的内容".repeat(60);
        List<String> out = new ArrayList<>(feeder.feed(longText));
        out.addAll(feeder.flush());
        assertTrue(out.size() > 1, "超长文本应被切成多段");
        for (String seg : out) {
            assertTrue(seg.length() <= 120, "单段不应超过上限: " + seg.length());
        }
        assertEquals(longText, String.join("", out));
    }
}
