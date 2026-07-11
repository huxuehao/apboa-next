package com.hxh.apboa.node.base.security;

import java.util.Collections;
import java.util.List;

/**
 * 安全检查结果记录
 */
public record SecurityCheckResult(boolean isSafe, List<String> errors, List<String> warnings) {
    public SecurityCheckResult(boolean isSafe, List<String> errors, List<String> warnings) {
        this.isSafe = isSafe;
        this.errors = Collections.unmodifiableList(errors);
        this.warnings = Collections.unmodifiableList(warnings);
    }
}
