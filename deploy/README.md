# CC Pocket relay 部署手册

本文说明如何部署 **CC Pocket relay**（Kotlin/JVM Ktor 应用）。relay 只转发不透明的端到端加密二进制数据，只保存指纹、公钥和哈希，实现零知识中继。服务只监听环回地址，由前置 **Caddy** 终止 TLS。

> 状态：**已上线**。端到端加密层已通过完整生产链路（daemon → Cloudflare → Caddy → relay → 设备）验证，脚本见 `scripts/relay-smoke-prod.sh`。威胁模型见 `docs/SECURITY.md`。

## 网络拓扑

```
客户端 ──HTTPS──> Cloudflare（代理与边缘证书）
                      │  （橙云代理；源站为 $RELAY_HOST）
                      ▼
        Caddy :80/:443（源站 Let's Encrypt 证书）
                      │  reverse_proxy（同时升级 WebSocket）
                      ▼
        cc-pocket-relay 127.0.0.1:9000（SQLite 位于 /var/lib/cc-pocket-relay/relay.db）
```

公开健康检查：`https://pocket.ark-nexus.cc/healthz` → `ok`。WebSocket 端点（自动代理）：`/v1/daemon`、`/v1/device`；REST 端点：`/v1/pair/redeem`、`/v1/pair/code`。

## 服务器信息

> 下列命令使用 `$RELAY_HOST`，请先执行 `export RELAY_HOST=<源站 IP>`。源站位于 Cloudflare 后方，因此不提交到 Git。

| 项目 | 值 |
| --- | --- |
| 主机 | `$RELAY_HOST`（用户 `root`） |
| 系统 | Alibaba Cloud Linux 3（Anolis 8，兼容 RHEL8）· x86_64 · systemd 239 |
| 内存 | 约 1.8 GB；relay 堆限制为 `-Xmx256m` |
| JRE | `java-17-openjdk-headless`，系统路径 `/usr/bin/java` |
| Caddy | `epel` 仓库的 `2.6.4`，包含 systemd unit 与 `caddy` 用户 |
| Relay 用户 | `ccpocket`（系统用户，`nologin`） |

### 服务器路径

| 路径 | 用途 |
| --- | --- |
| `/opt/cc-pocket-relay/` | relay 分发目录，包含 `bin/cc-pocket-relay` 和 `lib/*.jar`，属主 `root:root` |
| `/var/lib/cc-pocket-relay/relay.db` | SQLite 数据库，属主 `ccpocket:ccpocket`，目录权限 `0750` |
| `/etc/systemd/system/cc-pocket-relay.service` | relay unit，与 `deploy/cc-pocket-relay.service` 对应 |
| `/etc/caddy/Caddyfile` | Caddy 配置，与 `deploy/Caddyfile` 对应；`.orig` 是默认备份 |
| `/var/lib/caddy/.local/share/caddy/certificates/.../pocket.ark-nexus.cc/` | 自动管理的 LE 证书与私钥 |

## 本目录文件

- `cc-pocket-relay.service`：加固后的 systemd unit，启用 `NoNewPrivileges`、`ProtectSystem=full`、`ProtectHome`，以 `ccpocket` 身份运行，只允许写入 `/var/lib/cc-pocket-relay`，并设置 `JAVA_TOOL_OPTIONS=-Xmx256m`。
- `Caddyfile`：单站点配置，反向代理到 `127.0.0.1:9000`；Caddy 自动申请 Let's Encrypt 证书并升级 WebSocket。

## SSH 非交互连接

```bash
export SSHPASS='<root 密码>'
sshpass -e ssh -o StrictHostKeyChecking=accept-new root@$RELAY_HOST '<cmd>'
```

## 首次初始化

```bash
# 1. 安装 JRE
dnf install -y java-17-openjdk-headless

# 2. 从 epel 安装 Caddy
dnf install -y 'dnf-command(copr)'
dnf install -y caddy            # 同时安装 unit 并创建 caddy 用户

# 3. 创建用户和目录
useradd --system --no-create-home --shell /usr/sbin/nologin ccpocket
mkdir -p /opt/cc-pocket-relay /var/lib/cc-pocket-relay
chown ccpocket:ccpocket /var/lib/cc-pocket-relay && chmod 750 /var/lib/cc-pocket-relay

# 4. 安装分发文件和 unit；复制方式见下方“重新部署”
install -m 0644 cc-pocket-relay.service /etc/systemd/system/
cp -a /etc/caddy/Caddyfile /etc/caddy/Caddyfile.orig   # 只备份一次
install -m 0644 Caddyfile /etc/caddy/Caddyfile

# 5. 启动服务
systemctl daemon-reload
systemctl enable --now cc-pocket-relay
caddy validate --config /etc/caddy/Caddyfile
systemctl enable --now caddy
```

