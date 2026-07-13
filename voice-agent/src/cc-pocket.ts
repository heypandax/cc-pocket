/**
 * CC Pocket 集成模块
 *
 * 为 xAI 电话助手提供与 CC Pocket daemon 交互的能力：
 * - 检查 daemon 运行状态
 * - 读取项目文档回答用户问题
 * - 提供故障排查建议
 */

import { execSync } from 'child_process';
import { readFileSync, existsSync } from 'fs';
import { join, dirname } from 'path';
import { fileURLToPath } from 'url';
import type { ToolDefinition } from './xai-client.js';

const __dirname = dirname(fileURLToPath(import.meta.url));
// 文档根目录：源码布局下是仓库根（dist/../..）；部署布局下升级脚本把
// README/docs 拷进了 voice-agent 目录本身（dist/..）。取第一个有 README.md 的。
const CC_POCKET_ROOT =
  [join(__dirname, '..'), join(__dirname, '../..')].find((p) =>
    existsSync(join(p, 'README.md')),
  ) ?? join(__dirname, '../..');
const DAEMON_LOOPBACK = 8799;

// === 知识库（从 CC Pocket 项目文档提取） ===

const KNOWLEDGE_BASE = {
  what_is_cc_pocket: `
CC Pocket 是一个手机 App，让你在 iPhone 或 Android 上远程操控你电脑/服务器里的 Claude Code、OpenAI Codex 和 Cursor Agent。

架构：手机 App ↔ relay（云端中转）↔ daemon（你的服务器/电脑）↔ Claude Code / Codex / Cursor CLI

主要功能：
- 在手机上新建/恢复 AI 编程会话
- 实时查看 AI 回复、工具调用、文件改动
- 权限审批（风险分级 + 影响范围）
- 图片附件、语音输入、斜杠命令
- 一部手机配对多台电脑
`,

  common_issues: `
CC Pocket 常见问题：

1. 手机连不上：
   - 检查 daemon 是否在运行（systemctl --user status cc-pocket-daemon）
   - 确认手机和电脑在同一网络或 relay 可达
   - 最常见原因：同时跑了两个 daemon，互相冲突
   - 用升级脚本修复：bash ~/bin/update-cc-pocket-daemon.sh

2. 会话卡死/状态乱跳：
   - 也是多 daemon 冲突的典型症状
   - ps aux | grep 'cc-pocket-daemon/lib' 检查进程数（正常应为 1）

3. relay 断连：
   - 检查网络代理/VPN 是否拦截 relay 连接
   - relay 会自动重连，最多等 30 秒
`,

  pairing: `
CC Pocket 配对方法：

1. 确保 daemon 在运行：cc-pocket-daemon status
2. 生成配对码：cc-pocket-daemon pair
3. 打开手机上的 CC Pocket App，扫描终端显示的二维码
4. 或手动输入 6 位配对码
5. 配对码有效期 60 秒

如果 pair 命令提示 "daemon is up but its relay link is down"：
- 等待 daemon 重连 relay（最多 30 秒）
- 检查网络是否能访问 relay 地址
`,

  troubleshooting: `
CC Pocket 故障排查步骤：

1. 先跑 cc-pocket-daemon status 看整体状态
2. 确认只有一个 daemon 进程在跑：
   ps aux | grep 'cc-pocket-daemon/lib' | grep -v grep | wc -l  # 正常应为 1
3. 确认 daemon 连上了 relay：
   检查 /status 的 attached 字段
4. 查看 daemon 日志：journalctl --user -u cc-pocket-daemon -n 50
5. 如果都不行，重置 daemon：
   bash ~/bin/update-cc-pocket-daemon.sh
`,
};

// === Daemon 状态查询 ===

export interface DaemonStatus {
  running: boolean;
  accountId?: string;
  relayConnected?: boolean;
  version?: string;
  error?: string;
}

