package com.hxh.apboa.console.heartbeat;

import com.hxh.apboa.common.config.auth.PassAuth;
import com.hxh.apboa.common.config.auth.RoleNeed;
import com.hxh.apboa.common.enums.TenantRole;
import com.hxh.apboa.common.r.R;
import com.hxh.apboa.console.heartbeat.model.HeartbeatOverviewVO;
import com.hxh.apboa.console.heartbeat.model.NodeStatusVO;
import com.hxh.apboa.console.heartbeat.model.WebSocketNodeVO;
import com.hxh.apboa.heartbeat.HeartbeatPayload;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

/**
 * 描述：心跳上报与节点状态查询接口
 *
 * @author huxuehao
 **/
@RestController
@RequestMapping("/heartbeat")
@RequiredArgsConstructor
public class HeartbeatController {

    private final NodeRegistry nodeRegistry;

    private final WebSocketNodeRegistry webSocketNodeRegistry;

    /**
     * 接收执行节点心跳上报（异步非阻塞，无需用户鉴权）
     *
     * @param payload 心跳请求体
     * @return 异步操作结果
     */
    @PassAuth
    @PostMapping("/report")
    public Mono<R<Void>> report(@RequestBody HeartbeatPayload payload) {
        return Mono.fromRunnable(() -> nodeRegistry.report(payload))
                .subscribeOn(Schedulers.boundedElastic())
                .then(Mono.just(R.success("操作成功")));
    }

    /**
     * 查询所有执行节点状态（管理员权限）
     *
     * @return 节点状态列表
     */
    @RoleNeed({TenantRole.TENANT_ADMIN})
    @GetMapping("/nodes")
    public R<List<NodeStatusVO>> listNodes() {
        return R.data(nodeRegistry.getAllNodes());
    }

    /**
     * 节点监控总览（执行节点 + WebSocket 节点合一，管理员权限，减少设置页轮询请求数）
     *
     * @return 总览
     */
    @RoleNeed({TenantRole.TENANT_ADMIN})
    @GetMapping("/overview")
    public R<HeartbeatOverviewVO> overview() {
        HeartbeatOverviewVO vo = new HeartbeatOverviewVO();
        vo.setNodes(nodeRegistry.getAllNodes());
        vo.setWebsocketNodes(webSocketNodeRegistry.getAllNodes());
        return R.data(vo);
    }

    /**
     * 接收 WebSocket 服务心跳上报（异步非阻塞，无需用户鉴权）
     *
     * @param payload 心跳请求体
     * @return 异步操作结果
     */
    @PassAuth
    @PostMapping("/websocket/report")
    public Mono<R<Void>> reportWebSocket(@RequestBody HeartbeatPayload payload) {
        return Mono.fromRunnable(() -> webSocketNodeRegistry.report(payload))
                .subscribeOn(Schedulers.boundedElastic())
                .then(Mono.just(R.success("操作成功")));
    }

    /**
     * 查询所有 WebSocket 节点状态（管理员权限）
     *
     * @return WebSocket 节点状态列表
     */
    @RoleNeed({TenantRole.TENANT_ADMIN})
    @GetMapping("/websocket")
    public R<List<WebSocketNodeVO>> listWebSocketNodes() {
        return R.data(webSocketNodeRegistry.getAllNodes());
    }
}
