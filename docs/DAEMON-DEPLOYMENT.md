# daemon artifact 部署与升级

本文用于把 GitHub Actions 构建好的 daemon 安装到 Linux 服务器。生产服务器不需要 Gradle、Android SDK 或源码构建环境，低内存服务器也不应现场执行 `./gradlew :daemon:installDist`。

一般安装或升级直接运行官方安装器即可；它会解析最新 GitHub Release、根据 CPU 架构下载 daemon、使用同一 Release 的 `SHA256SUMS` 校验，并注册／重启 systemd user 服务：

```bash
curl -fsSL https://raw.githubusercontent.com/ac54u-mobile/cc-pocket/main/scripts/install.sh | bash
```

已经通过安装器安装的机器，日常升级优先使用内置更新命令，无需重新配对：

```bash
cc-pocket-daemon update
cc-pocket-daemon --version
```

升级器会保留 `~/.cc-pocket/` 中的身份与配对数据，并尝试自动重启服务。如果版本已安装但自动重启失败：

```bash
/usr/bin/systemctl --user daemon-reload
/usr/bin/systemctl --user restart cc-pocket-daemon
/usr/bin/systemctl --user status cc-pocket-daemon --no-pager
```

只有内置更新不可用、需要指定 artifact 或执行回滚时，才使用本文后面的手动替换流程。

安装后运行 `cc-pocket-daemon pair`，再在 App 中扫描二维码或输入终端显示的六位配对码。`cc-pocket-daemon pair` 必须在服务器终端执行，不能填进 App 的配对码输入框。

跨服务器迁移、Relay 数据库复制和 App 协调切换见 [SERVER-MIGRATION.md](./SERVER-MIGRATION.md)。

## 1. 生成 artifact

1. 打开 GitHub 仓库的 **Actions → daemon-artifact**。
2. 选择 **Run workflow**，填写要发布的 daemon 版本。
3. 等工作流成功后，在该次运行的 **Artifacts** 下载 daemon 压缩包。产物名为
   `cc-pocket-daemon-<version>-linux-x86_64.tar.gz`，内置 JRE，生产服务器不需要安装 Java。
4. 下载同一 Release 的 `SHA256SUMS`，校验来源和文件完整性后再传到服务器。

App 与 daemon 使用独立版本号。是否兼容应以协议和功能要求为准，不要因为 App 版本较小就把较新的 daemon 降级。

## 2. 替换 Linux systemd --user 服务

不同安装器可能使用 `~/.local/share/cc-pocket-daemon` 或 `~/.local/share/cc-pocket/versions/<version>`。不要覆盖猜测出来的目录，先确认实际路径：

```bash
systemctl --user show cc-pocket-daemon -p ExecStart -p WorkingDirectory
systemctl --user status cc-pocket-daemon
readlink -f ~/.local/bin/cc-pocket-daemon
```

升级时：

1. 使用同包 `SHA256SUMS` 校验后，解压 artifact 到临时目录。
2. 备份当前安装目录。
3. 停止服务，只替换 artifact 中的 `bin/`、`lib/` 及其随包资源。
4. 恢复本机密钥配置，不要用仓库示例覆盖生产凭据。
5. 执行 `systemctl --user daemon-reload`，再启动服务。

```bash
systemctl --user stop cc-pocket-daemon
# 在这里完成经过核对的文件替换
systemctl --user daemon-reload
systemctl --user start cc-pocket-daemon
```

不要同时运行旧 daemon、`./gradlew :daemon:run` 或临时 `nohup` 实例。多个实例会争用同一账号、relay 连接和本地端口，表现为 App 时好时坏、会话重复或频繁断线。

## 3. 验证

```bash
systemctl --user is-active cc-pocket-daemon
systemctl --user status cc-pocket-daemon --no-pager
journalctl --user -u cc-pocket-daemon -n 80 --no-pager
cc-pocket-daemon status
cc-pocket-daemon --version
```

至少确认：服务为 `active`、只有一个 daemon 实例、版本与 artifact 一致、relay 已连接。然后在 App 中重新进入电脑，验证项目列表和一个短会话。

如果升级失败，停止服务并恢复备份的 `bin/`、`lib/` 后重启；不要在生产服务器临时编译一个来源不明的版本救急。

## 4. 常见安装问题

### `cc-pocket-daemon: command not found`

这通常只是 `~/.local/bin` 不在 PATH，并不表示 daemon 没安装。先用 `readlink -f ~/.local/bin/cc-pocket-daemon` 和 systemd 的 `ExecStart` 定位真实文件，再加入 PATH：

```bash
echo 'export PATH="$HOME/.local/bin:$PATH"' >> ~/.bashrc
source ~/.bashrc
hash -r
```

### 长路径被终端拆行

不要把可执行文件和 `service-install/status/pair` 分开执行。使用短变量：

```bash
D="$HOME/.local/share/cc-pocket/versions/<version>/cc-pocket-daemon/bin/cc-pocket-daemon"
"$D" service-install --apply --exec "$D"
"$D" status
"$D" pair
```

### 新 App 提示验证码无效

先检查 `"$D" status` 中的 Relay。App 和 daemon 连接不同 Relay 时，验证码即使刚生成也会提示 `invalid or expired code`。验证码有效期为 120 秒，确认 Relay 一致后再生成新码。
