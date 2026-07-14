# daemon artifact 部署与升级

本文用于把 GitHub Actions 构建好的 daemon 安装到 Linux 服务器。生产服务器不需要 Gradle、Android SDK 或源码构建环境，低内存服务器也不应现场执行 `./gradlew :daemon:installDist`。

## 1. 生成 artifact

1. 打开 GitHub 仓库的 **Actions → daemon-artifact**。
2. 选择 **Run workflow**，填写要发布的 daemon 版本。
3. 等工作流成功后，在该次运行的 **Artifacts** 下载 daemon 压缩包。
4. 校验下载来源和文件完整性后再传到服务器。

App 与 daemon 使用独立版本号。是否兼容应以协议和功能要求为准，不要因为 App 版本较小就把较新的 daemon 降级。

## 2. 替换 Linux systemd --user 服务

以下示例假设安装目录为 `~/.local/share/cc-pocket-daemon`，服务名为 `cc-pocket-daemon`。先确认自己的实际目录：

```bash
systemctl --user show cc-pocket-daemon -p ExecStart -p WorkingDirectory
systemctl --user status cc-pocket-daemon
```

升级时：

1. 解压 artifact 到临时目录。
2. 备份当前安装目录和 `voice-agent/.env`。
3. 停止服务，只替换 artifact 中的 `bin/`、`lib/` 及其随包资源。
4. 恢复本机 `.env` 等密钥配置，不要用仓库示例覆盖生产凭据。
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
```

至少确认：服务为 `active`、只有一个 daemon 实例、版本与 artifact 一致、relay 已连接。然后在 App 中重新进入电脑，验证项目列表和一个短会话。

如果升级失败，停止服务并恢复备份的 `bin/`、`lib/` 后重启；不要在生产服务器临时编译一个来源不明的版本救急。

## 4. Voice Agent

`cc-pocket-daemon voice-agent --action start` 用于启动可选的语音/电话助手，不是 daemon 主服务的启动命令。先保持 `cc-pocket-daemon` systemd 服务正常，再按需执行：

```bash
cc-pocket-daemon voice-agent --action status
cc-pocket-daemon voice-agent --action start
cc-pocket-daemon voice-agent --action stop
```

如果未配置 voice-agent 的依赖和 `.env`，不需要启动它，也不会影响 App 的文本会话。
