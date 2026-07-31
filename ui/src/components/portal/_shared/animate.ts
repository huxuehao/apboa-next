/**
 * portal 组件共享动画工具：数值缓动（一次性，rAF 驱动）。
 *
 * @author huxuehao
 */

/**
 * 从 from 缓动到 to（easeOutCubic），返回取消函数。
 */
export function animateNumber(
  from: number,
  to: number,
  duration: number,
  onUpdate: (v: number) => void,
): () => void {
  if (from === to || duration <= 0) {
    onUpdate(to)
    return () => {}
  }
  const start = performance.now()
  let raf = 0
  const tick = (now: number) => {
    const t = Math.min(1, (now - start) / duration)
    const eased = 1 - Math.pow(1 - t, 3)
    onUpdate(from + (to - from) * eased)
    if (t < 1) raf = requestAnimationFrame(tick)
  }
  raf = requestAnimationFrame(tick)
  return () => cancelAnimationFrame(raf)
}
