import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { test } from 'node:test'
import ts from 'typescript'

const sourceUrl = new URL('../src/utils/chat/ttsReplayDecision.ts', import.meta.url)

async function loadDecisionModule() {
  let source = ''
  try {
    source = await readFile(sourceUrl, 'utf8')
  } catch {
    // RED 阶段：实现文件尚不存在，让行为断言以明确的期望值失败。
  }
  const javascript = ts.transpileModule(source, {
    compilerOptions: {
      module: ts.ModuleKind.ESNext,
      target: ts.ScriptTarget.ES2022,
    },
  }).outputText
  return import(`data:text/javascript;base64,${Buffer.from(javascript).toString('base64')}`)
}

const { shouldReplayLatestTts } = await loadDecisionModule()

test('仅在没有活动播报且回复已结束时重播最后一条 AI 回复', () => {
  const cases = [
    {
      name: '回复与音频均已结束时重播最后一条 AI 回复',
      input: { speaking: false, running: false, hasLatestAssistant: true },
      expected: true,
    },
    {
      name: '静音音频仍在播放时续播，不从头重播',
      input: { speaking: true, running: false, hasLatestAssistant: true },
      expected: false,
    },
    {
      name: 'AI 仍在生成时交给服务端补喂，不发起手动重播',
      input: { speaking: false, running: true, hasLatestAssistant: true },
      expected: false,
    },
    {
      name: '当前没有 AI 回复时只开启自动朗读',
      input: { speaking: false, running: false, hasLatestAssistant: false },
      expected: false,
    },
  ]

  for (const { name, input, expected } of cases) {
    const actual = typeof shouldReplayLatestTts === 'function'
      ? shouldReplayLatestTts(input)
      : undefined
    assert.equal(actual, expected, name)
  }
})
