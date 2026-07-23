import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { test } from 'node:test'
import ts from 'typescript'

const sourceUrl = new URL('../src/utils/chat/mentionPicker.ts', import.meta.url)
const dropdownSourceUrl = new URL('../src/components/chat/ResourceMentionDropdown.vue', import.meta.url)

async function loadPickerModule() {
  let source = ''
  try {
    source = await readFile(sourceUrl, 'utf8')
  } catch {
    // RED 阶段：实现文件尚不存在，下面的行为断言会明确失败。
  }
  const javascript = ts.transpileModule(source, {
    compilerOptions: {
      module: ts.ModuleKind.ESNext,
      target: ts.ScriptTarget.ES2022,
    },
  }).outputText
  return import(`data:text/javascript;base64,${Buffer.from(javascript).toString('base64')}`)
}

const picker = await loadPickerModule()

const sendMessage = {
  kind: 'agent-mcp',
  id: 'send_message',
  name: 'send_message',
  description: '发送即时消息',
  raw: { serverId: 'feishu', serverName: '飞书 MCP' },
}

const searchMessage = {
  kind: 'agent-mcp',
  id: 'search_message',
  name: 'search_message',
  description: '搜索历史消息',
  raw: { serverId: 'feishu', serverName: '飞书 MCP' },
}

const systemNotice = {
  kind: 'agent-tool',
  id: 'notice',
  name: '系统通知',
  description: '向指定用户发送系统通知',
}

const dataSkill = {
  kind: 'agent-skill',
  id: 'data-analysis',
  name: 'data-analysis',
  alias: '数据分析',
  description: '分析表格数据',
}

const allItems = [searchMessage, systemNotice, dataSkill, sendMessage]

test('全局搜索同时匹配名称、分隔符、MCP 服务、别名和描述', () => {
  const searchMentionItems = picker.searchMentionItems
  const actual = typeof searchMentionItems === 'function'
    ? {
        prefix: searchMentionItems(allItems, 'send').map((item) => item.id),
        separators: searchMentionItems(allItems, 'send message').map((item) => item.id),
        server: searchMentionItems(allItems, '飞书').map((item) => item.id),
        alias: searchMentionItems(allItems, '数据分析').map((item) => item.id),
        description: searchMentionItems(allItems, '指定用户').map((item) => item.id),
      }
    : undefined

  assert.deepEqual(actual, {
    prefix: ['send_message'],
    separators: ['send_message'],
    server: ['search_message', 'send_message'],
    alias: ['data-analysis'],
    description: ['notice'],
  })
})

test('最近使用按最新选择去重并限制数量，失效资源不会回显', () => {
  const { mentionItemKey, pushMentionRecent, resolveMentionRecents } = picker
  const actual = typeof mentionItemKey === 'function'
    && typeof pushMentionRecent === 'function'
    && typeof resolveMentionRecents === 'function'
    ? (() => {
        const sendKey = mentionItemKey(sendMessage)
        const searchKey = mentionItemKey(searchMessage)
        const skillKey = mentionItemKey(dataSkill)
        let keys = pushMentionRecent([searchKey, sendKey], sendMessage, 3)
        keys = pushMentionRecent(keys, dataSkill, 2)
        return {
          keys,
          resolved: resolveMentionRecents([...keys, 'agent-tool::deleted'], allItems)
            .map((item) => item.id),
          distinctMcpKeys: sendKey !== mentionItemKey({
            ...sendMessage,
            raw: { serverId: 'other', serverName: '另一个 MCP' },
          }),
          expectedSearchKey: searchKey,
          expectedSkillKey: skillKey,
        }
      })()
    : undefined

  assert.deepEqual(actual, {
    keys: ['agent-skill::data-analysis', 'agent-mcp:feishu:send_message'],
    resolved: ['data-analysis', 'send_message'],
    distinctMcpKeys: true,
    expectedSearchKey: 'agent-mcp:feishu:search_message',
    expectedSkillKey: 'agent-skill::data-analysis',
  })
})

test('下拉菜单不渲染伪搜索框或清空按钮', async () => {
  const dropdownSource = await readFile(dropdownSourceUrl, 'utf8')

  assert.equal(dropdownSource.includes('mention-search-bar'), false)
  assert.equal(dropdownSource.includes('输入名称、描述或 MCP 服务'), false)
  assert.equal(dropdownSource.includes('清空搜索内容'), false)
})
