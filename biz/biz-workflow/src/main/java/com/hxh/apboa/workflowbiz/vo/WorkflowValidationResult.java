package com.hxh.apboa.workflowbiz.vo;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class WorkflowValidationResult {
    private boolean valid = true;
    private List<WorkflowValidationError> errors = new ArrayList<>();
    private List<WorkflowValidationError> warnings = new ArrayList<>();

    public void addError(String nodeId, String field, String message) {
        valid = false;
        errors.add(new WorkflowValidationError(nodeId, field, message));
    }

    public void addWarning(String nodeId, String field, String message) {
        warnings.add(new WorkflowValidationError(nodeId, field, message));
    }

    public record WorkflowValidationError(String nodeId, String field, String message) {
    }
}
