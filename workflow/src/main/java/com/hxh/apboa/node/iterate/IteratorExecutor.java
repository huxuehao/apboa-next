package com.hxh.apboa.node.iterate;

/**
 * 描述：代码执行接口
 * Java代码的GULES脚本需要实现该方法
 *
 * @author huxuehao
 **/
public interface IteratorExecutor {
    /**
     * 处理迭代过程中的单个元素，该方法会在遍历可迭代对象时被逐一调用。
     *
     * @param item  当前迭代到的子元素
     * @param index 当前子元素在可迭代对象中的位置索引
     * @return 处理后的子元素对象
     */
    Object doIterate(Object item, Integer  index) throws Exception;
}
