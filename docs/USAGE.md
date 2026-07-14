# CC Pocket 使用文档

CC Pocket 让手机成为电脑或服务器上 Claude Code、OpenAI Codex 与 Cursor Agent 的遥控器：人不在工位，任务继续运行，批准权限、跟进进度、补充指令都在手机上完成。

链路一句话：App ↔ 零知识 relay（只转发密文）↔ 你的 cc-pocket daemon ↔ `claude` / `codex` / `cursor-agent` CLI。中继看不到任何明文，端到端加密只存在于 App 和 daemon 之间（详见 [SECURITY.md](./SECURITY.md)）。

> App 与 daemon 独立发版。使用 Codex 目标、审查、Skills、Plugins、MCP、Apps 等新能力时，请同时升级 App 和 daemon；历史变化见 [CHANGELOG.md](../CHANGELOG.md)。

---

## 准备条件

- 一台可运行 daemon 的 Mac、Linux 或 Windows 电脑/服务器，并至少安装、登录一种受支持的 Agent CLI；
- 一部手机：iPhone（App Store 搜「CC Pocket」）或 Android（从 [GitHub Releases](https://github.com/ac54u-mobile/cc-pocket/releases) 下载 APK 安装）。

## 一、安装电脑端（daemon）

```bash
brew install --cask heypandax/tap/cc-pocket
cc-pocket-daemon service-install --apply   # 注册为登录自启服务，断线自动重连
cc-pocket-daemon pair                      # 打出二维码 + 6 位配对码
```

- 升级：`brew upgrade --cask heypandax/tap/cc-pocket`（**必须用全名**——Homebrew 官方仓有个不相关的同名 `cc-pocket` cask，裸名会操作到那个包）；
- 可选（语音输入给 Android／桌面客户端用，iPhone 不需要）：`brew install whisper-cpp`。

## 二、配对手机

手机打开 CC Pocket：**扫描**终端里的二维码，或**手输** 6 位配对码。第 6 位输入完成后数字键盘会自动收起并开始连接，无需再点一次按钮。配对一次永久有效，之后手机在任何网络（蜂窝、异地 Wi-Fi）都能连上，无需与电脑同一局域网。

配对无需注册账号——身份就是设备密钥本身。多台手机可分别配对同一台电脑。

## 三、日常使用

### 目录与会话

- 进入后先选**工作目录**：可按目录**树状层层下钻**（带面包屑回上层），也可一键切回**平铺**的最近列表——两种视图都会记住你的选择；输入即按路径／项目名／进行中会话标题**筛选**，每个项目显示各自的会话数；
- 目录下可以**新建会话**，或**恢复**任意历史会话——从上次离开的地方继续；
- **运行中的项目**点一下直达正在跑的会话；想翻这个目录的历史会话，点行尾的「历史」徽标进入会话列表自选；
- 电脑终端里正在跑的 claude 会话也能看到：默认**只读旁观**实时输出，点「Continue here」可接管为手机控制。**接管不再随手分叉**——只有终端里的 claude 确实还在写入时才会分支出新会话（避免双写冲突），人已经退出的会话会原地接续。

### 选择智能体（Claude / Codex / Cursor）

**新建会话时可以挑后端**：Claude、Codex、Cursor 共用完全相同的四级模式列表、页面高度、说明、选中态与危险操作确认。首次安装以 **Claude + Default** 开始；以后使用 Settings 中保存的默认 Agent 与模式。

如果 daemon 所在电脑已安装并登录 **Cursor Agent CLI**，新建会话时还可以选择 **Cursor**。Cursor 直接操作服务器上的当前工作目录，使用该服务器的 Cursor 登录态和套餐额度，不需要 Cloud Agents API Key 或先把改动推到 GitHub。

```bash
cursor-agent --version
cursor-agent status
cursor-agent --list-models
```

daemon 默认自动寻找 `cursor-agent`；自定义安装路径可用 `cc-pocket-daemon run --cursor-bin /path/to/cursor-agent`，或设置 `CC_POCKET_CURSOR_BIN`。Cursor 会话支持实时文本与思考流、工具事件、模型切换、停止和 `--resume` 连续对话。打开模型菜单时，App 会向 daemon 请求当前账户的 `cursor-agent --list-models` 实时结果；发现失败时才使用内置常用列表，完整账户专属 ID 仍可在「自定义」中输入。

Fable 5 可选 `claude-fable-5-high` 或 `claude-fable-5-thinking-high`。Cursor 当前把 Fable 5 标记为 **NO ZDR**，对数据驻留或零保留有要求时请改用带 ZDR 的模型。

Cursor 历史会话会从 `~/.cursor/projects/*/agent-transcripts/<会话-id>/*.jsonl` 回放到 App；用户与助手正文会显示，Cursor 注入的时间戳包装和 `[REDACTED]` 标记会被过滤。图片附件会临时写成项目根目录下的隐藏文件并以 `@文件` 交给 Cursor，Run 结束、取消或异常退出后立即删除，不会保留在项目中。

权限与错误处理：Cursor headless 当前没有可供 CC Pocket 回传逐条人工决定的审批协议。Cautious 为只读 Plan，Balanced 使用 Cursor Smart Auto Review，Autonomous 使用沙箱内 `--force`，Full auto 使用关闭沙箱的 `--force`。登录失效、模型不可用、权限拒绝与其他进程退出会显示针对 Cursor 的处理建议，而不是笼统的「agent process ended」。

- Claude 用 app 的赤陶色（terracotta）主题色；**Codex 用青色（teal）**，并且只有 Codex 会在列表与标题里被标记，Claude 保持不标。
- 一个会话**始终绑定一个后端**：中途不能在 Claude 与 Codex 之间切换，想换就新建一个会话。
- 四级模式会由 daemon 翻译为各后端原生的审批与沙箱策略：

| 预设 | 大致含义 |
|---|---|
| Cautious | 最谨慎：命令与改动都先征求批准，沙箱最严 |
| Balanced | 折中：常规操作放行，敏感动作仍来问 |
| Autonomous | 更放手：大部分自己来，少数情况才询问 |
| Full auto | 全自动：一律不问，沙箱最宽（信任度最高，谨慎使用） |

Cursor 对应关系：Cautious 使用 Plan + 沙箱，Balanced 使用 Smart Auto Review + 沙箱，Autonomous 使用 `--force` + 沙箱，Full auto 使用 `--force` 并关闭沙箱。Cursor 的 headless 接口暂不提供 CC Pocket 可回传的逐条人工审批协议，因此 Balanced 由 Cursor 自己审核安全调用。

### 配置 Codex 与选择模型

CC Pocket 不自己调用 OpenAI API；它在电脑上启动你已登录的 Codex CLI，所以模型是否可用仍由 Codex 账户、工作区策略和 CLI 版本决定。

1. 在 daemon 所在电脑安装并登录 Codex：

   ```bash
   codex --version
   codex login
   codex
   ```

   首次运行的 Codex 会话能正常回复后，再从 CC Pocket 连接。daemon 默认自动寻找 `codex`；找不到时可用 `cc-pocket-daemon run --codex-bin /path/to/codex`，或设置 `CC_POCKET_CODEX_BIN`。

2. 打开 CC Pocket，进入一台已配对的电脑，选择项目目录，点「新建会话」，把后端切换为 **Codex**，从与 Claude 相同的四级模式列表中选择后启动会话。

3. 进入 Codex 会话后，点击输入框上方的模型名称打开模型选择器。模型按提供方分组，可搜索显示名或模型 ID：

   | 模型 ID | 适合场景 |
   |---|---|
   | `gpt-5.6-sol` | 复杂、开放式或高价值任务，默认首选 |
   | `gpt-5.6-terra` | 日常开发的均衡选择 |
   | `gpt-5.6-luna` | 明确、重复、更注重速度的任务 |
   | `gpt-5.5` | 上一代复杂编码与推理模型 |
   | `gpt-5.3-codex-spark` | 低延迟代码迭代（需 ChatGPT Pro 权限） |
   | `gpt-5.4` | 通用编码、推理和工具使用 |
   | `gpt-5.4-mini` | 更快、更节省的日常任务 |

4. 在输入框上方的会话状态栏点击模型、推理强度或执行模式即可直接切换；右上角 **⋯** 保留终端、文件、清理等低频操作。复杂任务再提高强度；强度越高，通常耗时和用量也越高。

### Codex 官方能力

以下入口只在 **Codex 会话已经启动**后出现；它们调用 daemon 主机上的 Codex 官方 app-server，不是 CC Pocket 自行模拟的提示词功能。

- **运行中追加指令**：Codex 正在回答时继续发送，新指令通过 `turn/steer` 加入当前回合；无需先停止，也不会重复创建一轮。
- **上下文压缩**：在快捷操作中选择压缩上下文，使用 Codex 原生压缩能力保留关键上下文。
- **创建会话分支**：在会话快捷操作选择“创建会话分支”，从当前历史生成独立分支，原会话保持不变。
- **归档与恢复**：在项目会话列表归档 Codex 会话；切换到“已归档”可查看并恢复。Claude/Cursor 不显示此入口。
- **目标**：会话右上角 **⋯ → 目标**，填写目标内容，可选 Token 预算；之后可暂停、继续、完成或清除，并查看 Token/时间进度。
- **代码审查**：会话右上角 **⋯ → 代码审查**，可选择未提交改动、与基础分支比较、指定提交 SHA 或自定义审查说明。
- **技能与插件**：会话右上角 **⋯ → 技能与插件**。Skills 页列出仓库、用户、系统或管理员范围的技能，可启用、停用和刷新；Plugins 页列出 Codex Marketplace 插件，可安装，卸载时需要二次确认。
- **MCP 与 Apps**：会话右上角 **⋯ → MCP 与 Apps**。MCP 页显示授权状态及工具、资源、资源模板数量，可重新加载配置或在系统浏览器完成 OAuth；Apps 页显示连接/可访问状态，并通过官方安装地址发起连接。

Skills、Plugins 或 MCP 配置发生变化后可在页面内刷新，不必重建 App。安装插件会修改 daemon 主机当前 Codex 账号的配置；第三方授权和凭据由 Codex/系统浏览器处理，CC Pocket 不保存账号密码或 OAuth 凭据。

### Codex 用量、限额与重置

进入 **设置 → Token 用量** 可查看本机三种 Agent 的 Token 统计。页面打开后立即读取，并在停留期间每 10 秒自动刷新；右上角 ↻ 可手动立即刷新。Codex 区域读取官方 CLI/app-server 在 daemon 主机上产生的最近快照，只显示快照明确提供的窗口、剩余百分比、北京时间重置时刻、套餐、Credits 和限额重置次数。

官方有时只返回每周窗口，不返回 5 小时窗口；此时 App 只显示每周额度，这是正常行为，不会用旧数据或推算值补出“5 小时限额”。没有任何快照时，先运行一次 Codex 或在 Codex CLI 输入 `/status`，再回到 App 刷新。

若页面显示可用的限额重置次数，可在该区域执行重置。重置属于官方账号操作，执行前会确认，结果以 Codex 返回为准；没有次数或账号不支持时不会显示可执行按钮。

如需核对账户侧最终数据，可在浏览器访问 <https://chatgpt.com/codex/cloud/settings/usage>。登录由 ChatGPT 官方页面处理，CC Pocket 不要求、接收或保存账号密码。实际消耗会随模型、上下文、推理、工具和缓存变化，因此消息数量只能作为区间估计。

需要新上线模型或 cc-switch 等第三方网关时，在模型列表底部的 **自定义** 输入完整模型 ID 并确认。CC Pocket 会原样传给 Codex，但不能为账户开通未授权的模型。

如果选择后报「model is not supported」或类似错误，先在电脑终端运行 `codex -m <模型-id>` 验证；终端也不可用时，说明该模型尚未对当前账户或工作区开放，请改用可用的模型。

### 对话

- 输出、工具调用、结果实时流式呈现，效果和终端一致；代码块按语言**语法高亮**（`sql`、`py`、`kt`、`js` 等常用语言，其余保持等宽原样）；
- Claude 在跑时，标题栏有常驻的「运行中」指示，输入框仍可打字——发送会**排队**到当前回合结束后注入，不用等；
- 随时点 ■ **打断**当前回合（会话保持存活）；
- 输入框上方的状态栏负责切换**模型**（也支持自定义模型 ID）、**推理强度**和**权限模式**；顶栏 **⋯ 快捷操作**只保留目标、审查、分支、Skills/Plugins、MCP/Apps、终端、文件和清理等低频操作；
- `/model <名称>` 切换模型（如 `/model opus`）；输入 `/` 可自动补全当前项目的斜杠命令。

### `@` 中文专业 Agent

在输入框键入 `@` 后，候选面板会先显示中文专业 Agent，再显示当前目录的文件。继续输入中文名称或关键词可筛选；点选 Agent 后补充具体任务并发送。单条消息最多选择 3 个 Agent。

Agent 清单由 daemon 从 `ac54u-mobile/agency-agents` 的 CC Pocket 发行清单读取，实际角色提示只在发送时按已选 ID 展开。聊天记录显示原始请求，不把长角色提示重复展示为用户消息。若只看到文件没有 Agent，请确认 daemon 已升级且能访问清单地址，然后重新输入 `@`。

### 查看会话改动的文件

⋯ 菜单 → **改动文件**：列出本会话 Claude 动过的所有文件，点开即看内容（代码带语法高亮，markdown 直接渲染）。看完 **←** 返回文件列表接着看下一个，**✕** 一键回到会话。

### 权限审批

Agent 要执行敏感操作（跑命令、改文件等）时，手机会弹出审批卡片：先看风险等级、影响范围和操作内容，再选择 **允许** 或 **拒绝**；勾选「本会话总是允许」可以让同类操作本会话内不再询问（可在会话中随时撤销）。允许、拒绝、工具执行和任务完成会进入按时间排列的活动记录，方便回看授权过程。

**执行权限模式**（会话内可随时切换）决定 Claude 问多问少：

| 模式 | 行为 |
|---|---|
| `default` | 每个敏感工具都先询问（默认，最稳） |
| `acceptEdits` | 文件编辑自动放行，命令等其他操作仍询问 |
| `plan` | 只读规划——Claude 只能看和想，不执行任何改动 |
| `bypassPermissions` | 全自动，一律不问（信任度最高，谨慎使用） |

**默认模式与默认推理强度**：在 **Settings** 里可以把上面某个模式设为**默认模式**，并设定**默认推理强度（effort）**——新建会话、恢复历史会话、桌面端「在这里继续」都以 **Settings 的默认模式**开场（选了全自动就都是全自动）；模型与推理强度仍按各会话记住的恢复。

### 语音输入

点麦克风开始说话（单段上限 90 秒）：

- **iPhone**：系统级实时听写，文字逐词上屏，不经过网络；
- **Android／桌面**：录音发回你的 Mac，由 whisper 本地转写（需先 `brew install whisper-cpp`，没装时手机会提示这条命令）。

### 图片附件

点图片按钮选图，最多 **4 张**随一条消息发出——iOS 使用简洁的系统 `photo` 图标，并保持原始比例。

### 桌面接力

手机上聊到一半回到电脑时，接管提示会说明当前控制端；回到电脑可看到期间操作摘要。会话阅读位置按对话保存，重新打开会回到上次阅读处；若此前停在底部，则直接显示最新消息。

### 桌面版 App 与多机总览

除了手机，CC Pocket 还有 **macOS / Linux / Windows 桌面版**（[GitHub Releases](https://github.com/ac54u-mobile/cc-pocket/releases) 下载 .dmg / .msi）——两栏式「任务控制台」：左侧固定会话（⌘1–9 直达）、运行中项目、最近会话分组，右侧对话；⌘K 全局跳转。用一台电脑操控另一台，权限审批及输入框上方的模型/模式控制与手机一致。

配对了多台电脑？手机与桌面端都有**多机总览**：每台电脑的在线状态、正在跑的项目、待审批数一屏看完，一点即达，跨机审批不用来回切。

## 四、设备管理

Settings 里可以**解除当前手机的配对**；解除后这台手机需重新扫码才能再连。（管理多台已配对设备的功能还在路上。）

## 四点五、进阶：daemon 独立登录（防终端被登出）

如果你在手机操控会话的同时，电脑终端里开着的 `claude` 偶尔**被莫名登出**——那是两个 claude 共用同一份凭证、OAuth 刷新令牌轮换互相踩了（issue #69）。开启凭证隔离即可根治：

```bash
cc-pocket-daemon config --isolated-claude-auth on
# 然后重启 daemon（见下方故障排查的重启命令）
```

开启后 daemon 的 claude 使用自己独立的登录（`CLAUDE_CONFIG_DIR`），**会话历史、全局 CLAUDE.md、settings、agents/skills、MCP 配置全部仍与终端共享**（软链），互通不受影响。代价：macOS 上首次开启后需要在 App 的 Settings → Account 里登录一次（Linux/Windows 的文件凭证会自动迁移，无需重登）；此后手机上的「切换账号／登出」也只影响 daemon 自己，不再连坐终端。关闭：`config --isolated-claude-auth off` 后重启。

## 五、故障排查

| 现象 | 处理 |
|---|---|
| 手机显示离线／连不上 | 确认电脑开机在线；重启服务：`launchctl unload ~/Library/LaunchAgents/dev.ccpocket.daemon.plist && launchctl load ~/Library/LaunchAgents/dev.ccpocket.daemon.plist` |
| daemon 找不到 claude | 终端确认 `claude --version` 正常；自定义路径用 `cc-pocket-daemon run --claude-bin /path/to/claude`（或设 `CC_POCKET_CLAUDE_BIN`） |
| 语音转写报「未安装 whisper」 | 在 Mac 上 `brew install whisper-cpp` 后重试 |
| 想看 daemon 日志 | 停掉服务后前台跑 `cc-pocket-daemon run`，日志直接打在终端 |
| 配对码过期 | 重新跑 `cc-pocket-daemon pair`（每次生成限时一次性码） |

## 六、卸载

```bash
launchctl unload ~/Library/LaunchAgents/dev.ccpocket.daemon.plist
rm ~/Library/LaunchAgents/dev.ccpocket.daemon.plist
brew uninstall --cask heypandax/tap/cc-pocket
```

手机端直接删 App 即可。

---

疑问与反馈：[GitHub Issues](https://github.com/ac54u-mobile/cc-pocket/issues)
