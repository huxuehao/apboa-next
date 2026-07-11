package com.hxh.apboa.node.string.split;

/**
 * 描述：分割模式
 *
 * @author huxuehao
 **/
public enum SplitMode {
    SIMPLE, // 简单模式，按照单一符号进行分隔
    REGEX, // 正则模式，按照正则表达式进行分隔
    FIXED_LENGTH, // 固定长度模式，按照固定长度进行分隔
    LINE_BREAK, // 换行模式，按照换行符进行分隔
    KEY_VALUE, // 键值模式，按照键值分隔符进行分隔
    MULTIPLE_DELIMITERS // 多个分隔符模式，按照多个分隔符进行分隔
}
