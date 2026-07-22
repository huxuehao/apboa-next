package com.hxh.apboa.console.workflow;

import com.hxh.apboa.cache.service.CacheService;
import com.hxh.apboa.channel.entity.Channel;
import com.hxh.apboa.channel.service.ChannelService;
import com.hxh.apboa.common.entity.Cache;
import com.hxh.apboa.common.entity.Datasource;
import com.hxh.apboa.common.r.R;
import com.hxh.apboa.datasource.service.DatasourceService;
import com.hxh.apboa.mq.entity.Mq;
import com.hxh.apboa.mq.service.MqService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/workflow/resources")
@RequiredArgsConstructor
public class WorkflowResourceController {
    private final DatasourceService datasourceService;
    private final CacheService cacheService;
    private final MqService mqService;
    private final ChannelService channelService;

    @GetMapping("/summary")
    public R<Map<String, Long>> summary() {
        long datasourceTotal = datasourceService.count();
        long cacheTotal = cacheService.count();
        long mqTotal = mqService.count();
        long datasourceEnabled = datasourceService.lambdaQuery().eq(Datasource::getEnabled, true).count();
        long cacheEnabled = cacheService.lambdaQuery().eq(Cache::getEnabled, true).count();
        long mqEnabled = mqService.lambdaQuery().eq(Mq::getEnabled, true).count();
        long channelTotal = channelService.count();
        long channelEnabled = channelService.lambdaQuery().eq(Channel::getEnabled, true).count();

        Map<String, Long> summary = new LinkedHashMap<>();
        summary.put("total", datasourceTotal + cacheTotal + mqTotal + channelTotal);
        summary.put("datasourceTotal", datasourceTotal);
        summary.put("cacheTotal", cacheTotal);
        summary.put("mqTotal", mqTotal);
        summary.put("channelTotal", channelTotal);
        summary.put("datasourceEnabled", datasourceEnabled);
        summary.put("cacheEnabled", cacheEnabled);
        summary.put("mqEnabled", mqEnabled);
        summary.put("channelEnabled", channelEnabled);
        return R.data(summary);
    }
}
