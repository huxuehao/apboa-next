package com.hxh.apboa.mq.controller;

import com.hxh.apboa.common.r.R;
import com.hxh.apboa.mq.entity.Mq;
import com.hxh.apboa.mq.service.MqService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 描述：消息队列控制器
 *
 * @author huxuehao
 **/
@RestController
@RequestMapping("/mq")
@RequiredArgsConstructor
public class MqController {
    private final MqService mqService;

    /**
     * 描述：添加消息队列
     *
     * @param mq 消息队列
     * @return 添加结果
     */
    @PostMapping
    public R<?> add(@RequestBody Mq mq) {
        return R.data(mqService.save(mq));
    }

    /**
     * 描述：更新消息队列
     *
     * @param mq 消息队列
     * @return 更新结果
     */
    @PutMapping
    public R<?> update(@RequestBody Mq mq) {
        return R.data(mqService.updateById(mq));
    }

    /**
     * 描述：删除消息队列
     *
     * @param mqIds 消息队列ID
     * @return 删除结果
     */
    @DeleteMapping("{force}")
    public R<?> deleteMq(@PathVariable("force") Integer force, @RequestBody List<String> mqIds) {
        return R.data(mqService.deleteMq(force, mqIds));
    }

    /**
     * 描述：查询消息队列列表
     *
     * @return 消息队列列表
     */
    @GetMapping
    public R<List<Mq>> list() {
        return R.data(mqService.list());
    }

    /**
     * 描述：查询消息队列
     *
     * @param mqId 消息队列ID
     * @return 消息队列
     */
    @GetMapping("/{mqId}")
    public R<Mq> get(@PathVariable("mqId") String mqId) {
        return R.data(mqService.getById(mqId));
    }

    /**
     * 描述：启用消息队列
     *
     * @param mqId 消息队列ID
     * @param v    启用状态
     * @return 启用结果
     */
    @PutMapping("/{mqId}/enable/{v}")
    public R<?> enable(@PathVariable("mqId") String mqId, @PathVariable("v") Integer v) {
        return R.data(mqService.updateEnable(mqId, v));
    }

    @PostMapping("/check/connect")
    public R<?> checkConnect(@RequestBody Mq mq) {
        return R.fail("暂未实现");
    }
}
