import request from '@/utils/request'
import type { ApiResponse } from '@/types'
import type { SkillsHubVO, SkillImportResult } from '@/types'

/**
 * SkillHub 技能市场 API
 */

/**
 * 搜索技能
 * GET /skill/hub/search
 */
export function search(params: SkillHubSearchParams) {
  return request.get<ApiResponse<SkillsHubVO[]>>('/api/skill/hub/search', {
    params
  })
}

/**
 * 下载/导入技能
 * GET /skill/hub/download
 */
export function download(slug: string, category: string, name: string) {
  return request.get<ApiResponse<SkillImportResult>>('/api/skill/hub/download', {
    params: { slug, category }
  })
}

/**
 * SkillHub 搜索参数
 */
export interface SkillHubSearchParams {
  keyword?: string
  category?: string
  source?: string
  labels?: string
  sortBy?: string
  order?: string
  page: number
}
