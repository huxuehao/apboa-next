package com.hxh.apboa.node.code.load;

import com.hxh.apboa.node.base.CodeLanguage;
import com.hxh.apboa.node.code.CodeExecutor;
import org.springframework.beans.factory.InitializingBean;

/**
 * 描述：实例加载接口
 *
 * @author huxuehao
 **/
public interface InstanceLoader extends InitializingBean {

    /**
     * 加载实例
     * @param codeSource 源代码
     * @return 实例
     */
    CodeExecutor loadInstance(String codeSource);

    /**
     * 获取语言
     * @return 语言
     */
    CodeLanguage getLanguage();

    /**
     * 初始化
     */
    @Override
    default void afterPropertiesSet() {
        // 完成注册
        InstanceLoadFactory.registerLoader(this);
    };
}
