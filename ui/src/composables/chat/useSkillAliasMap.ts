/**
 * 技能包 name→alias 展示映射（页面级单例）
 *
 * ChatInputEditor 加载当前 agent 的技能后写入映射，AgentSkillTag 在消息流渲染
 * <agent-skill>name</agent-skill> 标签时按 name 取别名显示。
 * 规则：撞 key 取第一个；无别名（或未命中）回退原 name。
 *
 * @author huxuehao
 */
import { ref } from 'vue'

/** name -> alias 映射（模块级共享，当前 chat 页面对应一个 agent） */
const aliasMap = ref<Record<string, string>>({})

export function useSkillAliasMap() {
  /**
   * 用技能列表刷新映射（撞 key 取第一个、仅收录有别名的项）
   */
  const setFromSkills = (skills: Array<{ name?: string; alias?: string | null }>) => {
    const m: Record<string, string> = {}
    for (const s of skills || []) {
      if (s.name && s.alias && !(s.name in m)) {
        m[s.name] = s.alias
      }
    }
    aliasMap.value = m
  }

  return { aliasMap, setFromSkills }
}
