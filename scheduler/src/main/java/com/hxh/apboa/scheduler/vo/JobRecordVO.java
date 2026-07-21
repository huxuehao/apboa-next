package com.hxh.apboa.scheduler.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 任务运行记录 VO
 *
 * @author huxuehao
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class JobRecordVO {
    /**
     * 任务ID
     */
    private Long jobId;

    /**
     * 记录ID（智能体任务时指向 ChatSession.id，工作流任务时指向 WorkflowRun.id）
     */
    private Long recordId;

    /**
     * 记录创建时间
     */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
