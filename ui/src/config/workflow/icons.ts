import type { Component } from 'vue'
import {
  ApiOutlined,
  BranchesOutlined,
  CodeOutlined,
  DatabaseOutlined,
  DeleteOutlined,
  FilterOutlined,
  ForkOutlined,
  FunctionOutlined,
  GatewayOutlined,
  NodeIndexOutlined,
  PlayCircleOutlined,
  ReloadOutlined,
  SaveOutlined,
  SelectOutlined,
  SendOutlined,
  SortAscendingOutlined,
  SplitCellsOutlined,
  StopOutlined,
  TableOutlined,
} from '@ant-design/icons-vue'

export const workflowIconMap: Record<string, Component> = {
  play: PlayCircleOutlined,
  stop: StopOutlined,
  branches: BranchesOutlined,
  database: DatabaseOutlined,
  save: SaveOutlined,
  delete: DeleteOutlined,
  reload: ReloadOutlined,
  table: TableOutlined,
  message: SendOutlined,
  api: ApiOutlined,
  code: CodeOutlined,
  iterate: ForkOutlined,
  loop: ReloadOutlined,
  filter: FilterOutlined,
  sort: SortAscendingOutlined,
  split: SplitCellsOutlined,
  template: FunctionOutlined,
  serialize: GatewayOutlined,
  unserialize: NodeIndexOutlined,
  aggregate: FunctionOutlined,
  select: SelectOutlined,
  match: BranchesOutlined,
}

export function getWorkflowIcon(name?: string) {
  return workflowIconMap[name || ''] || FunctionOutlined
}
