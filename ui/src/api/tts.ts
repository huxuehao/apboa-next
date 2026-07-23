import request from '@/utils/request'
import type { ApiResponse } from '@/types'

/**
 * 文字转语音：单段合成，成功返回音频 Blob（audio/*）。
 * 失败时全局异常处理器返回 JSON（blob.type 为 application/json），由调用方甄别。
 * 保留给连通性检测等一次性场景，正文播报走 broadcast 会话通道。
 * POST /api/runtime/tts/speak
 */
export function speak(agentId: string, text: string) {
  return request.post<Blob>('/api/runtime/tts/speak', { agentId, text }, {
    responseType: 'blob',
    // 长句合成 + 本地模型冷启动都慢于普通接口
    timeout: 120000
  })
}

/**
 * 手动朗读（历史消息等）：走流式会话通道，音频经已订阅的 WS 音频流返回。
 * POST /api/runtime/tts/broadcast
 */
export function broadcast(threadId: string, agentId: string, text: string) {
  return request.post<ApiResponse<void>>('/api/runtime/tts/broadcast', { threadId, agentId, text })
}
