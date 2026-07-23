package com.hxh.apboa.engine.tts.stream;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 流式播报的文本装配器：markdown 增量流入 → 提纯为可朗读纯文本 → 按句切段输出。
 * 服务端断句语义（等价百炼 realtime 的 ServerCommit：调用方只管喂 token，
 * 本类决定何时凑成一段可合成的句子）。
 *
 * 提纯目标是「读起来自然」而非严格解析：代码块/表格/特殊渲染块（均为 fenced 语法）
 * 整块略过，链接读文字，行内标记去符号留内容。流式期间对未闭合的 fenced 块整体暂扣，
 * 保证已消费前缀稳定（与前端旧实现同构，此逻辑收编到服务端）。
 *
 * 非线程安全：由会话在单线程内使用。
 *
 * @author huxuehao
 */
public class SpeechSentenceFeeder {

    /** 单段合成的最大字符数（克隆模式下句子越短首声越快，上限防超长粘段） */
    private static final int MAX_SEGMENT_LENGTH = 120;

    /**
     * 渐进式颗粒度：短句合成时间被克隆前缀支配，首句越短首声越快；
     * 但短句播放时间也短，需要连续几个小颗粒句累积播放缓冲，
     * 颗粒度才能平滑爬升到正常（否则首句播完下一句还没合成好，中间断流）。
     * 前三句遇任何标点（含逗号顿号）按 2/6/8 字门槛即发，第四句起恢复句末断句。
     */
    private static final int[] EAGER_MIN_LENGTHS = {2, 6, 8};
    private static final int MIN_SEGMENT_LENGTH = 10;

    /** 句末边界（中英句末标点与换行；分号/冒号视为可断点） */
    private static final String SENTENCE_BOUNDARY = "。！？；：!?;\n";

    /** 爬坡期的软边界（句末边界 + 逗号顿号），换更小颗粒度尽快出声 */
    private static final String EAGER_BOUNDARY = SENTENCE_BOUNDARY + "，、,";

    private static final Pattern FENCED_BLOCK = Pattern.compile("(```|~~~)[^\\n]*\\n[\\s\\S]*?\\1[^\\n]*(\\n|$)");
    private static final Pattern OPEN_FENCE = Pattern.compile("(^|\\n)(```|~~~)");
    private static final Pattern IMAGE = Pattern.compile("!\\[[^\\]]*\\]\\([^)]*\\)");
    private static final Pattern LINK = Pattern.compile("\\[([^\\]]*)\\]\\([^)]*\\)");
    private static final Pattern HEADING = Pattern.compile("(?m)^#{1,6}\\s+");
    private static final Pattern BLOCKQUOTE = Pattern.compile("(?m)^\\s*>\\s?");
    private static final Pattern LIST_PREFIX = Pattern.compile("(?m)^\\s*([-*+]|\\d+\\.)\\s+");
    private static final Pattern INLINE_CODE = Pattern.compile("`([^`]*)`");
    private static final Pattern BOLD = Pattern.compile("(\\*\\*|__)(.*?)\\1");
    private static final Pattern ITALIC = Pattern.compile("([*_])(.*?)\\1");
    private static final Pattern STRIKE = Pattern.compile("~~(.*?)~~");
    private static final Pattern HR = Pattern.compile("(?m)^\\s*([-*_]\\s*){3,}$");
    private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");
    private static final Pattern SPACES = Pattern.compile("[ \\t]+");
    private static final Pattern MULTI_NEWLINE = Pattern.compile("\\n{2,}");

    private final StringBuilder markdown = new StringBuilder();
    private int consumed = 0;
    private int emittedCount = 0;

    /**
     * 喂入 markdown 增量，返回新凑成的可合成句子（可能为空）
     */
    public List<String> feed(String delta) {
        if (delta != null && !delta.isEmpty()) {
            markdown.append(delta);
        }
        return extract(false);
    }

    /**
     * 正文结束：把未到句末的尾巴也吐出
     */
    public List<String> flush() {
        return extract(true);
    }

    private List<String> extract(boolean isFinal) {
        String speech = toSpeechText(markdown.toString());
        // 提纯结果偶发前缀回缩（未闭合标记转闭合），越界时回退重对齐
        if (consumed > speech.length()) {
            consumed = speech.length();
        }
        int pendingBase = consumed;
        String pending = speech.substring(consumed);
        List<String> out = new ArrayList<>();
        if (pending.isEmpty()) {
            return out;
        }

        StringBuilder buf = new StringBuilder();
        for (int i = 0; i < pending.length(); i++) {
            char ch = pending.charAt(i);
            buf.append(ch);
            boolean eager = emittedCount < EAGER_MIN_LENGTHS.length;
            String boundarySet = eager ? EAGER_BOUNDARY : SENTENCE_BOUNDARY;
            int minLen = eager ? EAGER_MIN_LENGTHS[emittedCount] : MIN_SEGMENT_LENGTH;
            boolean boundary = boundarySet.indexOf(ch) >= 0 && buf.toString().trim().length() >= minLen;
            if (boundary || buf.length() >= MAX_SEGMENT_LENGTH) {
                String seg = buf.toString().trim();
                if (!seg.isEmpty()) {
                    out.add(seg);
                    emittedCount++;
                }
                consumed = pendingBase + i + 1;
                buf.setLength(0);
            }
        }
        if (isFinal && !buf.toString().trim().isEmpty()) {
            out.add(buf.toString().trim());
            consumed = pendingBase + pending.length();
            emittedCount++;
        }
        return out;
    }

    /**
     * markdown → 朗读文本（与前端旧版 toSpeechText 同构）
     */
    static String toSpeechText(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return "";
        }
        String text = FENCED_BLOCK.matcher(markdown).replaceAll("\n");
        // 未闭合的 fenced 块：从起始处截断（流式期间暂扣，闭合后上一条规则接管）
        var openMatcher = OPEN_FENCE.matcher(text);
        if (openMatcher.find()) {
            text = text.substring(0, openMatcher.start());
        }
        text = IMAGE.matcher(text).replaceAll("");
        text = LINK.matcher(text).replaceAll("$1");
        // 表格行与分隔行整行略过
        StringBuilder kept = new StringBuilder();
        for (String line : text.split("\n", -1)) {
            String trimmed = line.trim();
            if (trimmed.startsWith("|") || trimmed.matches("^[-:| ]+$") && trimmed.contains("-")) {
                continue;
            }
            kept.append(line).append('\n');
        }
        text = kept.toString();
        text = HEADING.matcher(text).replaceAll("");
        text = BLOCKQUOTE.matcher(text).replaceAll("");
        text = LIST_PREFIX.matcher(text).replaceAll("");
        text = INLINE_CODE.matcher(text).replaceAll("$1");
        text = BOLD.matcher(text).replaceAll("$2");
        text = ITALIC.matcher(text).replaceAll("$2");
        text = STRIKE.matcher(text).replaceAll("$1");
        text = HR.matcher(text).replaceAll("");
        text = HTML_TAG.matcher(text).replaceAll("");
        text = SPACES.matcher(text).replaceAll(" ");
        text = MULTI_NEWLINE.matcher(text).replaceAll("\n");
        return text.trim();
    }
}