export function checkDaemonStatus(): DaemonStatus {
  try {
    const url = `http://127.0.0.1:${DAEMON_LOOPBACK}/status`;
    const result = execSync(`curl -s --max-time 3 ${url}`, { encoding: 'utf-8' }).trim();

    if (!result) {
      return { running: false, error: 'daemon 未响应' };
    }

    // 尝试解析 JSON
    try {
      const data = JSON.parse(result);
      return {
        running: true,
        accountId: data.accountId,
        relayConnected: data.attached ?? false,
        version: data.version,
      };
    } catch {
      // 可能是纯文本状态
      return { running: true };
    }
  } catch {
    return {
      running: false,
      error: 'daemon 未运行在端口 ' + DAEMON_LOOPBACK,
    };
  }
}

export function countDaemonProcesses(): number {
  try {
    // 匹配 classpath 里的 cc-pocket-daemon/lib，避免把 voice-agent 自身等无关进程算进去
    const result = execSync(
      "ps aux | grep 'cc-pocket-daemon/lib' | grep -v grep | wc -l",
      { encoding: 'utf-8' },
    ).trim();
    return parseInt(result, 10) || 0;
  } catch {
    return -1;
  }
}

// === 文档读取 ===

export function readDoc(docPath: string): string | null {
  const fullPath = join(CC_POCKET_ROOT, docPath);
  if (!existsSync(fullPath)) return null;
  try {
    return readFileSync(fullPath, 'utf-8');
  } catch {
    return null;
  }
}

// === xAI Function Tools 定义 ===

export const CC_POCKET_TOOLS: ToolDefinition[] = [
  {
    type: 'function',
    name: 'check_cc_pocket_status',
    description: '检查 CC Pocket daemon 运行状态：是否在运行、relay 连接、账户 ID',
    parameters: {
      type: 'object',
      properties: {},
    },
  },
  {
    type: 'function',
    name: 'get_cc_pocket_knowledge',
    description: '获取 CC Pocket 的知识库信息：介绍、常见问题、配对方法、故障排查',
    parameters: {
      type: 'object',
      properties: {
        topic: {
          type: 'string',
          enum: ['what_is_cc_pocket', 'common_issues', 'pairing', 'troubleshooting'],
          description: '要查询的主题',
        },
      },
      required: ['topic'],
    },
  },
  {
    type: 'function',
    name: 'read_cc_pocket_document',
    description: '读取 CC Pocket 项目的文档内容',
    parameters: {
      type: 'object',
      properties: {
        doc_name: {
          type: 'string',
          enum: ['README.md', 'docs/USAGE.md', 'docs/RUN.md', 'docs/SECURITY.md', 'CHANGELOG.md'],
          description: '要读取的文档文件名',
        },
      },
      required: ['doc_name'],
    },
  },
];

// === Tool 执行器 ===

export function executeTool(
  name: string,
  args: Record<string, unknown>,
): string {
  switch (name) {
    case 'check_cc_pocket_status': {
      const status = checkDaemonStatus();
      const procCount = countDaemonProcesses();

      if (!status.running) {
        return `CC Pocket daemon 状态：❌ 未运行\n` +
          `详情：${status.error}\n` +
          `建议：运行 systemctl --user start cc-pocket-daemon 启动 daemon，或用 cc-pocket-daemon status 查看详情`;
      }

      return `CC Pocket daemon 状态：✅ 运行中\n` +
        `账户 ID：${status.accountId?.slice(0, 12) ?? '未知'}…\n` +
        `Relay 连接：${status.relayConnected ? '✅ 已连接' : '❌ 断开'}\n` +
        `版本：${status.version ?? '未知'}\n` +
        `进程数：${procCount}（正常应为 1）${procCount !== 1 ? ' ⚠️ 多个 daemon 会导致冲突！' : ''}`;
    }

    case 'get_cc_pocket_knowledge': {
      const topic = args.topic as string;
      return KNOWLEDGE_BASE[topic as keyof typeof KNOWLEDGE_BASE]
        ?? `未知主题: ${topic}。可选: ${Object.keys(KNOWLEDGE_BASE).join(', ')}`;
    }

    case 'read_cc_pocket_document': {
      const docName = args.doc_name as string;
      const content = readDoc(docName);
      if (!content) return `文档 ${docName} 不存在或无法读取`;
      // 限制长度避免超过 token 限制
      return content.slice(0, 8000) + (content.length > 8000 ? '\n... (内容已截断)' : '');
    }

    default:
      return `未知工具: ${name}`;
  }
}
