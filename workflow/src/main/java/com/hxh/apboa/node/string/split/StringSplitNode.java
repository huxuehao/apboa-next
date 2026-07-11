package com.hxh.apboa.node.string.split;

import com.hxh.apboa.common.consts.NodeConst;
import com.hxh.apboa.common.util.FuncUtils;
import com.hxh.apboa.node.base.EnhancedNode;
import com.hxh.apboa.node.base.NodeOutput;
import com.hxh.apboa.common.enums.NodeType;
import com.hxh.apboa.node.base.context.NodeContext;
import com.hxh.apboa.node.base.verify.VerifyFail;
import com.hxh.apboa.node.base.verify.VerifyResult;
import lombok.Getter;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 描述：字符串分割节点
 *
 * @author huxuehao
 **/
public class StringSplitNode extends EnhancedNode {
    @Getter
    private final Config config;

    public StringSplitNode(String id, String name, Config config) {
        super(id, name, NodeType.STRING_SPLIT);
        this.config = config;
    }

    @Override
    protected NodeOutput doExecute(Map<String, Object> inputs, NodeOutput output, NodeContext context) {
        try {
            return successNodeOutput(inputs, output);
        } catch (Exception e) {
            return executionNodeOutput(e,  output);
        }
    }

    /**
     * 创建成功输出
     * @param inputs 节点输入
     * @param output 节点输出
     * @return 节点输出
     */
    private NodeOutput successNodeOutput(Map<String, Object> inputs, NodeOutput output) throws Exception {
        Object inputString = inputs.get(NodeConst.DEFAULT_INPUT_NAME);
        List<String> resultList;
        if (inputString instanceof String) {
            resultList = switch (config.getMode()) {
                case SIMPLE -> simpleSplit(inputString.toString(), config.getDelimiter());
                case REGEX -> regexSplit(inputString.toString(), config.getDelimiter());
                case FIXED_LENGTH -> fixedLengthSplit(inputString.toString(), config.getDelimiter());
                case LINE_BREAK -> lineBreakSplit(inputString.toString());
                case KEY_VALUE -> keyValueSplit(inputString.toString(), config.getDelimiter());
                case MULTIPLE_DELIMITERS -> multipleDelimitersSplit(inputString.toString(), config.getDelimiters());
                default -> throw new Exception("不支持的分割模式");
            };
        } else {
            throw new Exception("输入参数必须是字符串");
        }

        // 将字符串分割信息追加到执行上下文中
        output.addExecutionContext("splitMode", config.getMode().name());
        output.addExecutionContext("delimiter", config.getDelimiter());

        output.addOutput(NodeConst.DEFAULT_OUTPUT_NAME, applyPostProcessing(resultList));
        output.markComplete();
        return output;
    }

    /**
     * 简单分割
     * @param input 输入字符串
     * @param delimiter 分隔符
     */
    private List<String> simpleSplit(String input, String delimiter) {
        if (delimiter == null || delimiter.isEmpty()) {
            return Collections.singletonList(input);
        }

        String[] parts = input.split(Pattern.quote(delimiter), config.getLimit());
        return Arrays.asList(parts);
    }

    /**
     * 正则分割
     * @param input 输入字符串
     * @param regex 正则表达式
     */
    private List<String> regexSplit(String input, String regex) {
        if (regex == null || regex.isEmpty()) {
            return Collections.singletonList(input);
        }

        String[] parts = input.split(regex, config.getLimit());
        return Arrays.asList(parts);
    }

    /**
     * 固定长度分割
     * @param input 输入字符串
     * @param lengthStr 分割长度
     */
    private List<String> fixedLengthSplit(String input, String lengthStr) {
        try {
            int chunkSize = Integer.parseInt(lengthStr);
            if (chunkSize <= 0) {
                return Collections.singletonList(input);
            }

            List<String> result = new ArrayList<>();
            for (int i = 0; i < input.length(); i += chunkSize) {
                int end = Math.min(input.length(), i + chunkSize);
                result.add(input.substring(i, end));

                // 检查限制
                if (config.getLimit() > 0 && result.size() >= config.getLimit()) {
                    break;
                }
            }
            return result;
        } catch (NumberFormatException e) {
            throw new RuntimeException("固定长度分割参数必须是数字: " + lengthStr, e);
        }
    }

    /**
     * 换行符分割
     * @param input 输入字符串
     */
    private List<String> lineBreakSplit(String input) {
        String[] parts = input.split("\\r?\\n|\\r", config.getLimit());
        return Arrays.asList(parts);
    }