## 本地重新构建

```bash
cd ~/path/to/cc-pocket
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :relay:installDist
# → relay/build/install/cc-pocket-relay/  (bin/ + lib/)
```

## 重新部署或升级 relay（本地 → 服务器）

```bash
export SSHPASS='<root 密码>'
REPO=~/path/to/cc-pocket
DIST="$REPO/relay/build/install/cc-pocket-relay"

# 先按上节重新构建，再执行：
sshpass -e ssh -o StrictHostKeyChecking=accept-new root@$RELAY_HOST \
  'systemctl stop cc-pocket-relay && rm -rf /opt/cc-pocket-relay/bin /opt/cc-pocket-relay/lib'
sshpass -e scp -o StrictHostKeyChecking=accept-new -r "$DIST/bin" "$DIST/lib" \
  root@$RELAY_HOST:/opt/cc-pocket-relay/
sshpass -e ssh -o StrictHostKeyChecking=accept-new root@$RELAY_HOST \
  'chown -R root:root /opt/cc-pocket-relay && chmod +x /opt/cc-pocket-relay/bin/cc-pocket-relay && systemctl start cc-pocket-relay && sleep 4 && curl -fsS http://127.0.0.1:9000/healthz && echo " OK"'
```

> 重新部署不会修改 `/var/lib/cc-pocket-relay/` 中的 SQLite 数据库。

修改 `cc-pocket-relay.service` 或 `Caddyfile` 后，重新复制文件并执行：

```bash
# unit 已修改
systemctl daemon-reload && systemctl restart cc-pocket-relay
# Caddyfile 已修改；先校验，再无停机重载
caddy validate --config /etc/caddy/Caddyfile && systemctl reload caddy
```

## 日常运维

```bash
# 状态
systemctl status cc-pocket-relay
systemctl status caddy

# 持续查看日志
journalctl -u cc-pocket-relay -f
journalctl -u caddy -f
journalctl -u caddy | grep -iE 'obtain|acme|cert|error'   # ACME / cert status

# 重启或重载
systemctl restart cc-pocket-relay
systemctl reload caddy        # zero-downtime config reload

# 服务器本机健康检查
curl -s http://127.0.0.1:9000/healthz                     # -> ok

# 经过 Cloudflare 的公开健康检查
curl -sS https://pocket.ark-nexus.cc/healthz              # -> ok

# 绕过 Cloudflare 直连源站，验证 Caddy 的 LE 证书
curl -sS --resolve pocket.ark-nexus.cc:443:$RELAY_HOST https://pocket.ark-nexus.cc/healthz
```

## TLS 与网络说明

- 域名 `pocket.ark-nexus.cc` 经过 Cloudflare 代理，DNS 指向 Cloudflare 而不是源站。证书分两层：客户端看到 Cloudflare 边缘证书，源站使用 Caddy 为该域名申请的 Let's Encrypt 证书。ACME `http-01` 挑战由 Cloudflare 转发到源站 80 端口。
- Caddy 自动续期，证书与私钥保存在 `/var/lib/caddy/.local/share/caddy/certificates/...`。
- 当前 Let's Encrypt 证书可能出现 `no OCSP stapling ... no OCSP server specified` 日志，这是无害提示，可忽略。

## 阿里云安全组

公网已能访问入站 **TCP 80 和 443**；ACME 挑战成功且公开 HTTPS 返回 `ok`，当前无需额外操作。

如果安全组变化导致 80/443 不可达，Caddy 会在 `journalctl -u caddy` 中反复执行 ACME 挑战。请在阿里云 ECS 安全组控制台为该实例开放入站 **TCP 80 和 443**。这一步不能通过 SSH 内的主机防火墙代替；端口恢复可达后，Caddy 会自动申请或续期证书。

## 同机服务：请勿影响

- `searxng` on `127.0.0.1:8080`
- `openclaw-gateway` on `:12739`
- SSH 配置：不要修改。

每次部署后都要确认这些服务仍在监听。
