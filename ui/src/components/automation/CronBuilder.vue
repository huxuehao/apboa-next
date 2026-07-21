/**
 * Cron 表达式构建器组件
 * 支持可视化选择和直接输入，提供常用预设
 *
 * @component
 */
<script setup lang="ts">
import { ref, computed, watch } from 'vue'

const modelValue = defineModel<string>({ default: '0 0 * * * ?' })

const cronParts = ref({
  second: '0',
  minute: '0',
  hour: '*',
  day: '*',
  month: '*',
  week: '?'
})

/** 秒级选项 */
const secondOptions = [
  ...Array.from({ length: 60 }, (_, i) => ({ value: String(i), label: `${i}秒` })),
  { value: '*/1', label: '每秒' },
  { value: '*/5', label: '每5秒' },
  { value: '*/10', label: '每10秒' },
  { value: '*/15', label: '每15秒' },
  { value: '*/20', label: '每20秒' },
  { value: '*/30', label: '每30秒' },
]

/** 分级选项 */
const minuteOptions = [
  { value: '*', label: '每分' },
  ...Array.from({ length: 60 }, (_, i) => ({ value: String(i), label: `${i}分` })),
  { value: '*/1', label: '每1分' },
  { value: '*/5', label: '每5分' },
  { value: '*/10', label: '每10分' },
  { value: '*/15', label: '每15分' },
  { value: '*/20', label: '每20分' },
  { value: '*/30', label: '每30分' },
]

/** 时级选项 */
const hourOptions = [
  { value: '*', label: '每小时' },
  ...Array.from({ length: 24 }, (_, i) => ({ value: String(i), label: `${i}点` })),
]

/** 日级选项 */
const dayOptions = [
  { value: '*', label: '每天' },
  { value: '?', label: '不指定' },
  ...Array.from({ length: 31 }, (_, i) => ({ value: String(i + 1), label: `${i + 1}日` })),
  { value: 'L', label: '最后一天' },
]

/** 月级选项 */
const monthOptions = [
  { value: '*', label: '每月' },
  ...Array.from({ length: 12 }, (_, i) => ({ value: String(i + 1), label: `${i + 1}月` })),
]

/** 周级选项 */
const weekOptions = [
  { value: '?', label: '不指定' },
  { value: 'MON', label: '周一' },
  { value: 'TUE', label: '周二' },
  { value: 'WED', label: '周三' },
  { value: 'THU', label: '周四' },
  { value: 'FRI', label: '周五' },
  { value: 'SAT', label: '周六' },
  { value: 'SUN', label: '周日' },
  { value: 'MON-FRI', label: '周一至周五' },
  { value: 'SUN,SAT', label: '周六、周日' },
]

/**
 * Cron表达式描述
 */
const cronDescription = computed(() => {
  const cron = modelValue.value
  if (!cron) return ''
  const parts = cron.split(' ')
  if (parts.length !== 6 && parts.length !== 7) return '格式不正确'

  const [second, minute, hour, day, month, week] = parts as [string, string, string, string, string, string]
  if (second === '0' && minute === '0' && hour === '0' && day === '*' && month === '*' && week === '?') return '每天零点执行'
  if (second === '0' && minute === '0' && hour === '*' && day === '*' && month === '*' && week === '?') return '每小时执行'
  if (second === '0' && minute === '*/5' && hour === '*' && day === '*' && month === '*' && week === '?') return '每5分钟执行'

  const desc: string[] = []
  if (week === 'MON-FRI') desc.push('工作日')
  else if (week === 'SUN,SAT') desc.push('周末')
  if (hour !== '*') desc.push(`${hour}点`)
  if (minute && minute !== '0' && minute !== '*') {
    desc.push(minute.startsWith('*/') ? `每${minute.replace('*/', '')}分钟` : `${minute}分`)
  }
  return desc.length > 0 ? desc.join('，') : '自定义执行策略'
})

function updateCronFromParts() {
  const { second, minute, hour, day, month, week } = cronParts.value
  let actualDay = day
  if (week !== '?' && day !== '?') actualDay = '?'
  modelValue.value = `${second} ${minute} ${hour} ${actualDay} ${month} ${week}`
}

function parseCronToParts() {
  const parts = modelValue.value?.split(' ')
  if (parts && parts.length >= 6) {
    cronParts.value = {
      second: parts[0] || '0',
      minute: parts[1] || '0',
      hour: parts[2] || '*',
      day: parts[3] || '*',
      month: parts[4] || '*',
      week: parts[5] || '?'
    }
  }
}

function applyPreset(cron: string) {
  modelValue.value = cron
  parseCronToParts()
}

// 初始化时解析
parseCronToParts()

watch(modelValue, () => {
  parseCronToParts()
})
</script>

