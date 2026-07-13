import WebSocket from 'ws';
import { EventEmitter } from 'events';
import { config } from './config.js';
import type { ClientEvent, SessionUpdateEvent, ServerEvent } from './types.js';

export interface ToolDefinition {
  type: string;
  name: string;
  description: string;
  parameters: Record<string, unknown>;
}

export type ToolExecutor = (name: string, args: Record<string, unknown>) => string | Promise<string>;

export class XaiRealtimeClient extends EventEmitter {
  private ws: WebSocket | null = null;
  private connected = false;
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null;
  private shouldReconnect = true;
  private tools: ToolDefinition[] = [];
  private toolExecutor: ToolExecutor | null = null;

  /** 设置 AI 可用的工具 */
  setTools(tools: ToolDefinition[], executor: ToolExecutor): void {
    this.tools = tools;
    this.toolExecutor = executor;
  }

  async connect(): Promise<void> {
    return new Promise((resolve, reject) => {
      const url = `${config.xai.baseUrl}?agent_id=${config.xai.agentId}&model=${config.xai.model}`;

      console.log(`🔗 连接到 xAI Agent...`);
      console.log(`   Agent: ${config.xai.agentId}`);
      console.log(`   Model: ${config.xai.model}`);
      if (this.tools.length > 0) {
        console.log(`   Tools: ${this.tools.map((t) => t.name).join(', ')}`);
      }

      this.ws = new WebSocket(url, {
        headers: { Authorization: `Bearer ${config.xai.apiKey}` },
      });

      const timeout = setTimeout(() => reject(new Error('连接超时 (10s)')), 10000);

      this.ws.on('open', () => {
        clearTimeout(timeout);
        this.connected = true;
        console.log('✅ WebSocket 已连接 — 等待来电...');

        // 配置 session，包含 tools
        const sessionConfig: SessionUpdateEvent['session'] = {
          instructions: config.xai.instructions,
          turn_detection: { type: 'server_vad' },
        };
        if (this.tools.length > 0) {
          (sessionConfig as any).tools = this.tools;
        }
        this.updateSession(sessionConfig);

        resolve();
      });

      this.ws.on('message', (raw: Buffer) => {
        try {
          const event = JSON.parse(raw.toString());
          this.handleEvent(event);
        } catch {
          // 忽略解析错误
        }
      });

      this.ws.on('error', (err: Error) => {
        clearTimeout(timeout);
        console.error('❌ WebSocket 错误:', err.message);
        this.emit('error', err);
        if (!this.connected) reject(err);
      });

      this.ws.on('close', (code: number) => {
        this.connected = false;
        console.log(`🔌 WebSocket 断开 (code: ${code})`);
        this.emit('close', code);

        if (this.shouldReconnect) {
          const delay = 3000;
          console.log(`🔄 ${delay / 1000}s 后自动重连...`);
          this.reconnectTimer = setTimeout(() => this.connect(), delay);
        }
      });
    });
  }

  // ============================================
  // 事件处理
  // ============================================

  private handleEvent(event: any): void {
    switch (event.type) {
      case 'session.created':
        console.log(`📋 会话: ${event.session.id}`);
        break;

      case 'conversation.created':
        console.log(`💬 对话: ${event.conversation.id}`);
        break;

      // === 用户说话转写 ===
      case 'response.output_audio_transcript.delta':
        process.stdout.write(event.delta);
        break;

      case 'response.output_audio_transcript.done':
        process.stdout.write('\n');
        console.log(`👤 用户: ${event.transcript}`);
        break;

      // === AI 文字回复 ===
      case 'response.output_text.delta':
        process.stdout.write(event.delta);
        break;

      case 'response.output_text.done':
        process.stdout.write('\n');
        console.log(`🤖 AI: ${event.text}`);
        break;

      // === AI 音频输出 ===
      case 'response.output_audio.delta':
        this.emit('audio.output', event.delta);
        break;

      case 'response.output_audio.done':
        this.emit('audio.output.done');
        break;

      // === 响应完成 ===
      case 'response.done': {
        const output = event.response?.output || [];
        for (const item of output) {
          if (item.type === 'text') {
            console.log(`🤖 AI: ${item.transcript || item.text}`);
          }
        }
        if (event.response?.status !== 'completed') {
          console.warn(`⚠️  响应状态: ${event.response.status}`);
        }
        this.emit('response.done', event.response);
        break;
      }

      // === Function Call ===
      case 'response.function_call_arguments.done':
        this.handleFunctionCall(event).catch((err) =>
          console.error('Function call 执行失败:', err),
        );
        break;

      // === VAD 事件 ===
      case 'input_audio_buffer.speech_started':
        console.log('🎤 用户开始说话...');
        this.emit('user.speaking', true);
        break;

      case 'input_audio_buffer.speech_stopped':
        console.log('🎤 用户停止说话');
        this.emit('user.speaking', false);
        break;

      case 'error':
        console.error(`❌ xAI 错误 [${event.error?.type}]: ${event.error?.message}`);
        this.emit('ai.error', event.error);
        break;

      default:
        break;
    }

    this.emit('event', event);
  }

  /** 处理 AI 发起的 function call */
  private async handleFunctionCall(event: any): Promise<void> {
    const fnName = event.name;
    const fnCallId = event.call_id;
    let args: Record<string, unknown> = {};

    try {
      args = JSON.parse(event.arguments || '{}');
    } catch {
      args = {};
    }

    console.log(`🔧 AI 调用工具: ${fnName}(${JSON.stringify(args)})`);

    let result: string;
    if (this.toolExecutor) {
      result = await Promise.resolve(this.toolExecutor(fnName, args));
    } else {
      result = '工具执行器未配置';
    }

    console.log(`🔧 工具结果: ${result.slice(0, 200)}${result.length > 200 ? '...' : ''}`);

    // 返回 function call 结果给 AI
    this.send({
      type: 'conversation.item.create',
      item: {
        type: 'function_call_output',
        call_id: fnCallId,
        output: result,
      },
    } as any);

    // 触发 AI 继续回复
    this.send({ type: 'response.create' } as any);
  }

  // ============================================
  // 客户端动作
  // ============================================

  updateSession(session: SessionUpdateEvent['session']): void {
    this.send({ type: 'session.update', session } as ClientEvent);
  }

  sendText(text: string): void {
    this.send({
      type: 'conversation.item.create',
      item: {
        type: 'message', role: 'user',
        content: [{ type: 'input_text', text }],
      },
    } as ClientEvent);
    this.send({ type: 'response.create' } as ClientEvent);
  }

  sendAudio(pcmBase64: string): void {
    this.send({
      type: 'input_audio_buffer.append',
      audio: pcmBase64,
    } as ClientEvent);
  }

  commitAudio(): void {
    this.send({ type: 'input_audio_buffer.commit' } as ClientEvent);
    this.send({ type: 'response.create' } as ClientEvent);
  }

  interrupt(): void {
    this.send({ type: 'response.cancel' } as ClientEvent);
    this.send({ type: 'input_audio_buffer.clear' } as ClientEvent);
  }

  hangup(): void {
    this.send({ type: 'response.cancel' } as ClientEvent);
  }

  private send(event: any): void {
    if (!this.ws || !this.connected) {
      console.error('⚠️  WebSocket 未连接');
      return;
    }
    this.ws.send(JSON.stringify(event));
  }

  // ============================================
  // 生命周期
  // ============================================

  disconnect(): void {
    this.shouldReconnect = false;
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
    if (this.ws) {
      this.ws.close();
      this.ws = null;
    }
    this.connected = false;
  }

  get isConnected(): boolean {
    return this.connected;
  }
}