    /**
     * 键值对分割
     * @param input 输入字符串
     * @param pairDelimiter 分隔符
     */
    private List<String> keyValueSplit(String input, String pairDelimiter) {
        String keyValueDelimiter = config.getKeyValueDelimiter();
        List<String> result = new ArrayList<>();

        // 先按对分割
        String[] pairs = input.split(Pattern.quote(pairDelimiter), config.getLimit());

        String processedPair;
        for (String pair : pairs) {
            // 再按键值分割符分割
            String[] keyValue = pair.split(Pattern.quote(keyValueDelimiter), 2);
            if (keyValue.length == 2) {
                processedPair = formatKeyValuePair(keyValue[0].trim(), keyValue[1].trim());
            } else if (keyValue.length == 1) {
                processedPair = formatKeyValuePair(keyValue[0].trim(), null);
            } else {
                continue;
            }

            result.add(processedPair);
        }

        return result;
    }

    /**
     * 格式化键值对输出
     */
    private String formatKeyValuePair(String key, String value) {
        return switch (config.getKeyValueOutputFormat()) {
            case EQUALS_SEPARATED -> key + "=" + value;
            case JSON_OBJECT -> "{\"" + key + "\": \"" + value + "\"}";
            case MAP_ENTRY -> key + " -> " + value;
            case CUSTOM -> String.format(config.getKeyValueCustomFormat(), key, value);
            default -> key + ": " + value;
        };
    }

    /**
     * 多个分隔符分割
     * @param input 输入字符串
     * @param delimiters 分隔符集合
     */
    private List<String> multipleDelimitersSplit(String input, List<String> delimiters) {
        String regex = delimiters.stream()
                .map(Pattern::quote)
                .collect(Collectors.joining("|"));

        String[] parts = input.split(regex, config.getLimit());
        return Arrays.asList(parts);
    }

    /**
     * 应用后处理
     * @param parts 分割后的字符串列表
     */
    private List<String> applyPostProcessing(List<String> parts) {
        if (parts.isEmpty()) {
            return Collections.emptyList();
        }

        // 计算最优容量
        int initialCapacity = config.getMaxResults() > 0
                ? Math.min(parts.size(), config.getMaxResults())
                : parts.size();
        List<String> processed = new ArrayList<>(initialCapacity);

        boolean hasProcessingResult = config.isProcessingResult();
        boolean hasPrefixOrSuffix = config.getPrefix() != null || config.getSuffix() != null;
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (config.getMaxResults() > 0 && processed.size() >= config.getMaxResults()) {
                break;
            }

            if (config.isTrimParts()) {
                part = part.trim();
            }

            if (config.isRemoveEmpty() && part.isEmpty()) {
                continue;
            }

            if (hasProcessingResult && hasPrefixOrSuffix) {
                sb.setLength(0);
                if (config.getPrefix() != null) {
                    sb.append(config.getPrefix());
                }
                sb.append(part);
                if (config.getSuffix() != null) {
                    sb.append(config.getSuffix());
                }
                part = sb.toString();
            }

            processed.add(part);
        }

        return processed;
    }

    /**
     * 异常节点输出
     */
    private NodeOutput executionNodeOutput(Exception e, NodeOutput output) {
        output.markFailed( getName() + "执行失败: " + e.getMessage());
        return output;
    }

    @Override
    public VerifyResult verifyConfig(Map<String, Object> inputs) {
        if (FuncUtils.isEmpty(config.getDelimiter())) {
            if (config.getMode() != SplitMode.LINE_BREAK && config.getMode() != SplitMode.MULTIPLE_DELIMITERS) {
                return VerifyResult.invalid(new VerifyFail("delimiter", "分隔符不能为空"));
            }
        }

        if (config.getMode() == SplitMode.MULTIPLE_DELIMITERS && FuncUtils.isEmpty(config.getDelimiters())) {
            return VerifyResult.invalid(new VerifyFail("delimiters", "多个分隔符不能为空"));
        }

        if (inputs.get("is_debug_verify") == null) {
            if (!(inputs.get(NodeConst.DEFAULT_INPUT_NAME) instanceof String)) {
                return VerifyResult.invalid(new VerifyFail("input", "输入数据必须是字符串"));
            }
        }

        if (config.getMode() == null) {
            return VerifyResult.invalid(new VerifyFail("mode", "分割模式不能为空"));
        } else if (config.getMode() == SplitMode.KEY_VALUE && FuncUtils.isEmpty(config.getKeyValueDelimiter())) {
            return VerifyResult.invalid(new VerifyFail("keyValueDelimiter", "键值分隔符不能为空"));
        }

        return VerifyResult.valid();
    }
}
