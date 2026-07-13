import dotenv from 'dotenv';
import { resolve, dirname } from 'path';
import { fileURLToPath } from 'url';

const __dirname = dirname(fileURLToPath(import.meta.url));
dotenv.config({ path: resolve(__dirname, '../.env') });

export const config = {
  xai: {
    apiKey: process.env.XAI_API_KEY || '',
    agentId: process.env.XAI_AGENT_ID || '',
    baseUrl: 'wss://api.x.ai/v1/realtime',
    model: process.env.XAI_MODEL || 'grok-voice-latest',
    voice: process.env.XAI_VOICE || 'eve',
    instructions: process.env.XAI_INSTRUCTIONS || '你是一个友好的电话助手。请用自然、亲切的语气与来电者交流。',
  },
  twilio: {
    accountSid: process.env.TWILIO_ACCOUNT_SID || '',
    authToken: process.env.TWILIO_AUTH_TOKEN || '',
    phoneNumber: process.env.TWILIO_PHONE_NUMBER || '',
  },
  server: {
    port: parseInt(process.env.PORT || '3000', 10),
  },
} as const;

// 验证必需配置
export function validateConfig(): void {
  const errors: string[] = [];
  if (!config.xai.apiKey) errors.push('缺少 XAI_API_KEY');
  if (!config.xai.agentId) errors.push('缺少 XAI_AGENT_ID');
  if (errors.length > 0) {
    throw new Error(`配置错误:\n${errors.join('\n')}`);
  }
}
