package com.hxh.apboa.channel.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hxh.apboa.channel.entity.Channel;
import com.hxh.apboa.channel.service.ChannelService;
import com.hxh.apboa.common.mp.support.MP;
import com.hxh.apboa.common.mp.support.PageParams;
import com.hxh.apboa.common.r.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 通知渠道管理控制器
 *
 * @author huxuehao
 */
@RestController
@RequestMapping("/channel")
@RequiredArgsConstructor
public class ChannelController {
    private final ChannelService channelService;

    @PostMapping
    public R<?> add(@RequestBody Channel channel) {
        return R.data(channelService.save(channel));
    }

    @PutMapping
    public R<?> update(@RequestBody Channel channel) {
        return R.data(channelService.updateChannel(channel));
    }

    @DeleteMapping("{force}")
    public R<?> deleteChannel(@PathVariable("force") Integer force, @RequestBody List<String> channelIds) {
        return R.data(channelService.deleteChannel(force, channelIds));
    }

    @GetMapping("/page")
    public R<IPage<Channel>> page(PageParams pageParams, Channel query) {
        return R.data(channelService.page(MP.getPage(pageParams), MP.getQueryWrapper(query)));
    }

    @GetMapping
    public R<List<Channel>> list(@RequestParam(value = "enabled", required = false) Integer enabled) {
        return R.data(channelService.listByEnabled(enabled));
    }

    @GetMapping("/{channelId}")
    public R<Channel> get(@PathVariable("channelId") String channelId) {
        return R.data(channelService.getById(channelId));
    }

    @PutMapping("/{channelId}/enable/{v}")
    public R<?> enable(@PathVariable("channelId") String channelId, @PathVariable("v") Integer v) {
        return R.data(channelService.updateEnable(channelId, v));
    }

    @PostMapping("/check/connect")
    public R<?> checkConnect(@RequestBody Channel channel) {
        return R.data(channelService.checkConnect(channel));
    }

    @PostMapping("/{channelId}/check/connect")
    public R<?> checkSavedConnect(@PathVariable("channelId") String channelId) {
        return R.data(channelService.checkSavedConnect(channelId));
    }
}
