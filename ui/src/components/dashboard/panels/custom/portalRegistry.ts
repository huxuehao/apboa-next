/**
 * portal 自定义组件注册表：编译期 glob 锁定扫描目录（路径隔离），
 * 提供组件加载器查找、下拉选项（含 portalMeta 元信息）与 props 声明自动识别。
 *
 * @author huxuehao
 */
import type { Component } from 'vue'
import type { PanelPropType, PortalMeta } from '@/types/dashboard'

interface PortalModule {
  default: Component & { portalMeta?: PortalMeta }
}

/** 编译期锁定 portal 目录，运行期无法注入其他路径 */
const modules = import.meta.glob('@/components/portal/**/*.vue') as Record<
  string,
  () => Promise<PortalModule>
>

const PREFIX = '/src/components/portal/'

/** glob 路径 -> 组件标识（相对路径去扩展名，如 DemoWelcome、sub/Foo） */
function toId(path: string): string {
  return path.slice(path.indexOf(PREFIX) + PREFIX.length).replace(/\.vue$/, '')
}

/** 标识 -> 加载器映射 */
const loaderMap = new Map<string, () => Promise<PortalModule>>(
  Object.entries(modules).map(([path, loader]) => [toId(path), loader]),
)

/** 按标识取组件加载器（不存在返回 undefined） */
export function getPortalLoader(id: string): (() => Promise<PortalModule>) | undefined {
  return loaderMap.get(id)
}

/** 下拉选项：组件标识 + 名称 + 描述 */
export interface PortalOption {
  value: string
  name: string
  description: string
}

let optionsCache: PortalOption[] | null = null

/**
 * 异步加载全部 portal 组件元信息生成下拉选项（模块级缓存）。
 * 仅设计器配置时调用；门户运行态只加载已绑定组件，保持按需分包。
 */
export async function loadPortalOptions(): Promise<PortalOption[]> {
  if (optionsCache) return optionsCache
  const entries = await Promise.all(
    [...loaderMap.entries()].map(async ([id, loader]) => {
      let meta: PortalMeta = {}
      try {
        meta = (await loader()).default?.portalMeta || {}
      } catch {
        // 组件加载失败不阻断列表，回退文件名
      }
      return { value: id, name: meta.name || id, description: meta.description || '' }
    }),
  )
  optionsCache = entries.sort((a, b) => a.value.localeCompare(b.value))
  return optionsCache
}

/** 自动识别出的 prop 声明（含默认值，已转为弹窗存储格式） */
export interface PortalPropDef {
  key: string
  type: PanelPropType
  defaultValue: unknown
}

/** 由渲染器注入、不应由用户配置的 prop */
const INJECTED_PROP_KEYS = ['panelContext']

const propDefsCache = new Map<string, PortalPropDef[]>()

/**
 * 读取组件编译产物的 props 选项，自动识别声明的 key/类型/默认值。
 * 类型映射为 props 弹窗支持的六类，无法识别的类型跳过。
 */
export async function loadPortalPropDefs(id: string): Promise<PortalPropDef[]> {
  const cached = propDefsCache.get(id)
  if (cached) return cached
  const loader = loaderMap.get(id)
  if (!loader) return []
  let defs: PortalPropDef[] = []
  try {
    const raw = (await loader()).default as Component & { props?: Record<string, unknown> }
    defs = extractPropDefs(raw.props || {})
  } catch {
    // 加载失败时回退空声明（弹窗仍可手动添加）
  }
  propDefsCache.set(id, defs)
  return defs
}

/** 构造器 -> 弹窗类型；未知构造器返回 null */
function ctorToType(ctor: unknown): PanelPropType | null {
  switch (ctor) {
    case String:
      return 'string'
    case Number:
      return 'number'
    case Boolean:
      return 'boolean'
    case Array:
      return 'array'
    case Date:
      return 'date'
    case Object:
      return 'object'
    default:
      return null
  }
}

/** 解析运行时 props 选项（兼容 构造器/构造器数组/{type,default} 三种形态） */
function extractPropDefs(propsOption: Record<string, unknown>): PortalPropDef[] {
  const defs: PortalPropDef[] = []
  for (const [key, decl] of Object.entries(propsOption)) {
    if (INJECTED_PROP_KEYS.includes(key)) continue
    let ctor: unknown = decl
    let rawDefault: unknown
    if (decl && typeof decl === 'object' && !Array.isArray(decl)) {
      ctor = (decl as { type?: unknown }).type
      rawDefault = (decl as { default?: unknown }).default
    }
    if (Array.isArray(ctor)) ctor = ctor.find((c) => ctorToType(c) !== null)
    const type = ctorToType(ctor)
    if (!type) continue
    defs.push({ key, type, defaultValue: toModalValue(type, rawDefault) })
  }
  return defs
}

/** 默认值转为弹窗存储格式（array/object 存 JSON 字符串，date 存 YYYY-MM-DD） */
function toModalValue(type: PanelPropType, rawDefault: unknown): unknown {
  // 对象/数组默认值在运行时为工厂函数，需调用取值
  let value = rawDefault
  if (typeof rawDefault === 'function' && type !== 'boolean') {
    try {
      value = (rawDefault as () => unknown)()
    } catch {
      value = undefined
    }
  }
  switch (type) {
    case 'string':
      return value === undefined || value === null ? '' : String(value)
    case 'number':
      return typeof value === 'number' ? value : 0
    case 'boolean':
      return value === true
    case 'date': {
      if (value instanceof Date && !Number.isNaN(value.getTime())) {
        return value.toISOString().slice(0, 10)
      }
      return typeof value === 'string' ? value : ''
    }
    case 'array':
      return value === undefined ? '[]' : JSON.stringify(value)
    case 'object':
      return value === undefined ? '{}' : JSON.stringify(value)
  }
}
