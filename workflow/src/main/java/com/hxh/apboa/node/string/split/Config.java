package com.hxh.apboa.node.string.split;

import com.hxh.apboa.node.base.NodeConfig;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 描述：配置类
 *
 * @author huxuehao
 **/

@Getter
@Setter
public class Config implements NodeConfig {
    private SplitMode mode; // 分割模式
    private String delimiter; // 分隔符
    private List<String> delimiters; // 分隔符集合，当mode 为 MULTIPLE_DELIMITERS 时有效
    private boolean trimParts = true; // 是否对分割的结果进行trim
    private boolean removeEmpty = true; // 是否移除空字符串
    /**
     * 当 limit > 0 时, 正则表达式 regex 最多匹配 limit - 1 次，结果数组的长度不会超过 limit。
     *  - 例如，"a,b,c,d".split(",", 2) 会拆分成 ["a", "b,c,d"]（只拆分 1 次，结果数组长度为 2）
     * 当 limit = 0 时, 正则表达式会尽可能多地匹配（无限制次数），但结果数组会省略末尾的空字符串。
     *  - 例如，"a,b,,d,".split(",", 0) 会拆分成 ["a", "b", "", "d"]（末尾的空字符串被删除）
     * 当 limit < 0 时，正则表达式会尽可能多地匹配（无限制次数），且保留所有空字符串（包括末尾的）。
     *  - 例如，"a,b,,d,".split(",", -1) 会拆分成 ["a", "b", "", "d", ""]（保留末尾的空字符串）
     **/
    private int limit = -1; // 分割限制
    private int maxResults = -1; // 最大结果数

    /**
     * 处理切割结果
     * true: 可以添加前后缀
     * false: 不处理结果
     **/
    private boolean processingResult = true; // 处理切割结果
    private String prefix; // 键值对模式下的键
    private String suffix; // 键值对模式下的值

    private String keyValueDelimiter = "="; // 键值对分隔符
    private KeyValueOutputFormat keyValueOutputFormat = KeyValueOutputFormat.COLON_SEPARATED;
    /**
     * 自定义键值对输出格式，格式如下
     * "%s===>%s"
     * 最终在代码中会使用 String.format(keyValueCustom, key, value)
     */
    private String keyValueCustomFormat;
}
