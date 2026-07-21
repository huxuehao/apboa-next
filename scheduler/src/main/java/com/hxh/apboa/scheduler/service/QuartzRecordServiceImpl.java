package com.hxh.apboa.scheduler.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hxh.apboa.common.entity.JobRecord;
import com.hxh.apboa.scheduler.mapper.JobRecordMapper;
import org.springframework.stereotype.Service;

/**
 * @author huxuehao
 **/
@Service
public class QuartzRecordServiceImpl extends ServiceImpl<JobRecordMapper, JobRecord> implements QuartzRecordService {
}
