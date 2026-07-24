import request from '@/utils/request'
import type { ApiResponse } from '@/types'

/**
 * 语音转文字：整段 WAV 识别
 * POST /api/runtime/asr/recognize
 */
export function recognize(agentId: string, audio: Blob) {
  const formData = new FormData()
  formData.append('agentId', agentId)
  formData.append('file', audio, 'voice.wav')
  return request.post<ApiResponse<string>>('/api/runtime/asr/recognize', formData, {
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
}
