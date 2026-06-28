package com.hxh.apboa.mq.controller;

import com.hxh.apboa.common.r.R;
import com.hxh.apboa.mq.entity.Mq;
import com.hxh.apboa.mq.service.MqService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/mq")
@RequiredArgsConstructor
public class MqController {
    private final MqService mqService;

    @PostMapping
    public R<?> add(@RequestBody Mq mq) {
        return R.data(mqService.save(mq));
    }

    @PutMapping
    public R<?> update(@RequestBody Mq mq) {
        return R.data(mqService.updateById(mq));
    }

    @DeleteMapping("{force}")
    public R<?> deleteMq(@PathVariable("force") Integer force, @RequestBody List<String> mqIds) {
        return R.data(mqService.deleteMq(force, mqIds));
    }

    @GetMapping
    public R<List<Mq>> list(@RequestParam(value = "enabled", required = false) Integer enabled) {
        return R.data(mqService.listByEnabled(enabled));
    }

    @GetMapping("/{mqId}")
    public R<Mq> get(@PathVariable("mqId") String mqId) {
        return R.data(mqService.getById(mqId));
    }

    @PutMapping("/{mqId}/enable/{v}")
    public R<?> enable(@PathVariable("mqId") String mqId, @PathVariable("v") Integer v) {
        return R.data(mqService.updateEnable(mqId, v));
    }

    @PostMapping("/check/connect")
    public R<?> checkConnect(@RequestBody Mq mq) {
        return R.data(mqService.checkConnect(mq));
    }
}
