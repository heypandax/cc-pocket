import { XaiRealtimeClient } from './xai-client.js';
import { createServer } from './server.js';
import { validateConfig, config } from './config.js';
import { CC_POCKET_TOOLS, executeTool } from './cc-pocket.js';

/**
 * xAI 电话助手 — CC Pocket 集成版
 *
 * 当有人拨打你的 xAI 手机号时，AI 可以：
 * - 回答 CC Pocket 相关问题（功能介绍、使用方法）
 * - 检查 daemon 运行状态
 * - 提供故障排查建议
 * - 读取项目文档
 */

async function main(): Promise<void> {
  console.log('╔══════════════════════════════════╗');
  console.log('║   📞 xAI 电话助手 + CC Pocket    ║');
  console.log('╚══════════════════════════════════╝');
  console.log('');

  try {
    validateConfig();
  } catch (err) {
    console.error('❌', (err as Error).message);
    process.exit(1);
  }

  const client = new XaiRealtimeClient();

  // 注入 CC Pocket 工具
  client.setTools(CC_POCKET_TOOLS, executeTool);

  // 事件监听
  client.on('user.speaking', (speaking: boolean) => {
    // 可在此处理用户说话状态
  });

  client.on('response.done', () => {
    // 一轮对话完成
  });

  client.on('ai.error', (err: { type: string; message: string }) => {
    console.error(`⚠️  xAI 错误: [${err.type}] ${err.message}`);
  });

  // 优雅退出
  const shutdown = () => {
    console.log('\n🛑 正在关闭...');
    client.disconnect();
    process.exit(0);
  };

  process.on('SIGINT', shutdown);
  process.on('SIGTERM', shutdown);

  // 连接
  try {
    await client.connect();
    console.log('');
    console.log('📞 就绪 — 拨打你的 xAI 手机号进行测试');
    console.log('   AI 可以回答 CC Pocket 相关问题');
    console.log('   按 Ctrl+C 退出');
    console.log('');
  } catch (err) {
    console.error('❌ 连接失败:', (err as Error).message);
    process.exit(1);
  }

  // HTTP 服务器
  const app = createServer(client);
  const server = app.listen(config.server.port, () => {
    console.log(`🌐 HTTP: http://localhost:${config.server.port}/health`);
    console.log('');
  });

  process.stdin.resume();
}

main().catch((err) => {
  console.error('❌ 启动失败:', err);
  process.exit(1);
});
