<script setup lang="ts">
import {
  CopyOutlined,
  DeleteOutlined,
  EditOutlined,
  EyeOutlined,
  FullscreenOutlined,
  FormOutlined,
} from '@ant-design/icons-vue'

defineProps<{
  open: boolean
  x: number
  y: number
}>()

defineEmits<{
  edit: []
  rename: []
  copy: []
  delete: []
  fit: []
  logs: []
  close: []
}>()
</script>

<template>
  <div v-if="open" class="node-context-menu" :style="{ left: `${x}px`, top: `${y}px` }" @click.stop>
    <button type="button" @click="$emit('edit')">
      <EditOutlined />
      <span>编辑配置</span>
    </button>
    <button type="button" @click="$emit('rename')">
      <FormOutlined />
      <span>重命名</span>
    </button>
    <button type="button" @click="$emit('copy')">
      <CopyOutlined />
      <span>复制节点</span>
    </button>
    <button type="button" @click="$emit('fit')">
      <FullscreenOutlined />
      <span>适配到此节点</span>
    </button>
    <button type="button" @click="$emit('logs')">
      <EyeOutlined />
      <span>查看执行日志</span>
    </button>
    <div class="menu-divider" />
    <button type="button" class="danger" @click="$emit('delete')">
      <DeleteOutlined />
      <span>删除节点</span>
    </button>
  </div>
</template>

<style scoped lang="scss">
.node-context-menu {
  position: fixed;
  z-index: 40;
  display: grid;
  width: 168px;
  padding: 6px;
  box-shadow: 0px 3px 10px rgba(0, 0, 0, 0.08);
  border-radius: 8px;
  background: #fff;
}

.node-context-menu button {
  display: grid;
  grid-template-columns: 18px minmax(0, 1fr);
  gap: 8px;
  align-items: center;
  width: 100%;
  padding: 8px 10px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: #262626;
  cursor: pointer;
  text-align: left;
}

.node-context-menu button:hover {
  background: #f5f5f5;
}

.node-context-menu button.danger {
  color: #ff4d4f;
}

.menu-divider {
  height: 1px;
  margin: 4px;
  background: #f0f0f0;
}
</style>