<template>
  <div class="cron-builder">
    <div class="cron-generator">
      <div class="cron-row">
        <span class="cron-label">秒</span>
        <ASelect v-model:value="cronParts.second" @change="updateCronFromParts">
          <ASelectOption v-for="opt in secondOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</ASelectOption>
        </ASelect>
      </div>
      <div class="cron-row">
        <span class="cron-label">分</span>
        <ASelect v-model:value="cronParts.minute" @change="updateCronFromParts">
          <ASelectOption v-for="opt in minuteOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</ASelectOption>
        </ASelect>
      </div>
      <div class="cron-row">
        <span class="cron-label">时</span>
        <ASelect v-model:value="cronParts.hour" @change="updateCronFromParts">
          <ASelectOption v-for="opt in hourOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</ASelectOption>
        </ASelect>
      </div>
      <div class="cron-row">
        <span class="cron-label">日</span>
        <ASelect v-model:value="cronParts.day" @change="updateCronFromParts">
          <ASelectOption v-for="opt in dayOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</ASelectOption>
        </ASelect>
      </div>
      <div class="cron-row">
        <span class="cron-label">月</span>
        <ASelect v-model:value="cronParts.month" @change="updateCronFromParts">
          <ASelectOption v-for="opt in monthOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</ASelectOption>
        </ASelect>
      </div>
      <div class="cron-row">
        <span class="cron-label">周</span>
        <ASelect v-model:value="cronParts.week" @change="updateCronFromParts">
          <ASelectOption v-for="opt in weekOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</ASelectOption>
        </ASelect>
      </div>
    </div>

    <div class="cron-expression">
      <span>Quartz Cron</span>
      <AInput v-model:value="modelValue" placeholder="0 0 * * * ?" @change="parseCronToParts" />
      <div class="cron-desc">{{ cronDescription }}</div>
    </div>

    <div class="cron-presets">
      <div class="preset-title">快捷预设</div>
      <div class="preset-group">
        <span class="preset-label">分钟</span>
        <ASpace wrap :size="[8, 4]">
          <AButton type="link" size="small" @click="applyPreset('0 */1 * * * ?')">每1分钟</AButton>
          <AButton type="link" size="small" @click="applyPreset('0 */5 * * * ?')">每5分钟</AButton>
          <AButton type="link" size="small" @click="applyPreset('0 */10 * * * ?')">每10分钟</AButton>
          <AButton type="link" size="small" @click="applyPreset('0 */15 * * * ?')">每15分钟</AButton>
          <AButton type="link" size="small" @click="applyPreset('0 */20 * * * ?')">每20分钟</AButton>
          <AButton type="link" size="small" @click="applyPreset('0 */30 * * * ?')">每30分钟</AButton>
        </ASpace>
      </div>
      <div class="preset-group">
        <span class="preset-label">小时</span>
        <ASpace wrap :size="[8, 4]">
          <AButton type="link" size="small" @click="applyPreset('0 0 * * * ?')">每1小时</AButton>
          <AButton type="link" size="small" @click="applyPreset('0 0 */2 * * ?')">每2小时</AButton>
          <AButton type="link" size="small" @click="applyPreset('0 0 */3 * * ?')">每3小时</AButton>
          <AButton type="link" size="small" @click="applyPreset('0 0 */4 * * ?')">每4小时</AButton>
          <AButton type="link" size="small" @click="applyPreset('0 0 */6 * * ?')">每6小时</AButton>
          <AButton type="link" size="small" @click="applyPreset('0 0 */8 * * ?')">每8小时</AButton>
          <AButton type="link" size="small" @click="applyPreset('0 0 */12 * * ?')">每12小时</AButton>
        </ASpace>
      </div>
      <div class="preset-group">
        <span class="preset-label">每天</span>
        <ASpace wrap :size="[8, 4]">
          <AButton type="link" size="small" @click="applyPreset('0 0 0 * * ?')">凌晨0点</AButton>
          <AButton type="link" size="small" @click="applyPreset('0 0 2 * * ?')">凌晨2点</AButton>
          <AButton type="link" size="small" @click="applyPreset('0 0 4 * * ?')">凌晨4点</AButton>
          <AButton type="link" size="small" @click="applyPreset('0 0 6 * * ?')">早6点</AButton>
          <AButton type="link" size="small" @click="applyPreset('0 0 7 * * ?')">早7点</AButton>
          <AButton type="link" size="small" @click="applyPreset('0 0 8 * * ?')">早8点</AButton>
          <AButton type="link" size="small" @click="applyPreset('0 0 9 * * ?')">早9点</AButton>
          <AButton type="link" size="small" @click="applyPreset('0 0 10 * * ?')">早10点</AButton>
          <AButton type="link" size="small" @click="applyPreset('0 0 12 * * ?')">中午12点</AButton>
          <AButton type="link" size="small" @click="applyPreset('0 0 14 * * ?')">下午2点</AButton>
          <AButton type="link" size="small" @click="applyPreset('0 0 16 * * ?')">下午4点</AButton>
          <AButton type="link" size="small" @click="applyPreset('0 0 18 * * ?')">晚6点</AButton>
          <AButton type="link" size="small" @click="applyPreset('0 0 20 * * ?')">晚8点</AButton>
          <AButton type="link" size="small" @click="applyPreset('0 0 22 * * ?')">晚10点</AButton>
          <AButton type="link" size="small" @click="applyPreset('0 0 23 * * ?')">晚11点</AButton>
        </ASpace>
      </div>
      <div class="preset-group">
        <span class="preset-label">工作日</span>
        <ASpace wrap :size="[8, 4]">
          <AButton type="link" size="small" @click="applyPreset('0 0 7 ? * MON-FRI')">早7点</AButton>
          <AButton type="link" size="small" @click="applyPreset('0 0 8 ? * MON-FRI')">早8点</AButton>
          <AButton type="link" size="small" @click="applyPreset('0 0 9 ? * MON-FRI')">早9点</AButton>
          <AButton type="link" size="small" @click="applyPreset('0 30 9 ? * MON-FRI')">早9:30</AButton>
          <AButton type="link" size="small" @click="applyPreset('0 0 10 ? * MON-FRI')">早10点</AButton>
          <AButton type="link" size="small" @click="applyPreset('0 0 12 ? * MON-FRI')">午12点</AButton>
          <AButton type="link" size="small" @click="applyPreset('0 0 14 ? * MON-FRI')">下午2点</AButton>
          <AButton type="link" size="small" @click="applyPreset('0 0 18 ? * MON-FRI')">晚6点</AButton>
          <AButton type="link" size="small" @click="applyPreset('0 0 20 ? * MON-FRI')">晚8点</AButton>
          <AButton type="link" size="small" @click="applyPreset('0 0 9,18 ? * MON-FRI')">早9点/晚6点</AButton>
        </ASpace>
      </div>
      <div class="preset-group">
        <span class="preset-label">周末</span>
        <ASpace wrap :size="[8, 4]">
          <AButton type="link" size="small" @click="applyPreset('0 0 8 ? * SUN,SAT')">早8点</AButton>
          <AButton type="link" size="small" @click="applyPreset('0 0 9 ? * SUN,SAT')">早9点</AButton>
          <AButton type="link" size="small" @click="applyPreset('0 0 10 ? * SUN,SAT')">早10点</AButton>
          <AButton type="link" size="small" @click="applyPreset('0 0 14 ? * SUN,SAT')">下午2点</AButton>
          <AButton type="link" size="small" @click="applyPreset('0 0 20 ? * SUN,SAT')">晚8点</AButton>
        </ASpace>
      </div>
      <div class="preset-group">
        <span class="preset-label">指定日</span>
        <ASpace wrap :size="[8, 4]">
          <AButton type="link" size="small" @click="applyPreset('0 0 9 ? * MON')">周一早9点</AButton>
          <AButton type="link" size="small" @click="applyPreset('0 0 9 ? * TUE')">周二早9点</AButton>
          <AButton type="link" size="small" @click="applyPreset('0 0 9 ? * WED')">周三早9点</AButton>
          <AButton type="link" size="small" @click="applyPreset('0 0 9 ? * THU')">周四早9点</AButton>
          <AButton type="link" size="small" @click="applyPreset('0 0 18 ? * FRI')">周五晚6点</AButton>
        </ASpace>
      </div>
      <div class="preset-group">
        <span class="preset-label">月度</span>
        <ASpace wrap :size="[8, 4]">
          <AButton type="link" size="small" @click="applyPreset('0 0 0 1 * ?')">每月1号零点</AButton>
          <AButton type="link" size="small" @click="applyPreset('0 0 0 15 * ?')">每月15号零点</AButton>
          <AButton type="link" size="small" @click="applyPreset('0 0 0 L * ?')">每月最后一天</AButton>
          <AButton type="link" size="small" @click="applyPreset('0 0 0 1 1,4,7,10 ?')">每季度首日</AButton>
        </ASpace>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.cron-builder {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.cron-generator {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  padding: 16px 0;
  background-color: var(--color-bg-container);
  border-radius: var(--border-radius-base);
  border: 1px solid var(--color-border);
}

.cron-row {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.cron-label {
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
}

.cron-expression {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.cron-desc {
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
}

.cron-presets {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px 0;
  background-color: var(--color-bg-container);
  border-radius: var(--border-radius-base);
  border: 1px solid var(--color-border);
}

.preset-title {
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text);
}

.preset-group {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.preset-label {
  width: 48px;
  flex-shrink: 0;
  padding-top: 1px;
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
  white-space: nowrap;
}
</style>
