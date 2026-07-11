package com.hxh.apboa.workflowbiz.service;

import com.hxh.apboa.workflowbiz.vo.WorkflowValidationResult;

public interface WorkflowValidator {
    WorkflowValidationResult validate(Object definition);
}
