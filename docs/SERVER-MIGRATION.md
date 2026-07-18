# 服务器迁移与无锁定切换

本文用于把生产 Relay 或 Linux daemon 迁移到另一台服务器。目标是保留已有账号和设备、避免配对码跨 Relay 失效，并始终留有可回滚入口。

## 先区分两个角色

- **Relay 服务器**：公网中继，生产地址为 `wss://relay.txx.app`，保存 `/var/lib/cc-pocket-relay/relay.db`。
- **daemon 主机**：实际运行 Codex/Claude/Cursor 的电脑或服务器，身份保存在 `~/.cc-pocket/identity.json`。

App 和目标 daemon 必须同时连接同一个 Relay。若 daemon 仍在旧 Relay，而新版 App 已连接新 Relay，`cc-pocket-daemon pair` 虽然能生成验证码，手机仍会提示 `invalid or expired code`。

## 迁移前清单

1. 不要卸载旧 App、删除旧 Relay 或停止旧 daemon；先保持回滚通道。
2. 确认新域名已解析到新服务器，80/443 可达。
3. 从 GitHub Actions 构建 Relay、daemon 和 App；生产服务器不要运行 Gradle。
4. 备份以下数据，并把备份保存在服务器以外：
   - Relay：`/var/lib/cc-pocket-relay/relay.db`
   - daemon：`~/.cc-pocket/`
   - systemd：`~/.config/systemd/user/cc-pocket-daemon.service`
5. 使用 SSH 密钥；不要把 root、NPM 或 GitHub 密码写进脚本、仓库和 shell 历史。

## 一、迁移 Relay 数据库

SQLite 使用 WAL 时不能只复制 `relay.db`。旧 Relay 在线期间用 SQLite 的一致性备份：

```bash
MIGRATION_DIR=$(mktemp -d /tmp/cc-pocket-relay-migration.XXXXXX)
sqlite3 /var/lib/cc-pocket-relay/relay.db ".backup '$MIGRATION_DIR/relay.db'"
sqlite3 "$MIGRATION_DIR/relay.db" 'PRAGMA integrity_check;'
chmod 600 "$MIGRATION_DIR/relay.db"
```

输出必须为 `ok`。再用 `scp` 传到新服务器，并对比账号和设备数量：

```bash
sqlite3 relay.db 'select count(*) from accounts; select count(*) from devices;'
```

把备份安装为新服务器的 `/var/lib/cc-pocket-relay/relay.db`。新 Relay 验证完成后删除 `/tmp` 中的传输副本，但不要删除正式数据库或离线备份。

## 二、部署新 Relay

使用 GitHub Actions `relay-artifact` 的 `cc-pocket-relay-<version>-linux-x86_64.tar.gz`，校验同包 `SHA256SUMS` 后解压到 `/opt/cc-pocket-relay`。

Nginx Proxy Manager 部署按 [NPM.md](../deploy/NPM.md) 操作。Relay 只加入 NPM 的 Docker 网络，不发布宿主机 9000 端口。

切换客户端前必须全部通过：

```bash
curl -fsS https://relay.txx.app/healthz
curl -sS -o /dev/null -w '%{http_code}\n' http://relay.txx.app/healthz
```

预期分别为 `ok` 和 `301`。还要确认：

- NPM 的 Websockets Support、Force SSL、HTTP/2 已开启。
- Relay 容器为 `running` 且没有持续重启。
- `ss -ltn '( sport = :9000 )'` 没有宿主机监听项。
- 新旧数据库的 `accounts`、`devices` 数量一致。

## 三、安装 daemon Artifact

Artifact 外层名称和压缩包名称不同：GitHub Artifact 名通常是 `cc-pocket-daemon-<version>`，其中的文件是 `cc-pocket-daemon-<version>-linux-x86_64.tar.gz`。先用 `find` 查看实际文件名，不要凭旧版本名称猜测。

解压到版本目录后，用短变量避免长命令被终端换行拆开：

```bash
D="$HOME/.local/share/cc-pocket/versions/<version>/cc-pocket-daemon/bin/cc-pocket-daemon"
test -x "$D" && "$D" --version
"$D" service-install --apply --exec "$D"
"$D" status
```

`service-install`、`status` 和 `pair` 是 daemon 的子命令，不能拆成两条命令。下面是错误示例：

```text
cc-pocket-daemon
service-install --apply
```

若交互式 shell 找不到命令，先使用绝对路径，再修复 PATH：

```bash
echo 'export PATH="$HOME/.local/bin:$PATH"' >> ~/.bashrc
source ~/.bashrc
hash -r
command -v cc-pocket-daemon
```

最终 `status` 必须同时显示目标版本、`wss://relay.txx.app` 和 `relay: ✓ attached`。

## 四、协调 App 与 daemon 切换

推荐顺序：

1. 新 Relay 上线并导入数据库，旧 Relay 保持运行。
2. 升级一台 daemon，确认它在新 Relay `attached`。
3. 安装新版 App，连接这台 daemon 做项目列表和短会话测试。
4. 逐台升级其他 daemon。
5. 所有设备稳定后再停止旧 Relay，至少保留数据库备份一个发布周期。

卸载 iOS App 可能清除手机本地密钥和配对凭据。Relay 数据库备份无法恢复手机端私钥，因此重新安装后出现配对页是正常的，需要重新运行：

```bash
cc-pocket-daemon pair
```

验证码有效 120 秒且只能使用一次。若提示 `invalid or expired code`，不要只反复生成验证码，先在 daemon 上执行 `cc-pocket-daemon status`，确认它与 App 使用同一个 Relay。

## 五、验收与回滚

验收：

```bash
systemctl --user is-active cc-pocket-daemon
cc-pocket-daemon status
journalctl --user -u cc-pocket-daemon -n 80 --no-pager
curl -fsS https://relay.txx.app/healthz
```

至少完成一次真实的 App → Relay → daemon 连接和 Codex 短会话。健康检查只能证明 HTTP 可用，不能替代 daemon 的 `attached` 和真实 WebSocket 验证。

回滚时不要重新配对或生成新身份：让 daemon 重新使用原来的版本和 Relay，恢复原数据库，然后重启服务。始终保留 `~/.cc-pocket/identity.json`；删除它会生成新账号，旧配对将无法识别这台 daemon。
