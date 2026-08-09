# Docker 环境网关端口暴露方案

本文记录网关端口在 Docker 部署下的源码结论、限制和可实施的改造方案。目标是：网关应用上线后，用户填写的端口可以从宿主机访问；应用下线、删除或进程退出后，端口同步释放。

## 1. 源码结论

当前网关数据面运行在 `runner-console` 进程中，而不是一个独立的网关容器。

- `GatewayLifecycleManager.onlineApp` 在 Vert.x 中调用 `listen(option.getPort())`，直接在当前 JVM 所在的网络命名空间监听端口。
- `DeployedApp.close` 调用 `server.close()`，应用下线已经能够释放容器内的监听端口。
- 管理面保存端口时只校验 `1024-65535`，端口唯一性由数据库约束保证；没有任何 Docker API 或宿主机端口编排逻辑。
- `GatewaySyncPublisher`/`GatewaySyncSubscriber` 只同步应用上线、下线、重置消息，不会改变容器的 `HostConfig.PortBindings`。
- `docker-compose-simple.yml` 和 `docker-compose-console.yml` 只映射控制台固定端口 `3060:3060`；Compose 创建容器后，不能动态增加或删除端口映射。

因此，下面几种看似简单的修改不能解决问题：

1. 只在 Dockerfile 增加 `EXPOSE`。`EXPOSE` 只是镜像元数据，不会发布宿主机端口。
2. 只增加 `ports: 1024-65535:1024-65535`。这会在容器创建时一次性占用整个宿主机端口范围，不能按应用销毁释放，并且很容易因宿主机已有服务启动失败。
3. 只让 Vert.x 监听 `0.0.0.0`。这只能保证容器网络内可访问，不能穿过 Docker bridge 到达宿主机。

## 2. 推荐方案：独立网关节点使用 host 网络

将网关数据面从 `runner-console` 拆成独立的 `runner-gateway` 服务，并让该服务使用宿主机网络（Linux Docker Engine）。网关 JVM 监听的端口就是宿主机端口，不需要静态端口映射；`server.close()` 后端口立即释放。

### 2.1 进程拆分

新建 `runner-gateway` Spring Boot 模块，依赖现有 `biz-gateway`、`gateway` 以及网关运行所需的基础模块。将当前控制台启动时加载网关的逻辑迁移到该模块；管理接口仍由 `runner-console` 提供，两个服务通过数据库和 Redis 通信。

网关服务需要提供以下配置：

```yaml
server:
  port: 3065
spring:
  main:
    web-application-type: none
```

`GatewayBootstrap`、`GatewaySyncSubscriber` 和 `WorkflowPublishedSubscriber` 保持在网关服务中。控制台继续发布 `APP_ONLINE`、`APP_OFFLINE`、`APP_RESET` 等消息，网关节点消费后调用现有 `GatewayLifecycleManager`。

### 2.2 Compose 配置

在控制台 Compose 文件中增加独立服务（示意）：

```yaml
  apboa-gateway:
    build:
      context: ..
      dockerfile: docker/gateway/Dockerfile
    network_mode: host
    restart: unless-stopped
    environment:
      SPRING_PROFILES_ACTIVE: docker
      SERVER_PORT: "3065"
      MYSQL_HOST: ${MYSQL_HOST}
      MYSQL_PORT: ${MYSQL_PORT}
      REDIS_HOST: ${REDIS_HOST}
      REDIS_PORT: ${REDIS_PORT}
      APBOA_PUBLIC_HOST: ${APBOA_PUBLIC_HOST}
    volumes:
      - ${LOG_PATH:-./logs}/gateway:/app/logs
      - ${DATA_PATH:-./data}/.apboa:/app/.apboa
```

宿主机防火墙只放行平台允许的端口范围；不要在 Compose 中预留整段 `ports`。由于 `network_mode: host` 与 `networks` 不能同时使用，网关服务访问 MySQL/Redis 时应使用宿主机可达地址（生产环境建议显式配置，不要依赖容器服务名）。Windows/macOS Docker Desktop 的 host 网络能力与 Linux 不同，生产部署应明确要求 Linux Docker Engine；非 Linux 环境使用下文的 sidecar 方案。

