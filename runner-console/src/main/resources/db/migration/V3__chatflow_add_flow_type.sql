ALTER TABLE `workflow`
    ADD COLUMN `flow_type` VARCHAR(16) NOT NULL DEFAULT 'WORKFLOW' COMMENT '流程类型: WORKFLOW | CHATFLOW';

CREATE INDEX `idx_workflow_flow_type` ON `workflow`(`flow_type`);
