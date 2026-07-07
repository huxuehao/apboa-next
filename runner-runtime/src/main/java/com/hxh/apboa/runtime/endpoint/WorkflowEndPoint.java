package com.hxh.apboa.runtime.endpoint;

import com.hxh.apboa.common.r.R;
import com.hxh.apboa.workflowbiz.dto.WorkflowRunRequest;
import com.hxh.apboa.workflowbiz.service.WorkflowRunService;
import com.hxh.apboa.workflowbiz.vo.WorkflowRunResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 描述：工作流端点
 *
 * @author huxuehao
 **/
@RestController
@RequestMapping("/runtime/workflow")
@RequiredArgsConstructor
public class WorkflowEndPoint {
    private final WorkflowRunService workflowRunService;

    @PostMapping("/{id}/debug-run")
    public R<WorkflowRunResult> debugRun(@PathVariable("id") Long id, @RequestBody(required = false) WorkflowRunRequest request) {
        return R.data(workflowRunService.debugRun(id, request));
    }

    @PostMapping("/{id}/run")
    public R<WorkflowRunResult> run(@PathVariable("id") Long id, @RequestBody(required = false) WorkflowRunRequest request) {
        return R.data(workflowRunService.run(id, request));
    }
}
