# cc-pocket 运行与测试

> 前置：JDK 17（任意发行版——编译用的 17 由 Gradle toolchain 自动下载，启动 `./gradlew` 本身只要 PATH/`JAVA_HOME` 里有任一 JDK）+ Android SDK（`ANDROID_HOME` 或 `local.properties`）；`claude` CLI 已安装并登录。所有 `./gradlew` 命令在仓库根目录执行。
> 用 installDist 启动器时若提示找不到 java，命令前加 `JAVA_HOME=<你的 JDK 路径>`（macOS Homebrew 例：`/opt/homebrew/opt/openjdk@17`）。

---

## 1. 单元测试（最快，不依赖 claude）

```bash
./gradlew test
```

覆盖：协议序列化往返、`StreamParser`（stream-json 解析）、`PermissionBridge`（授权握手：ask → allow/deny/超时/auto）、`TranscriptScanner`（消息计数排除工具结果）、`Broker`（离线推送 + 配对码单次有效）。

---

## 2. M0 —— 本机驱动真实 claude（两个终端，最能看清核心）

先构建启动器：

```bash
./gradlew :daemon:installDist
D=daemon/build/install/cc-pocket-daemon/bin/cc-pocket-daemon
```

**终端 1 —— 起 daemon**（本机 WS 在 `127.0.0.1:8765`）：

```bash
$D run --claude-bin ~/.local/bin/claude
```

**终端 2 —— 交互式 test-client**：

```bash
$D test-client
```

然后逐条输入（每条等上一条的回应再发下一条）：

```
dirs                                  # 列出有历史会话的目录
ls /Users/you/code/my-app             # 列该目录下的会话（不拉起 claude）
open /Users/you/code/my-app           # 新建会话；等出现 [live] convo=… session=…
say 用一句话说这个项目叫什么            # 看流式输出，结尾 [done] in/out token
cd /Users/you/code/another-app        # 切目录：同一 convo、新 cwd（旧 claude 进程被回收）
say 你现在的工作目录 basename 是什么？  # 确认已在新目录
quit
```

恢复已有会话：把 `ls` 列出的某个 `sessionId` 接在 open 后面 —— `open <目录> <sessionId>`。
切权限模式：`mode <default|acceptEdits|plan|bypassPermissions>` —— daemon 会用新模式重启子进程（保留本会话「总是允许」规则）。
授权弹窗：默认权限模式下，若你的 claude 配置会对某个工具询问，test-client 会显示 `[ASK] …`，输入 `allow` 或 `deny` 即可（你这台机器对常见工具是放行的，所以多数命令不弹窗——授权回环由单元测试 `PermissionBridgeTest` 确定性覆盖）。
退出后 `pgrep -fl claude` 应看不到本 daemon 的子进程（干净杀树）。

**Codex 与 Cursor 后端**：daemon 除了 Claude Code，也能驱动 OpenAI Codex 和 Cursor Agent。它会自动探测 PATH 上的 `codex` 与 `cursor-agent`；自定义路径分别使用 `--codex-bin <path>`、`--cursor-bin <path>`。后端由客户端在新建会话时选择，一个会话始终绑定一个后端。Cursor 使用 daemon 主机上的登录态与账户额度，模型目录来自 `cursor-agent --list-models`。

**Codex 限额**：设置 → Token 用量读取 Codex 官方本机快照，只显示快照明确返回的窗口、重置时间、套餐、Credits 和可用重置次数。官方只返回每周窗口时不会虚构 5 小时窗口。若尚无快照，先运行一次 Codex 或在 Codex CLI 输入 `/status`。账户最终数据可在浏览器打开 <https://chatgpt.com/codex/cloud/settings/usage> 核对；CC Pocket 不收集 ChatGPT 账号或密码。

**新会话默认值**：全新安装使用 Claude + `default`；之后读取 App Settings 中持久化的默认 Agent 与模式。Claude、Codex、Cursor 在客户端共用相同的四级模式 UI，daemon 再按后端翻译成对应 approval/sandbox 参数。

**生产服务器升级**：安装发行版后直接运行 `cc-pocket-daemon update`，再用 `cc-pocket-daemon --version` 验证；不需要重新配对。不要在资源紧张的服务器现场运行 Gradle。自动更新失败、手动 artifact 部署和回滚步骤见 [DAEMON-DEPLOYMENT.md](./DAEMON-DEPLOYMENT.md)。

---

## 3. M1 —— 经云端 relay（端到端加密 + 配对）

relay 鉴权 daemon（Ed25519 签名挑战）、按账户路由**不透明密文**；daemon 与设备之间 Noise 端到端加密，relay 只见密文。安全模型见 `docs/SECURITY.md`。

一键自检（脚本自带启动/清理）：

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 bash scripts/relay-smoke.sh        # 本地 relay 全流程
JAVA_HOME=/opt/homebrew/opt/openjdk@17 bash scripts/relay-smoke-prod.sh   # 经 Cloudflare 的线上 relay
```

手动三步（本地 relay；线上把 `ws://127.0.0.1:9000` 换成 `wss://relay.txx.app` 并跳过终端 1）：

