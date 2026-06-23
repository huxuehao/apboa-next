package com.hxh.apboa.node.iterate;

/**
 * 描述：代码执行接口
 * Java代码的GULES脚本需要实现该方法
 *
 * @author huxuehao
 **/
public interface IteratorExecutor {
    /**
     * 执行代码
     *
     * @param item 列表对象
     * @param index 列表索引
     * @return 输出参数
     */
    Object doIterate(Object item, Integer  index) throws Exception;
}
