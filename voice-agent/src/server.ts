import express from 'express';
import type { Request, Response } from 'express';
import { XaiRealtimeClient } from './xai-client.js';

/**
 * Express 服务器 — 健康检查 + Twilio webhook 集成
 */
export function createServer(xaiClient: XaiRealtimeClient): express.Express {
  const app = express();
  app.use(express.urlencoded({ extended: true }));
  app.use(express.json());

  // === 健康检查 ===
  app.get('/health', (_req: Request, res: Response) => {
    res.json({
      status: 'ok',
      xaiConnected: xaiClient.isConnected,
      uptime: process.uptime(),
      memory: process.memoryUsage().rss,
    });
  });

  // === Twilio 来电 webhook ===
  // 当 Twilio 收到来电时，返回 TwiML 指令把音频流桥接到 xAI
  app.post('/incoming-call', async (req: Request, res: Response) => {
    const { From, CallSid, CallerName } = req.body;
    console.log(`📞 来电: ${From} | ${CallerName || ''} | SID: ${CallSid}`);

    // 确保 xAI 连接活跃
    if (!xaiClient.isConnected) {
      console.warn('⚠️  xAI 未连接，来电将无法处理');
    }

    const twiml = `<?xml version="1.0" encoding="UTF-8"?>
<Response>
  <Say voice="alice">你好，正在为你连接 AI 助手，请稍候。</Say>
  <Connect>
    <Stream url="wss://${req.hostname}/media-stream">
      <Parameter name="callerId" value="${From}" />
    </Stream>
  </Connect>
</Response>`;

    res.type('text/xml').send(twiml);
  });

  // === Twilio 通话状态回调 ===
  app.post('/call-status', (req: Request, res: Response) => {
    const { CallSid, CallStatus, CallDuration, From, To } = req.body;
    const emoji =
      CallStatus === 'completed' ? '✅' :
      CallStatus === 'in-progress' ? '📞' :
      CallStatus === 'ringing' ? '🔔' :
      CallStatus === 'busy' ? '📵' :
      CallStatus === 'no-answer' ? '🙅' : '📊';

    console.log(`${emoji} 通话状态: ${CallStatus} | ${From} → ${To} | 时长: ${CallDuration || 0}s`);
    res.sendStatus(200);
  });

  // === 切换 Agent 配置 ===
  app.post('/agent/config', (req: Request, res: Response) => {
    const { instructions, voice } = req.body;
    if (instructions || voice) {
      xaiClient.updateSession({
        ...(instructions && { instructions }),
        ...(voice && { voice }),
      });
      console.log('🔄 Agent 配置已更新:', { instructions, voice });
    }
    res.json({ ok: true });
  });

  return app;
}
