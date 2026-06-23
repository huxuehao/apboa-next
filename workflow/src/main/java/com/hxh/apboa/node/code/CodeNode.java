package com.hxh.apboa.node.code;

import com.hxh.apboa.common.util.FuncUtils;
import com.hxh.apboa.node.base.EnhancedNode;
import com.hxh.apboa.node.base.NodeOutput;
import com.hxh.apboa.node.base.NodeType;
import com.hxh.apboa.node.base.context.NodeContext;
import com.hxh.apboa.node.base.security.SecurityCheckResult;
import com.hxh.apboa.node.base.security.SecurityChecker;
import com.hxh.apboa.node.base.security.SecurityCheckerFactory;
import com.hxh.apboa.node.base.verify.VerifyFail;
import com.hxh.apboa.node.base.verify.VerifyResult;
import com.hxh.apboa.node.code.load.InstanceLoadFactory;
import lombok.Getter;

import java.util.Map;

/**
 * 描述：代码执行节点
 *
 * @author huxuehao
 **/
public class CodeNode extends EnhancedNode {
    @Getter
    private final Config config;

    public CodeNode(String id, String name, Config config) {
        super(id, name, NodeType.CODE);
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
        CodeExecutor codeExecutor = InstanceLoadFactory.getInstanceLoader(config.getLanguage())
                .loadInstance(config.getCodeSource());
        if (codeExecutor == null) {
            throw new Exception("无法加载代码执行对象");
        }

        // 执行代码
        Map<String, Object> codeExecuteResult = codeExecutor.execute(inputs);

        // 保存输出
        codeExecuteResult.forEach(output::addOutput);
        output.markComplete();

        return output;
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
        // 代码语言
        if (FuncUtils.isEmpty(config.getLanguage())) {
            return VerifyResult.invalid(new VerifyFail("language", "代码语言不能为空"));
        }

        // 代码源
        if (FuncUtils.isEmpty(config.getCodeSource())) {
            return VerifyResult.invalid(new VerifyFail("codeSource", "代码源不能为空"));
        }

        // 代码安全检查
        SecurityChecker checker = SecurityCheckerFactory.getChecker(config.getLanguage());
        if (checker != null) {
            SecurityCheckResult checkResult = checker.checkCodeSecurity(config.getCodeSource());
            if (!checkResult.isSafe()) {
                VerifyResult invalid = VerifyResult.invalid();
                checkResult.errors().forEach(error -> invalid.addError("[error]codeSource", error));
                checkResult.warnings().forEach(warning -> invalid.addError("[warning]codeSource", warning));
                return invalid;
            }
        } else {
            return VerifyResult.invalid(new VerifyFail("language", "不支持的代码语言"));
        }

        return VerifyResult.valid();
    }
}
