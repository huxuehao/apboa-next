package com.hxh.apboa.common.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hxh.apboa.common.consts.TableConst;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 描述：任务运行记录，记录每次定时任务触发后关联的智能体会话或工作流运行
 * @author huxuehao
 **/
@Getter
@Setter
@TableName(TableConst.JOB_RECORD)
@NoArgsConstructor
@AllArgsConstructor
public class JobRecord {
    private Long jobId;
    private Long recordId;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
