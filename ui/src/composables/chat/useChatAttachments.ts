/**
 * 聊天输入框附件管理 Composable
 * 负责附件类型校验、上传、删除等逻辑
 *
 * @author huxuehao
 */

import { message } from 'ant-design-vue'
import * as attachApi from '@/api/attach'
import { isImageExtension, setCachedAttachUrl } from '@/utils/chat/attachImage'
import type { UploadedFileItem } from '@/types'

/**
 * useChatAttachments 选项
 */
export interface UseChatAttachmentsOptions {
  /** 获取当前附件列表 */
  getFiles: () => UploadedFileItem[]
  /** 写回附件列表（用于 v-model:uploadedFiles 透传） */
  setFiles: (files: UploadedFileItem[]) => void
  /** 获取允许的文件扩展名列表（不含点号，例如 ['png','jpg']） */
  getAllowedTypes: () => string[] | undefined
}

/**
 * 格式化文件大小显示
 *
 * @param bytes 字节数
 * @return 可读大小字符串
 */
export function formatFileSize(bytes: number): string {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return `${parseFloat((bytes / Math.pow(k, i)).toFixed(2))} ${sizes[i]}`
}

/**
 * 从文件名解析扩展名（小写，不含点号）
 *
 * @param fileName 文件名
 * @return 扩展名
 */
export function getExtension(fileName: string): string {
  const lastDot = fileName.lastIndexOf('.')
  return lastDot > -1 ? fileName.slice(lastDot + 1).toLowerCase() : ''
}

/**
 * 聊天附件管理 Composable
 *
 * @param opts 选项
 * @return 附件操作方法
 */
export function useChatAttachments(opts: UseChatAttachmentsOptions) {
  /**
   * 检查文件类型是否在允许列表中
   */
  const isFileTypeAllowed = (extension: string): boolean => {
    const allowed = opts.getAllowedTypes()
    if (!allowed?.length) return true
    return allowed.some((t) => t.toLowerCase() === extension)
  }

  /**
   * 根据允许的类型生成 input accept 属性值
   */
  const fileAcceptAttr = (): string => {
    const allowed = opts.getAllowedTypes()
    if (!allowed?.length) return '*/*'
    return allowed.map((t) => `.${t}`).join(',')
  }

  /**
   * 处理文件选择 change 事件，支持批量上传与上传中状态展示
   *
   * @param e 文件 input 的 change 事件
   */
  const handleFileChange = async (e: Event) => {
    const input = e.target as HTMLInputElement
    const files = input.files
    if (!files?.length) {
      input.value = ''
      return
    }
    const fileArray = Array.from(files)
    const allowedFiles: File[] = []
    const rejectedNames: string[] = []
    for (const file of fileArray) {
      const ext = getExtension(file.name)
      if (isFileTypeAllowed(ext)) {
        allowedFiles.push(file)
      } else {
        rejectedNames.push(file.name)
      }
    }
    if (rejectedNames.length > 0) {
      message.warning(`以下文件类型不允许上传: ${rejectedNames.join(', ')}`)
    }
    if (allowedFiles.length === 0) {
      input.value = ''
      return
    }

    const current = opts.getFiles()
    const newList = [...current]
    const tempIds: string[] = []

    // 立即将文件加入列表并显示（上传中状态）
    for (let i = 0; i < allowedFiles.length; i++) {
      const file = allowedFiles[i]
      if (!file) continue
      const tempId = `temp-${Date.now()}-${i}-${Math.random().toString(36).slice(2, 9)}`
      tempIds.push(tempId)
      const extension = getExtension(file.name)
      newList.push({
        id: tempId,
        name: file.name,
        extension,
        size: formatFileSize(file.size),
        uploading: true,
        // 图片生成本地 objectURL：选图即出预览，上传中/发送后均可复用
        localUrl: isImageExtension(extension) ? URL.createObjectURL(file) : undefined
      })
    }
    opts.setFiles(newList)
    input.value = ''

    // 后台逐个上传，完成后更新对应项
    for (let i = 0; i < allowedFiles.length; i++) {
      const file = allowedFiles[i]
      const tempId = tempIds[i]
      if (!file || tempId === undefined) continue
      try {
        const res = await attachApi.upload(file)
        const data = res?.data?.data
        if (data) {
          // 文档类型需要同步等待服务端文本提取完成，再标记上传结束
          // 防止用户误以为已上传完成而提前发送或删除，导致僵尸 .apboa 文件
          const ext = getExtension(file.name)
          const docExtensions = ['doc', 'docx', 'xlsx', 'xls', 'csv', 'pptx', 'ppt', 'pdf', 'txt', 'md']
          if (docExtensions.includes(ext)) {
            let parseSuccess = false
            try {
              const parseRes = await attachApi.parseText(data)
              parseSuccess = !!parseRes?.data?.data
            } catch {
              // parseText 接口调用异常，视为解析失败
            }

            if (!parseSuccess) {
              // 解析失败：删除后端附件并从列表移除
              attachApi.remove([data]).catch(() => {})
              removeItemAndReleaseLocalUrl(tempId)
              continue
            }
          }

          const updated = opts.getFiles().map((item) => {
            if (item.id !== tempId) return item
            // 图片：本地预览 objectURL 按真实附件 id 登记进模块缓存，
            // 发送后消息气泡直接命中缓存零请求；所有权移交缓存，此处不再 revoke
            if (item.localUrl) {
              setCachedAttachUrl(data, item.localUrl)
            }
            return { ...item, id: data, uploading: false }
          })
          opts.setFiles(updated)
        } else {
          removeItemAndReleaseLocalUrl(tempId)
        }
      } catch {
        removeItemAndReleaseLocalUrl(tempId)
      }
    }
  }

  /**
   * 从列表移除指定项，并释放尚未移交缓存的本地预览 objectURL（上传未完成场景）
   */
  const removeItemAndReleaseLocalUrl = (id: string) => {
    const target = opts.getFiles().find((f) => f.id === id)
    if (target?.localUrl && target.id.startsWith('temp-')) {
      URL.revokeObjectURL(target.localUrl)
    }
    opts.setFiles(opts.getFiles().filter((f) => f.id !== id))
  }

  /**
   * 移除单个附件，已完成上传的会调用后端删除接口
   *
   * @param item 待移除附件
   */
  const removeFile = async (item: UploadedFileItem) => {
    // 上传中的文件无需调用删除接口
    if (!item.uploading && !item.id.startsWith('temp-')) {
      await attachApi.remove([item.id])
    }
    // 上传中被移除：objectURL 尚未移交缓存，需在此释放；已完成的归缓存管理
    if (item.localUrl && item.id.startsWith('temp-')) {
      URL.revokeObjectURL(item.localUrl)
    }
    const newList = opts.getFiles().filter((f) => f.id !== item.id)
    opts.setFiles(newList)
  }

  return {
    formatFileSize,
    getExtension,
    isFileTypeAllowed,
    fileAcceptAttr,
    handleFileChange,
    removeFile
  }
}
