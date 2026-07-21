/**
 * 自动化任务相关类型定义
 *
 * @author huxuehao
 */

/**
 * 任务运行记录 VO
 */
export interface JobRecordVO {
  /** 任务ID */
  jobId: number
  /** 记录ID（智能体任务时指向 ChatSession.id，工作流任务时指向 WorkflowRun.id） */
  recordId: number
  /** 记录创建时间 */
  createTime: string
}

/**
 * 分页查询参数
 */
export interface JobPageParams {
  /** 页码 */
  page: number
  /** 每页数量 */
  size: number
  /** 任务类型（可选） */
  type?: 'AGENT' | 'WORKFLOW' | null
  /** 关键字搜索（可选） */
  keyword?: string
}

/**
 * Agent 对话消息 VO
 */
export interface ChatMessageVO {
  id: number
  sessionId: number
  role: string
  content: string
  depth: number
  createdAt: string
}

/**
 * Workflow 运行结果
 */
export interface WorkflowRunResult {
  run: {
    id: number
    workflowId: number
    status: string
    startTime: string
    endTime: string
  }
  nodeExecutions: Array<{
    nodeId: string
    nodeTitle: string
    nodeType: string
    status: string
    inputs: Record<string, unknown>
    outputs: Record<string, unknown>
    startTime: string
    endTime: string
    duration: number
  }>
  output: unknown
}
