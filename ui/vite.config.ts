import { fileURLToPath, URL } from 'node:url'
import { resolve } from 'node:path'
import fs from 'node:fs'
import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueJsx from '@vitejs/plugin-vue-jsx'
import vueSetupExtend from 'vite-plugin-vue-setup-extend'
import vueDevTools from 'vite-plugin-vue-devtools'
import basicSsl from '@vitejs/plugin-basic-ssl'
import zipPack, { Options as ZipPickOptions } from "vite-plugin-zip-pack"
import { AntDesignVueResolver } from 'unplugin-vue-components/resolvers';
import Components from 'unplugin-vue-components/vite';

/**
 * 根据构建目标确定入口页面
 *
 * @param target 构建目标：main | doc | all
 * @param rootDir 项目根目录
 */
function resolveInputs(target: string, rootDir: string): Record<string, string> {
  const input: Record<string, string> = {}
  if (target === 'main' || target === 'all') {
    input.main = resolve(rootDir, 'index.html')
  }
  if (target === 'doc' || target === 'all') {
    input.doc = resolve(rootDir, 'doc.html')
  }
  return input
}

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const rootDir = fileURLToPath(new URL('.', import.meta.url))
  const target = env.VITE_APP_TARGET || 'all'

  // 根据构建目标设置输出目录和基础路径
  const outDir = target === 'doc' ? 'dist-doc' : (target === 'main' ? 'dist-main' : 'dist')
  const contextPath = env.VITE_APP_CONTEXT_PATH || ''
  const base = target === 'doc' ? `${contextPath}/doc/` : (contextPath ? `${contextPath}/` : '/')

  // HTTPS 开发模式（npm run dev:https）：优先使用 mkcert 证书（npm run cert:dev 生成，
  // SAN 含局域网 IP，iPhone 信任其根 CA 后无警告；iOS 对 basic-ssl 快速自签会直接拒连），
  // 证书文件不存在时回退 basic-ssl（Android 手动信任可用）
  const useHttps = env.VITE_DEV_HTTPS === 'true'
  const devKeyFile = resolve(rootDir, '.cert/dev-key.pem')
  const devCertFile = resolve(rootDir, '.cert/dev-cert.pem')
  const hasMkcert = useHttps && fs.existsSync(devKeyFile) && fs.existsSync(devCertFile)

  return {
    base,
    // 压缩dist配置
    plugins: [
      // getUserMedia 仅在 HTTPS/localhost 下可用，手机经局域网 IP 访问测语音输入必须走 HTTPS
      ...(useHttps && !hasMkcert ? [basicSsl()] : []),
      vue(),
      vueJsx(),
      vueSetupExtend(),
      // vueDevTools(),
      zipPack({
        inDir: outDir,
        outFileName: `${outDir}.zip`,
      } as ZipPickOptions),
      Components({
        resolvers: [
          AntDesignVueResolver({
            importStyle: false, // css in js
          }),
        ],
      })
    ],
    resolve: {
      // 别名
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url))
      },
    },
    optimizeDeps: {
      include: [
        '@codemirror/state',
        '@codemirror/view',
        '@codemirror/commands',
        '@codemirror/language',
        '@codemirror/autocomplete',
        '@codemirror/search',
        '@codemirror/theme-one-dark'
      ]
    },
    // 开发服务器配置
    server: {
      ...(hasMkcert
        ? { https: { key: fs.readFileSync(devKeyFile), cert: fs.readFileSync(devCertFile) } }
        : {}),
      // 指定服务器端口
      port: 3030,
      // 指定开发服务器绑定的主机地址，绑定到0.0.0.0，支持外部设备通过你的机器的 IP 地址访问本地服务器
      host: true,
      // 启动服务器后是否自动打开浏览器
      open: false,
      // 代理配置，避免跨域
      proxy: {
        '/api/runtime/': {
          ws: true,
          target: 'http://127.0.0.1:3061',
          changeOrigin: true,
          timeout: 0,
          rewrite: (p) => {
            return p.replace(/^\/api/, '')
          },
        },
        '/api/ws/': {
          ws: true,
          target: 'http://127.0.0.1:3064',
          changeOrigin: true,
          timeout: 0,
          rewrite: (p) => {
            return p.replace(/^\/api/, '')
          },
        },
        '/api': {
          ws: true,
          target: 'http://127.0.0.1:3060',
          changeOrigin: true,
          timeout: 0,
          rewrite: (p) => {
            return p.replace(/^\/api/, '')
          },
        }
      }
    },
    css: {
      preprocessorOptions: {
        scss: {

        },
      }
    },
    build: {
      outDir,
      rollupOptions: {
        input: resolveInputs(target, rootDir),
      },
    },
  }
})
