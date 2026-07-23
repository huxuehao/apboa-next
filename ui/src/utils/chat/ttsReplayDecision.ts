export interface TtsReplayDecisionInput {
  speaking: boolean
  running: boolean
  hasLatestAssistant: boolean
}

/**
 * 开启自动朗读时，只有已经错过当前回复音频的场景才需要从头补播。
 * 活跃音频由播放器解除静音续播；生成中的回复由服务端 run buffer 补喂。
 */
export function shouldReplayLatestTts({
  speaking,
  running,
  hasLatestAssistant,
}: TtsReplayDecisionInput): boolean {
  return !speaking && !running && hasLatestAssistant
}
