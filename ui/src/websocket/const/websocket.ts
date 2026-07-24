export const WS_CONFIG = {
  URL: (() => {
    const isHttps = window.location.protocol === 'https:';
    const protocol = isHttps ? 'wss:' : 'ws:';
    return `${protocol}//${window.location.hostname}${window.location.port ? ':' + window.location.port : ''}/api/ws/apboa`;
  })(),
  MAX_RECONNECT_ATTEMPTS: 1440, // 约6小时
  INITIAL_RECONNECT_DELAY: 45000, // 45秒
  MAX_RECONNECT_DELAY: 300000, // 最大重连延迟5分钟
  PING_INTERVAL: 30000, // 30秒发送一次ping
  PONG_TIMEOUT: 5000, // 等待pong响应超时时间
};

export const WS_MESSAGE_TYPES = {
  PING: 'PING',
  PONG: 'PONG',

  CLIENT: 'CLIENT',
  ACCOUNT_ROLE_CHANGE: 'ACCOUNT_ROLE_CHANGE',
  WORKSPACE_FILE_CHANGE: 'WORKSPACE_FILE_CHANGE',

  /** 语音播报：订阅 / 退订 / 流控制事件（音频本体走二进制帧，事件名 TTS_AUDIO_FRAME） */
  TTS_SUBSCRIBE: 'TTS_SUBSCRIBE',
  TTS_UNSUBSCRIBE: 'TTS_UNSUBSCRIBE',
  TTS_STREAM: 'TTS_STREAM',
} as const;

/** 二进制音频帧在 eventBus 上的事件名（非服务端消息类型，仅前端内部分发用） */
export const TTS_AUDIO_FRAME_EVENT = 'TTS_AUDIO_FRAME';

export type WSMessageType = typeof WS_MESSAGE_TYPES[keyof typeof WS_MESSAGE_TYPES];
