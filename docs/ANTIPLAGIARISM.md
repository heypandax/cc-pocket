# 来源与洁净室实现声明

CC Pocket 是独立完成的洁净室实现。它与 Anthropic 公开的 `claude` CLI 接口互操作，但不派生自其他项目的源代码。

## 有意互操作的公开接口事实

为实现兼容，项目使用以下公开接口与格式事实：

- `claude` CLI 参数及语义：`-p`、`--output-format stream-json`、`--input-format stream-json`、`--permission-prompt-tool stdio`、`--replay-user-messages`、`--verbose`、`--permission-mode`、`--resume`、`--model`、`--append-system-prompt`；
- 标准输出中的 `stream-json` 事件类型：`system`、`assistant`、`user`、`result`、`control_request`、`control_cancel_request`；
- 工具权限握手：`control_request` 的 `can_use_tool` 与 `control_response` 的 `allow`/`deny`；
- 本地会话目录 `~/.claude/projects/<dir-key>/<sessionId>.jsonl` 及其目录编码方式。

设置子进程工作目录、清理进程树、过滤 `CLAUDECODE` 环境变量、从 JSONL 首部生成会话标题，以及“设备—云端 relay—外拨 daemon”三层架构，均属于常见工程技术。

从法律角度，互操作格式通常受合并原则及相关判例保护；不同实现语言与洁净室隔离也进一步降低了残余风险。

## CC Pocket 的原创部分

线路协议完全由本项目设计，包括 `Envelope`、密封的 `Frame` 层级、所有 `pocket/*` 消息名称，以及 `classDiscriminator = "t"` 的序列化约定。这些命名与结构均有意区别于其他项目。

## 实现规则

Anthropic schema 相关知识只允许存在于 daemon 的 `StreamParser`、`StreamWire` 和 `PermissionBridge`。实现时只能参考本仓库设计/映射说明、Anthropic 官方文档和本机 `claude --help` 输出。编写 CC Pocket 代码时，不得阅读其他项目的非协议源代码。