### 2.3 生命周期和故障恢复

网关上线、下线和重置必须都经过同一个生命周期适配器：

1. `onlineApp` 成功监听后记录 `appId -> port`，端口已经由宿主机网络直接提供。
2. `offlineApp` 先卸载路由，再关闭 `HttpServer`；关闭失败要记录并重试，不能仅从内存 Map 删除。
3. 网关服务启动时从数据库恢复在线应用；监听失败时保留失败日志并继续恢复其他应用。
4. 删除应用前先发送 `APP_OFFLINE`，数据库删除事务提交后再广播；现有 `deleteApps` 的消息机制可以继续使用。
5. 多网关节点部署时，端口必须按节点分配，或确保同一端口只在一个网关节点上线；否则 host 网络下会产生真实的宿主机端口冲突。

## 3. 不拆分进程时的可行方案：动态 sidecar 转发

如果暂时不能拆分 `runner-console`，可以增加 `GatewayPortBindingManager`，通过 Docker Engine API（挂载 `/var/run/docker.sock`）为每个在线应用创建一个轻量 TCP 转发 sidecar：

```text
宿主机:port -> sidecar(发布 port) -> apboa-console:port
```

上线成功后创建并记录 sidecar 名称（例如 `apboa-gateway-<appId>`）；下线、删除、端口重置和应用重启时删除 sidecar。启动恢复时按数据库在线状态对账，清理没有对应应用的孤儿 sidecar。该方案不需要预占整段端口，且删除 sidecar 就能释放宿主机端口。

必须满足以下条件：

- 使用 Docker Engine API，不要拼接执行 `docker` shell 命令。
- sidecar 与 `apboa-console` 位于同一个用户定义网络，并把目标固定为控制台容器名和端口。
- Docker socket 具有宿主机最高权限，必须通过最小权限代理、TLS 远程 API 或受限 socket proxy 隔离；不能直接把未保护的 socket 暴露给租户请求线程。
- sidecar 创建成功后才把应用报告为在线；创建失败要关闭 Vert.x listener 并回滚在线状态。
- 删除操作必须幂等，处理容器重启、Redis 重复消息和管理面超时。

这是当前代码改动最小的过渡方案，但运维复杂度和 Docker socket 安全风险高于独立网关节点，建议只作为无法拆分进程时的兼容实现。

## 4. 实施顺序和验收标准

建议按以下顺序实施：

1. 先抽取 `runner-gateway`，保留现有 `GatewayLifecycleManager` 和 Redis 同步协议，补充端口节点归属字段或节点级端口分配策略。
2. 为 Linux Docker Compose 增加 `network_mode: host` 的网关服务和网关专用 Dockerfile；控制台 Compose 保持现有固定 `3060` 映射。
3. 增加端口冲突、上线失败回滚、下线释放和进程重启恢复测试。
4. 在不支持 host 网络的环境启用 sidecar 实现，并增加孤儿转发清理任务。

验收必须覆盖：

- 在线应用端口可从宿主机和同网段机器访问；API 路由、鉴权、白名单和日志行为不变。
- 下线、删除、端口重置后，原端口不能再建立 TCP 连接，且新应用可以立即复用该端口。
- 网关容器重启后，数据库中在线应用自动恢复；离线应用不应重新监听。
- Redis 重复/乱序消息不会创建重复监听或重复 sidecar。
- 宿主机已有端口被占用时，上线失败不影响其他网关应用，数据库在线状态回滚为离线。

结论：在当前“网关和控制台同容器、bridge 网络、仅固定端口映射”的架构下，动态宿主机端口暴露不是通过一行 Compose 配置可以完成的。拆出网关并使用 host 网络是行为最直接、销毁语义最完整的方案；动态 sidecar 是保留现有进程边界时的可行替代方案。
