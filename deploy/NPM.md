# 使用 Nginx Proxy Manager 部署 Relay

适用于 Nginx Proxy Manager 与 Relay 位于同一台 Docker 主机的部署。Relay 不发布宿主机端口，
只在 Compose 的内部网络暴露 `9000`，NPM 通过容器名 `cc-pocket-relay` 访问它。

1. 把 `npm-compose.override.yaml` 复制为 NPM Compose 目录中的 `compose.override.yaml`。
2. 创建 `/etc/cc-pocket-relay/push.env`；没有 APNs/FCM 凭据时也可以是权限为 `0600` 的空文件。
3. 解压 Relay Artifact 到 `/opt/cc-pocket-relay`，数据库目录使用 `/var/lib/cc-pocket-relay`。
4. 在 NPM Compose 目录运行 `docker compose up -d relay`。
5. 在 NPM 新建 Proxy Host：
   - Domain Names：`relay.txx.app`
   - Scheme：`http`
   - Forward Hostname/IP：`cc-pocket-relay`
   - Forward Port：`9000`
   - Websockets Support：开启
   - Block Common Exploits：开启
   - Cache Assets：关闭
6. SSL 页面申请 Let's Encrypt 证书，开启 Force SSL 和 HTTP/2 Support。

验证：

```bash
curl -fsS https://relay.txx.app/healthz
```

应输出 `ok`。不要给 Relay Compose 服务添加 `ports: 9000:9000`，避免绕过 NPM 将内部接口暴露到公网。
