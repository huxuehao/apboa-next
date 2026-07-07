package com.hxh.apboa.console.workflow;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hxh.apboa.common.entity.Workflow;
import com.hxh.apboa.common.entity.WorkflowNodeExecution;
import com.hxh.apboa.common.entity.WorkflowRun;
import com.hxh.apboa.common.entity.WorkflowVersion;
import com.hxh.apboa.common.mp.support.MP;
import com.hxh.apboa.common.r.R;
import com.hxh.apboa.workflowbiz.dto.WorkflowQueryDTO;
import com.hxh.apboa.workflowbiz.dto.WorkflowRunQueryDTO;
import com.hxh.apboa.workflowbiz.dto.WorkflowRunRequest;
import com.hxh.apboa.workflowbiz.service.WorkflowMetadataService;
import com.hxh.apboa.workflowbiz.service.WorkflowRunService;
import com.hxh.apboa.workflowbiz.service.WorkflowService;
import com.hxh.apboa.workflowbiz.vo.NodeMetadata;
import com.hxh.apboa.workflowbiz.vo.WorkflowDetailVO;
import com.hxh.apboa.workflowbiz.vo.WorkflowRunResult;
import com.hxh.apboa.workflowbiz.vo.WorkflowValidationResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/workflow")
@RequiredArgsConstructor
public class WorkflowController {
    private final WorkflowService workflowService;
    private final WorkflowRunService workflowRunService;
    private final WorkflowMetadataService workflowMetadataService;

    @GetMapping("/page")
    public R<IPage<Workflow>> page(WorkflowQueryDTO query) {
        return R.data(workflowService.page(MP.getPage(query), MP.getQueryWrapper(query)));
    }

    @GetMapping("/{id}")
    public R<WorkflowDetailVO> detail(@PathVariable("id") Long id) {
        return R.data(workflowService.detail(id));
    }

    @PostMapping
    public R<Workflow> save(@RequestBody Workflow workflow) {
        workflowService.saveWorkflow(workflow);
        return R.data(workflow);
    }

    @PutMapping
    public R<Boolean> update(@RequestBody Workflow workflow) {
        return R.data(workflowService.updateWorkflow(workflow));
    }

    @DeleteMapping("/{force}")
    public R<Boolean> delete(@PathVariable("force") Integer force, @RequestBody List<Long> ids) {
        return R.data(workflowService.deleteWorkflow(force, ids));
    }

    @PostMapping("/{id}/copy")
    public R<Workflow> copy(@PathVariable("id") Long id) {
        return R.data(workflowService.copyWorkflow(id));
    }

    @PutMapping("/{id}/lock/{locked}")
    public R<Boolean> lock(@PathVariable("id") Long id, @PathVariable("locked") Integer locked) {
        return R.data(workflowService.lockWorkflow(id, locked));
    }

    @PostMapping("/{id}/validate")
    public R<WorkflowValidationResult> validate(@PathVariable("id") Long id) {
        return R.data(workflowService.validateWorkflow(id));
    }

    @PostMapping("/{id}/publish")
    public R<WorkflowVersion> publish(@PathVariable("id") Long id,
                                      @RequestParam(value = "remark", required = false) String remark) {
        return R.data(workflowService.publish(id, remark));
    }

    @GetMapping("/{id}/versions")
    public R<List<WorkflowVersion>> versions(@PathVariable("id") Long id) {
        return R.data(workflowService.versions(id));
    }

    @PostMapping("/{id}/versions/{version}/rollback")
    public R<Workflow> rollback(@PathVariable("id") Long id, @PathVariable("version") String version) {
        return R.data(workflowService.rollback(id, version));
    }

    @DeleteMapping("/{id}/versions/{version}")
    public R<Boolean> deleteVersion(@PathVariable("id") Long id, @PathVariable("version") String version) {
        return R.data(workflowService.deleteVersion(id, version));
    }

//    @PostMapping("/{id}/debug-run")
//    public R<WorkflowRunResult> debugRun(@PathVariable("id") Long id, @RequestBody(required = false) WorkflowRunRequest request) {
//        return R.data(workflowRunService.debugRun(id, request));
//    }
//
//    @PostMapping("/{id}/run")
//    public R<WorkflowRunResult> run(@PathVariable("id") Long id, @RequestBody(required = false) WorkflowRunRequest request) {
//        return R.data(workflowRunService.run(id, request));
//    }

    @GetMapping("/runs/page")
    public R<IPage<WorkflowRun>> runPage(WorkflowRunQueryDTO query) {
        return R.data(workflowRunService.page(MP.getPage(query), MP.getQueryWrapper(query)));
    }

    @GetMapping("/runs/{runId}")
    public R<WorkflowRun> runDetail(@PathVariable("runId") Long runId) {
        return R.data(workflowRunService.getById(runId));
    }

    @GetMapping("/runs/{runId}/nodes")
    public R<List<WorkflowNodeExecution>> nodeExecutions(@PathVariable("runId") Long runId) {
        return R.data(workflowRunService.nodeExecutions(runId));
    }

    @GetMapping("/node-metadata")
    public R<List<NodeMetadata>> nodeMetadata() {
        return R.data(workflowMetadataService.nodeMetadata());
    }
}
