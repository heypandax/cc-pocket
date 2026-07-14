# 为 CC Pocket 贡献代码

欢迎提交 Issue 和 Pull Request。仓库维护文档统一使用中文；代码标识、命令和必要的官方产品名保持原样。

## 模块概览

| 模块 | 作用 | 运行位置 |
|---|---|---|
| `:protocol` | 共享线路协议与端到端加密，是 `pocket/*` 帧的唯一事实来源 | KMP：JVM、Android、iOS |
| `:daemon` | 启动 `claude`、`codex`、`cursor-agent` 子进程并连接 relay | 用户电脑或服务器（JVM） |
| `:relay` | 零知识中继：配对、密文路由和限流 | 云端（JVM） |
| `:mobile:composeApp` | 手机和桌面客户端 | Android、iOS、桌面端 |
| `iosApp/` | KMP framework 的 Xcode 宿主，由 `project.yml` 生成 | iOS |

## 构建前置条件

1. **JDK 17**：任意发行版均可。Gradle 会解析精确工具链，本机只需让一个 JDK 出现在 `PATH` 或 `JAVA_HOME` 中。
2. **Android SDK**：设置 `ANDROID_HOME`，或在 `local.properties` 写入 `sdk.dir=...`。即使只执行 JVM 任务，Gradle 配置阶段也会读取 Android 模块。
3. **Firebase 占位文件**：真实配置不会提交到 Git。首次构建时执行：

   ```bash
   cp mobile/composeApp/google-services.json.template mobile/composeApp/google-services.json
   cp iosApp/iosApp/GoogleService-Info.plist.template iosApp/iosApp/GoogleService-Info.plist
   ```

4. daemon 端到端测试至少需要一种已安装并登录的 CLI：`claude`、`codex` 或 `cursor-agent`。调用真实提供方的测试会消耗对应账号额度。

## 执行测试

```bash
bash scripts/check-all.sh
bash scripts/relay-smoke.sh
./gradlew :protocol:jvmTest --tests "dev.ccpocket.protocol.e2e.*"
```

提交 PR 前运行 `scripts/check-all.sh`。CI 会测试 protocol、daemon、relay 并编译桌面目标。移动端 UI/截图测试需要真实 Skia 渲染器，因此只在本地执行。

使用真实 Claude 手动冒烟测试 daemon：`./gradlew :daemon:run --args="test-client"`，详见 [运行文档](docs/RUN.md)。

## 容易踩坑的地方

- **线路兼容性**：daemon 与 App 独立发版。`:protocol` 中的帧、模型和路由必须向后兼容；只能新增带默认值的字段，不能随意改名或改类型。PR 中要说明旧端如何处理新字段。
- **中文文档**：`README.md` 是默认首页，所有维护文档使用中文。`README.en.md` 仅保留中文入口说明。
- **Claude CLI 漂移**：daemon 依赖中途消息注入和 `AskUserQuestion` 回答形状等细节。升级 Claude CLI 后执行 `python3 scripts/probe-claude-wire.py`；该脚本会使用真实额度。
- **xcodegen**：`iosApp/iosApp.xcodeproj` 由 `iosApp/project.yml` 生成。需要持久化的工程改动应写入 YAML，而不是只在 Xcode 中修改。

## 脚本范围

所有贡献者可使用：`check-all.sh`、`relay-smoke.sh`、`install.sh`、`install.ps1`、`probe-claude-wire.py`。

以下脚本只供维护者使用，需要发布凭据、签名身份、生产 relay 或指定设备：`release-*.sh`、`release-windows.ps1`、`notary-setup.sh`、`redeploy-relay.sh`、`provision-relay-push.sh`、`relay-smoke-prod.sh`、`ios-fir.sh`、`install-pandaa.sh`、`update-local-daemon*.sh`。详见 [脚本文档](scripts/README.md)。

生产 Linux daemon 应使用 `daemon-artifact` 工作流和 [artifact 部署指南](docs/DAEMON-DEPLOYMENT.md)升级，不要在资源紧张的生产服务器现场构建。

## 反馈渠道

- Bug 或功能建议：使用 Issue 模板，并附上 daemon 版本（`cc-pocket-daemon status`）、电脑系统和客户端平台。
- 安全问题：通过 [GitHub 私密安全公告](https://github.com/ac54u-mobile/cc-pocket/security/advisories/new)报告，威胁模型见 [安全文档](docs/SECURITY.md)。

## 许可证

项目使用 MIT 许可证。提交贡献即表示同意按该许可证授权。