```bash
./gradlew :daemon:installDist :relay:installDist
D=daemon/build/install/cc-pocket-daemon/bin/cc-pocket-daemon
R=relay/build/install/cc-pocket-relay/bin/cc-pocket-relay
```

**终端 1 —— relay**（`--in-memory` 免落盘，正式用 `--db <path>`）：

```bash
$R --port 9000 --in-memory
```

**终端 2 —— daemon（外拨连 relay）**：首次运行自生成身份 `~/.cc-pocket/identity.json`，打印 account id，并起本地配对回环。

```bash
$D run --relay ws://127.0.0.1:9000 --claude-bin ~/.local/bin/claude
```

**配对**：让正在运行的 daemon mint 一张一次性票据，打印 `ccpocket://pair?...` 链接（内含 relay 地址 / account / daemon E2E 公钥 / 票据）。

```bash
$D pair        # 另开一个终端；输出 ccpocket://pair?relay=...&acct=...&dpk=...&ticket=...
```

**终端 3 —— 设备 test-client（自带 redeem + E2E）**：从上面的链接里取 `dpk` 和 `ticket`。

```bash
$D test-client --relay ws://127.0.0.1:9000 --daemon-pub "<dpk>" --ticket "<ticket>"
```

之后 `dirs / open / say …` 一样用，流量走 device ↔ relay ↔ daemon、全程加密（relay 看不到内容）。
手机端：把 `ccpocket://pair?...` 链接粘进 App 的「Pair」框即可（见 `docs/ios-device.md`）。

---

## 4. M2 —— 桌面客户端（Compose）

需要图形界面（在你的 Mac 上直接跑）。先按 §2 起一个本机 daemon，再：

```bash
./gradlew :mobile:composeApp:run
```

窗口里把地址填 `ws://127.0.0.1:8765/v1/ws` → Connect → 点目录 → 点会话/新建 → 在 Chat 里发消息。
（这是 Desktop 目标；Android/iOS 目标需要先装 Android SDK / Xcode。）

**桌面客户端（给用户的另一种选择）**：除了手机 App，cc-pocket 也能作为桌面 App 运行——从 GitHub Release 下载 DMG（macOS）/ MSI（Windows）：

- macOS：<https://github.com/ac54u-mobile/cc-pocket/releases/latest/download/cc-pocket-desktop-macos-arm64.dmg>
- Windows：<https://github.com/ac54u-mobile/cc-pocket/releases/latest/download/cc-pocket-desktop-windows-x86_64.msi>

或像上面那样从源码 `./gradlew :mobile:composeApp:run`。它通过和手机端**同一套配对**连到**另一台**机器上的 daemon——桌面端没有摄像头，所以请输入 `cc-pocket-daemon pair` 打印的那 6 位配对码。

---

## 5. M3 —— 打包成自带运行时的 App

```bash
./gradlew :daemon:packageDaemon
open daemon/build/jpackage      # 里面是 cc-pocket-daemon.app（自带 JRE，无需系统 java）
# 直接用打好的二进制：
daemon/build/jpackage/cc-pocket-daemon.app/Contents/MacOS/cc-pocket-daemon run --claude-bin ~/.local/bin/claude
```

生成后台常驻配置（默认只打印，加 `--apply` 才真正安装）：

```bash
$D service-install --exec "$PWD/daemon/build/jpackage/cc-pocket-daemon.app/Contents/MacOS/cc-pocket-daemon" --claude-bin ~/.local/bin/claude
```

---

## 6. Android（模拟器或真机）

SDK 已装在 `/opt/homebrew/share/android-commandlinetools`，AVD `ccpocket`（android-35, arm64）已建好。

```bash
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
export PATH="$PATH:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator"

# 1) 起一个带界面的模拟器（想看就别加 -no-window）
emulator -avd ccpocket &

# 2) 起本机 daemon（App 默认连 ws://10.0.2.2:8765 —— 模拟器访问宿主机的别名）
daemon/build/install/cc-pocket-daemon/bin/cc-pocket-daemon run --claude-bin ~/.local/bin/claude

# 3) 构建并安装 APK 到运行中的模拟器/真机
./gradlew :mobile:composeApp:installDebug
#   或手动：./gradlew :mobile:composeApp:assembleDebug
#           adb install -r mobile/composeApp/build/outputs/apk/debug/composeApp-debug.apk

# 4) App 里点 Connect → 看到目录列表 → 点目录 → 选/建会话 → Chat 发消息
```

真机：用数据线连上、开 USB 调试，`adb devices` 能看到后同样 `installDebug`；真机连本机 daemon 要把 App 里的 URL 改成你电脑的局域网 IP（如 `ws://192.168.1.100:8765`），并让 daemon 监听 `0.0.0.0`（`run --host 0.0.0.0`）。
运行截图见 `docs/design/android/`。

---

## 一键自检（可选）

```bash
./gradlew test                      # 全部单元/契约测试
./gradlew build                     # 四个模块全量编译 + 测试
```
