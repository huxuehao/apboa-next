package com.hxh.apboa.node.base.verify;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 描述：检验配置的正确性和完整性
 *
 * @author huxuehao
 **/
@Getter
@Setter
public class VerifyResult {
    /**
     * 是否有效
     */
    private boolean valid;
    /**
     * 错误信息
     */
    private List<VerifyFail> errors;

    public VerifyResult(boolean valid) {
        this.valid = valid;
        this.errors = new ArrayList<>();
    }
    public VerifyResult(boolean valid, VerifyFail error) {
        this.valid = valid;
        this.errors = new ArrayList<>();
        this.errors.add(error);
    }

    /**
     * 创建一个有效的结果
     */
    public static VerifyResult valid() {
        return new VerifyResult(true);
    }
    /**
     * 创建一个无效的结果
     */
    public static VerifyResult invalid() {
        return new VerifyResult(false);
    }
    /**
     * 创建一个无效的结果
     */
    public static VerifyResult invalid(VerifyFail error) {
        return new VerifyResult(false, error);
    }

    /**
     * 添加错误信息
     */
    public void addError(String field, String message) {
        this.errors.add(new VerifyFail(field, message));
    }

    /**
     * 添加错误信息
     */
    public void addError(VerifyFail error) {
        this.errors.add(error);
    }
}
