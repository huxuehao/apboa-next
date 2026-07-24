<script setup lang="ts">
import { RouterView } from 'vue-router'
import zhCN from 'ant-design-vue/es/locale/zh_CN'
import {
  useWebSocketSender
} from '@/websocket/composables/useWebSocketEvent';
import { useWebSocket } from "@/websocket/useWebSocket.ts";

void (() => {
  const { initWS } = useWebSocket();
  useWebSocketSender();
  initWS();
})();
</script>

<template>
  <AConfigProvider
    :locale="zhCN"
    :theme="{
      token: {
        fontFamily: `'Alibaba PuHuiTi 3.0', -apple-system, BlinkMacSystemFont, 'Segoe UI',
          'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', sans-serif`,
        fontSize: 16,
      }
     }">
    <RouterView />
  </AConfigProvider>
</template>

<style scoped></style>
