/**
 * 工具 toolId→name 展示映射（页面级单例）
 *
 * tool 的 name 本身即友好显示名（工具页面当别名用），但消息里 <agent-tool> 标签的值是
 * toolId（工具编号）。ChatInputEditor 加载当前 agent 的工具后写入映射，AgentToolTag
 * 渲染消息标签时按 toolId 取 name 显示。规则：撞 key 取第一个；未命中回退原 toolId。
 *
 * @author huxuehao
 */
import { ref } from 'vue'

/** toolId -> name 映射（模块级共享，当前 chat 页面对应一个 agent） */
const nameMap = ref<Record<string, string>>({})

export function useToolNameMap() {
  /**
   * 用工具列表刷新映射（撞 key 取第一个）
   */
  const setFromTools = (tools: Array<{ toolId?: string; name?: string }>) => {
    const m: Record<string, string> = {}
    for (const t of tools || []) {
      if (t.toolId && t.name && !(t.toolId in m)) {
        m[t.toolId] = t.name
      }
    }
    nameMap.value = m
  }

  return { nameMap, setFromTools }
}
