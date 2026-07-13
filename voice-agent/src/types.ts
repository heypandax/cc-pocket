// xAI Realtime API 事件类型定义

// === 客户端 → 服务端事件 ===
export interface SessionUpdateEvent {
  type: 'session.update';
  session: {
    voice?: string;
    instructions?: string;
    turn_detection?: {
      type: 'server_vad';
      threshold?: number;
      silence_duration_ms?: number;
      prefix_padding_ms?: number;
      idle_timeout_ms?: number;
    } | null;
    audio?: {
      input?: { format: string; sample_rate: number };
      output?: { format: string; sample_rate: number; speed?: number };
    };
    reasoning?: { effort?: 'high' | 'none' };
    replace?: Record<string, string>;
    resumption?: { enabled: boolean };
  };
}

export interface ConversationItemCreateEvent {
  type: 'conversation.item.create';
  item: {
    type: 'message';
    role: 'user' | 'assistant';
    content: Array<{ type: 'input_text' | 'output_text'; text: string }>;
  };
}

export interface ResponseCreateEvent {
  type: 'response.create';
  response?: {
    modalities?: Array<'text' | 'audio'>;
    instructions?: string;
    conversation_id?: string;
  };
}

export interface ResponseCancelEvent {
  type: 'response.cancel';
}

export interface InputAudioBufferAppendEvent {
  type: 'input_audio_buffer.append';
  audio: string; // base64
}

export interface InputAudioBufferCommitEvent {
  type: 'input_audio_buffer.commit';
}

export interface InputAudioBufferClearEvent {
  type: 'input_audio_buffer.clear';
}

export type ClientEvent =
  | SessionUpdateEvent
  | ConversationItemCreateEvent
  | ResponseCreateEvent
  | ResponseCancelEvent
  | InputAudioBufferAppendEvent
  | InputAudioBufferCommitEvent
  | InputAudioBufferClearEvent;

// === 服务端 → 客户端事件 ===
export interface SessionCreatedEvent {
  type: 'session.created';
  session: { id: string };
}

export interface SessionUpdatedEvent {
  type: 'session.updated';
  session: Record<string, unknown>;
}

export interface ConversationCreatedEvent {
  type: 'conversation.created';
  conversation: { id: string };
}

export interface ConversationItemCreatedEvent {
  type: 'conversation.item.created';
  item: Record<string, unknown>;
}

export interface ResponseAudioTranscriptDeltaEvent {
  type: 'response.output_audio_transcript.delta';
  response_id: string;
  item_id: string;
  output_index: number;
  content_index: number;
  delta: string;
}

export interface ResponseAudioTranscriptDoneEvent {
  type: 'response.output_audio_transcript.done';
  response_id: string;
  item_id: string;
  output_index: number;
  content_index: number;
  transcript: string;
}

export interface ResponseAudioDeltaEvent {
  type: 'response.output_audio.delta';
  response_id: string;
  item_id: string;
  output_index: number;
  content_index: number;
  delta: string; // base64 encoded PCM
}

export interface ResponseAudioDoneEvent {
  type: 'response.output_audio.done';
  response_id: string;
  item_id: string;
  output_index: number;
  content_index: number;
}

export interface ResponseDoneEvent {
  type: 'response.done';
  response: {
    id: string;
    status: 'completed' | 'failed' | 'cancelled';
    output: Array<{
      type: 'text' | 'audio';
      transcript?: string;
    }>;
  };
}

export interface InputAudioBufferSpeechStartedEvent {
  type: 'input_audio_buffer.speech_started';
}

export interface InputAudioBufferSpeechStoppedEvent {
  type: 'input_audio_buffer.speech_stopped';
}

export interface ErrorEvent {
  type: 'error';
  error: { type: string; message: string };
}

export type ServerEvent =
  | SessionCreatedEvent
  | SessionUpdatedEvent
  | ConversationCreatedEvent
  | ConversationItemCreatedEvent
  | ResponseAudioTranscriptDeltaEvent
  | ResponseAudioTranscriptDoneEvent
  | ResponseAudioDeltaEvent
  | ResponseAudioDoneEvent
  | ResponseDoneEvent
  | InputAudioBufferSpeechStartedEvent
  | InputAudioBufferSpeechStoppedEvent
  | ErrorEvent;
