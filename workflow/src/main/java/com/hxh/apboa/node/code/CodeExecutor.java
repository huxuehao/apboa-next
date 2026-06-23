package com.hxh.apboa.node.code;

import java.util.Map;

/**
 * 描述：代码执行接口
 * Java代码的GULES脚本需要实现该方法
 *
 * @author huxuehao
 **/
public interface CodeExecutor {
    /**
     * 执行代码
     *
     * @param inputs 输入参数
     * @return 输出参数
     */
    Map<String, Object> execute(Map<String, Object> inputs) throws Exception;
}
