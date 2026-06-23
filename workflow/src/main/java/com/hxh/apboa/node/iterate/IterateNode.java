package com.hxh.apboa.node.iterate;

import com.hxh.apboa.common.consts.NodeConst;
import com.hxh.apboa.common.util.FuncUtils;
import com.hxh.apboa.node.base.EnhancedNode;
import com.hxh.apboa.node.base.NodeOutput;
import com.hxh.apboa.node.base.NodeType;
import com.hxh.apboa.node.base.context.NodeContext;
import com.hxh.apboa.node.base.feature.LoopableNode;
import com.hxh.apboa.node.base.security.SecurityCheckResult;
import com.hxh.apboa.node.base.security.SecurityChecker;
import com.hxh.apboa.node.base.security.SecurityCheckerFactory;
import com.hxh.apboa.node.base.verify.VerifyFail;
import com.hxh.apboa.node.base.verify.VerifyResult;
import com.hxh.apboa.node.iterate.load.InstanceLoadFactory;
import lombok.Getter;

import java.util.*;

/**
 * 描述：迭代节点
 *
 * @author huxuehao
 **/
public class IterateNode extends EnhancedNode implements LoopableNode {
    @Getter
    private Config config;
    public IterateNode(String id, String name, Config config) {
        super(id, name, NodeType.ITERATE);
        this.config = config;
    }

    @Override
    protected NodeOutput doExecute(Map<String, Object> inputs, NodeOutput output, NodeContext context) {
        try {
            return successNodeOutput(inputs, output);
        } catch (Exception e) {
            return executionNodeOutput(e, output);
        }
    }

    /**
     * 创建成功输出
     * @param inputs 节点输入
     * @param output 节点输出
     * @return 节点输出
     */
    private NodeOutput successNodeOutput(Map<String, Object> inputs, NodeOutput output) throws Exception {
        // 获取代码执行对象
        IteratorExecutor iteratorExecutor = InstanceLoadFactory.getInstanceLoader(config.getLanguage())
                .loadInstance(config.getIterateCode());
        if (iteratorExecutor == null) {
            throw new Exception("无法加载迭代器代码执行对象");
        }

        Object input = inputs.get(NodeConst.DEFAULT_INPUT_NAME);
        // 迭代每一个元素，并将其处理并添加到输出结果中
        processCollectionInput(input, iteratorExecutor, output);

        output.markComplete();
        return output;
    }

    /**
     * 处理集合输入
     * @param input 输入
     * @param iteratorExecutor 迭代器代码执行对象
     * @param output 输出
     */
    private void processCollectionInput(Object input, IteratorExecutor iteratorExecutor, NodeOutput output) throws Exception {
        Collection<?> inputCollection = (Collection<?>) input;
        Iterator<?> iterator = inputCollection.iterator();
        List<Object> outputList = new LinkedList<>();

        int index = 0;
        while (iterator.hasNext()) {
            Object item = iterator.next();
            try {
                // 执行迭代器代码处理每个元素，获取处理结果
                Object result = iteratorExecutor.doIterate(item, index);
                outputList.add(result);
            } catch (Exception e) {
                throw new Exception("处理元素时发生错误: " + e.getMessage(), e);
            } finally {
                index++;
            }
        }

        output.addOutput(NodeConst.DEFAULT_OUTPUT_NAME, outputList);
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
        Object input = inputs.get(NodeConst.DEFAULT_INPUT_NAME);
        if (!(input instanceof Iterable<?>)) {
            return VerifyResult.invalid(new VerifyFail("input", "输入必须是一个迭代器"));
        }

        // 代码语言
        if (FuncUtils.isEmpty(config.getLanguage())) {
            return VerifyResult.invalid(new VerifyFail("language", "代码语言不能为空"));
        }

        // 代码源
        if (FuncUtils.isEmpty(config.getIterateCode())) {
            return VerifyResult.invalid(new VerifyFail("iterateCode", "代码源不能为空"));
        }

        // 代码安全检查
        SecurityChecker checker = SecurityCheckerFactory.getChecker(config.getLanguage());
        if (checker != null) {
            SecurityCheckResult checkResult = checker.checkCodeSecurity(config.getIterateCode());
            if (!checkResult.isSafe()) {
                VerifyResult invalid = VerifyResult.invalid();
                checkResult.errors().forEach(error -> invalid.addError("[error]iterateCode", error));
                checkResult.warnings().forEach(warning -> invalid.addError("[warning]iterateCode", warning));
                return invalid;
            }
        } else {
            return VerifyResult.invalid(new VerifyFail("language", "不支持的代码语言"));
        }

        return VerifyResult.valid();
    }
}
