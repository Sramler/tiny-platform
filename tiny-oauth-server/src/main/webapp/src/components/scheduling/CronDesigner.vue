<template>
  <div class="cron-designer">
    <a-form layout="vertical" :label-col="{ span: 24 }">
      <a-form-item label="快捷选择">
        <a-select
          v-model:value="presetKey"
          placeholder="选择预设或自定义"
          allow-clear
          style="width: 100%"
          @change="onPresetChange"
        >
          <a-select-option value="every_minute">每分钟</a-select-option>
          <a-select-option value="every_5_minutes">每 5 分钟</a-select-option>
          <a-select-option value="every_30_minutes">每 30 分钟</a-select-option>
          <a-select-option value="every_hour">每小时（整点）</a-select-option>
          <a-select-option value="daily">每天指定时间</a-select-option>
          <a-select-option value="weekly">每周指定星期与时间</a-select-option>
          <a-select-option value="weekdays_9am">工作日每天 09:00</a-select-option>
          <a-select-option value="monthly">每月指定日期与时间</a-select-option>
          <a-select-option value="custom">自定义表达式</a-select-option>
        </a-select>
      </a-form-item>

      <template v-if="presetKey === 'daily'">
        <a-form-item label="每天执行时间">
          <a-space>
            <a-select v-model:value="dailyHour" style="width: 80px" @change="emitDaily">
              <a-select-option v-for="h in 24" :key="h - 1" :value="h - 1">
                {{ String(h - 1).padStart(2, '0') }} 时
              </a-select-option>
            </a-select>
            <a-select v-model:value="dailyMinute" style="width: 80px" @change="emitDaily">
              <a-select-option v-for="m in 60" :key="m - 1" :value="m - 1">
                {{ String(m - 1).padStart(2, '0') }} 分
              </a-select-option>
            </a-select>
          </a-space>
        </a-form-item>
      </template>
      <template v-else-if="presetKey === 'weekly'">
        <a-form-item label="星期">
          <a-select v-model:value="weeklyDow" style="width: 120px" @change="emitWeekly">
            <a-select-option :value="1">周日</a-select-option>
            <a-select-option :value="2">周一</a-select-option>
            <a-select-option :value="3">周二</a-select-option>
            <a-select-option :value="4">周三</a-select-option>
            <a-select-option :value="5">周四</a-select-option>
            <a-select-option :value="6">周五</a-select-option>
            <a-select-option :value="7">周六</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="时间">
          <a-space>
            <a-select v-model:value="weeklyHour" style="width: 80px" @change="emitWeekly">
              <a-select-option v-for="h in 24" :key="h - 1" :value="h - 1">
                {{ String(h - 1).padStart(2, '0') }} 时
              </a-select-option>
            </a-select>
            <a-select v-model:value="weeklyMinute" style="width: 80px" @change="emitWeekly">
              <a-select-option v-for="m in 60" :key="m - 1" :value="m - 1">
                {{ String(m - 1).padStart(2, '0') }} 分
              </a-select-option>
            </a-select>
          </a-space>
        </a-form-item>
      </template>
      <template v-else-if="presetKey === 'monthly'">
        <a-form-item label="每月">
          <a-space>
            <a-select
              v-model:value="monthlyDayType"
              style="width: 120px"
              @change="onMonthlyDayTypeChange"
            >
              <a-select-option value="day">指定日期</a-select-option>
              <a-select-option value="last">最后一天</a-select-option>
            </a-select>
            <a-select
              v-if="monthlyDayType === 'day'"
              v-model:value="monthlyDay"
              style="width: 90px"
              @change="emitMonthly"
            >
              <a-select-option v-for="d in 31" :key="d" :value="d">{{ d }} 日</a-select-option>
            </a-select>
          </a-space>
        </a-form-item>
        <a-form-item label="时间">
          <a-space>
            <a-select v-model:value="monthlyHour" style="width: 80px" @change="emitMonthly">
              <a-select-option v-for="h in 24" :key="h - 1" :value="h - 1">
                {{ String(h - 1).padStart(2, '0') }} 时
              </a-select-option>
            </a-select>
            <a-select v-model:value="monthlyMinute" style="width: 80px" @change="emitMonthly">
              <a-select-option v-for="m in 60" :key="m - 1" :value="m - 1">
                {{ String(m - 1).padStart(2, '0') }} 分
              </a-select-option>
            </a-select>
          </a-space>
        </a-form-item>
      </template>
      <template v-else-if="presetKey === 'custom'">
        <a-form-item
          label="Cron 表达式（6 位：秒 分 时 日 月 周）"
          :validate-status="customValidationError ? 'error' : undefined"
          :help="customValidationError"
        >
          <a-input
            v-model:value="customExpression"
            placeholder="如 0 0 2 * * ? 表示每天 02:00"
            allow-clear
            @change="emitCustom"
          />
        </a-form-item>
      </template>

      <a-form-item v-if="currentExpression" label="当前表达式">
        <a-typography-text code copyable>{{ currentExpression }}</a-typography-text>
      </a-form-item>

      <a-form-item v-if="humanReadable" label="说明">
        <a-typography-text type="secondary">{{ humanReadable }}</a-typography-text>
      </a-form-item>

      <a-form-item v-if="nextRunText" label="下次执行约">
        <a-typography-text>{{ nextRunText }}</a-typography-text>
      </a-form-item>
    </a-form>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, computed } from 'vue'

/**
 * Quartz 6 位 Cron：秒 分 时 日 月 周（周 1=周日, 2=周一, ..., 7=周六）
 */
const PRESETS: Record<string, string> = {
  every_minute: '0 * * * * ?',
  every_5_minutes: '0 0/5 * * * ?',
  every_30_minutes: '0 0/30 * * * ?',
  every_hour: '0 0 * * * ?',
  weekdays_9am: '0 0 9 ? * 2-6',
}

const DOW_NAMES: Record<number, string> = {
  1: '周日',
  2: '周一',
  3: '周二',
  4: '周三',
  5: '周四',
  6: '周五',
  7: '周六',
}

const props = defineProps<{
  modelValue: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const presetKey = ref<string>('')
const dailyHour = ref(2)
const dailyMinute = ref(0)
const weeklyDow = ref(2)
const weeklyHour = ref(9)
const weeklyMinute = ref(0)
const monthlyDayType = ref<'day' | 'last'>('day')
const monthlyDay = ref(1)
const monthlyHour = ref(0)
const monthlyMinute = ref(0)
const customExpression = ref('')

const currentExpression = computed(() => {
  if (presetKey.value === 'every_minute') return PRESETS.every_minute
  if (presetKey.value === 'every_5_minutes') return PRESETS.every_5_minutes
  if (presetKey.value === 'every_30_minutes') return PRESETS.every_30_minutes
  if (presetKey.value === 'every_hour') return PRESETS.every_hour
  if (presetKey.value === 'weekdays_9am') return PRESETS.weekdays_9am
  if (presetKey.value === 'daily')
    return `0 ${dailyMinute.value} ${dailyHour.value} * * ?`
  if (presetKey.value === 'weekly')
    return `0 ${weeklyMinute.value} ${weeklyHour.value} ? * ${weeklyDow.value}`
  if (presetKey.value === 'monthly') {
    const dom = monthlyDayType.value === 'last' ? 'L' : monthlyDay.value
    return `0 ${monthlyMinute.value} ${monthlyHour.value} ${dom} * ?`
  }
  if (presetKey.value === 'custom') return customExpression.value.trim() || ''
  return ''
})

/** 1. 中文说明 */
const humanReadable = computed(() => {
  const expr = currentExpression.value
  if (!expr) return ''
  if (presetKey.value === 'every_minute') return '每分钟执行'
  if (presetKey.value === 'every_5_minutes') return '每 5 分钟执行'
  if (presetKey.value === 'every_30_minutes') return '每 30 分钟执行'
  if (presetKey.value === 'every_hour') return '每小时整点执行'
  if (presetKey.value === 'weekdays_9am') return '每个工作日（周一至周五）09:00 执行'
  if (presetKey.value === 'daily')
    return `每天 ${String(dailyHour.value).padStart(2, '0')}:${String(dailyMinute.value).padStart(2, '0')} 执行`
  if (presetKey.value === 'weekly')
    return `每周${DOW_NAMES[weeklyDow.value] ?? ''} ${String(weeklyHour.value).padStart(2, '0')}:${String(weeklyMinute.value).padStart(2, '0')} 执行`
  if (presetKey.value === 'monthly') {
    const dayDesc = monthlyDayType.value === 'last' ? '最后一天' : `${monthlyDay.value} 日`
    return `每月${dayDesc} ${String(monthlyHour.value).padStart(2, '0')}:${String(monthlyMinute.value).padStart(2, '0')} 执行`
  }
  if (presetKey.value === 'custom') return humanReadableFromExpr(expr)
  return ''
})

function humanReadableFromExpr(expr: string): string {
  const parts = expr.trim().split(/\s+/)
  if (parts.length < 6) return '自定义表达式'
  const [sec = '', min = '', hour = '', dom = '', month = '', dow = ''] = parts
  if (sec === '0' && min === '0' && hour !== '*' && dom === '*' && month === '*' && (dow === '?' || dow === '*'))
    return `每天 ${hour.padStart(2, '0')}:00 执行`
  if (sec === '0' && min === '*' && hour === '*' && dom === '*' && month === '*' && dow === '?')
    return '每分钟执行'
  if (sec === '0' && min === '0' && hour === '*' && dom === '*' && month === '*' && dow === '?')
    return '每小时整点执行'
  return '自定义表达式'
}

/** 3. 自定义校验 */
function validateQuartzCron(expr: string): string | null {
  const v = expr.trim()
  if (!v) return null
  const parts = v.split(/\s+/)
  if (parts.length !== 6) return '需要 6 段：秒 分 时 日 月 周，用空格分隔'
  const [sec = '', min = '', hour = '', dom = '', month = '', dow = ''] = parts
  const numOrStar = (s: string, max: number) =>
    s === '*' || s === '?' || (/^\d+$/.test(s) && parseInt(s, 10) >= 0 && parseInt(s, 10) <= max)
  if (!numOrStar(sec, 59)) return '秒：0-59 或 *'
  if (!numOrStar(min, 59)) return '分：0-59 或 *'
  if (!numOrStar(hour, 23)) return '时：0-23 或 *'
  if (dom !== 'L' && dom !== '*' && dom !== '?' && (!/^\d+$/.test(dom) || parseInt(dom, 10) < 1 || parseInt(dom, 10) > 31))
    return '日：1-31 或 * 或 ? 或 L'
  if (!numOrStar(month, 12)) return '月：1-12 或 *'
  if (!numOrStar(dow, 7)) return '周：1-7(日-六) 或 * 或 ?'
  return null
}

const customValidationError = computed(() => {
  if (presetKey.value !== 'custom') return ''
  const v = customExpression.value.trim()
  if (!v) return ''
  return validateQuartzCron(v) || ''
})

/** 6. 下次执行预览（仅对当前生成的预设/表达式做简单估算） */
const nextRunText = computed(() => {
  const expr = currentExpression.value
  if (!expr || customValidationError.value) return ''
  return getNextRunText(expr)
})

function getNextRunText(expr: string): string {
  const parts = expr.trim().split(/\s+/)
  if (parts.length < 6) return ''

  const now = new Date()
  const [sec = '', min = '', hour = '', dom = '', month = '', dow = ''] = parts

  if (sec === '0' && min === '*' && hour === '*' && dom === '*' && month === '*' && dow === '?') {
    const next = new Date(now)
    next.setSeconds(0)
    next.setMinutes(now.getMinutes() + 1)
    return formatNext(next)
  }
  if (expr === PRESETS.every_5_minutes) {
    const m = now.getMinutes()
    const nextMin = m + (5 - (m % 5))
    const next = new Date(now)
    next.setMinutes(nextMin)
    next.setSeconds(0)
    if (nextMin >= 60) next.setHours(next.getHours() + 1)
    return formatNext(next)
  }
  if (expr === PRESETS.every_30_minutes) {
    const m = now.getMinutes()
    const nextMin = m < 30 ? 30 : 0
    const next = new Date(now)
    next.setMinutes(nextMin)
    next.setSeconds(0)
    if (nextMin === 0) next.setHours(next.getHours() + 1)
    return formatNext(next)
  }
  if (sec === '0' && min === '0' && hour === '*' && dom === '*' && month === '*' && dow === '?') {
    const next = new Date(now)
    next.setMinutes(0)
    next.setSeconds(0)
    next.setHours(now.getHours() + 1)
    return formatNext(next)
  }
  if (presetKey.value === 'daily' || (sec === '0' && hour !== '*' && dom === '*' && month === '*' && (dow === '?' || dow === '*'))) {
    const h = parseInt(hour, 10)
    const m = parseInt(min, 10)
    const next = new Date(now)
    next.setHours(h, m, 0, 0)
    if (next <= now) next.setDate(next.getDate() + 1)
    return formatNext(next)
  }
  if (presetKey.value === 'weekly' || (dom === '?' && dow && dow !== '*' && dow !== '?')) {
    const targetDow = parseInt(dow, 10)
    const h = parseInt(hour, 10)
    const minVal = parseInt(min, 10)
    const next = new Date(now)
    next.setHours(h, minVal, 0, 0)
    const nowDow = next.getDay() === 0 ? 7 : next.getDay()
    let days = targetDow - nowDow
    if (days <= 0) days += 7
    next.setDate(next.getDate() + days)
    if (next <= now) next.setDate(next.getDate() + 7)
    return formatNext(next)
  }
  if (presetKey.value === 'monthly') {
    const h = monthlyHour.value
    const m = monthlyMinute.value
    if (monthlyDayType.value === 'last') {
      const thisMonthLast = new Date(now.getFullYear(), now.getMonth() + 1, 0)
      thisMonthLast.setHours(h, m, 0, 0)
      if (thisMonthLast > now) return formatNext(thisMonthLast)
      const nextMonthLast = new Date(now.getFullYear(), now.getMonth() + 2, 0)
      nextMonthLast.setHours(h, m, 0, 0)
      return formatNext(nextMonthLast)
    }
    const next = new Date(now)
    next.setHours(h, m, 0, 0)
    next.setDate(monthlyDay.value)
    if (next <= now || next.getDate() !== monthlyDay.value) {
      next.setMonth(next.getMonth() + 1)
      next.setDate(monthlyDay.value)
    }
    return formatNext(next)
  }
  if (expr === PRESETS.weekdays_9am) {
    const next = new Date(now)
    next.setHours(9, 0, 0, 0)
    next.setMinutes(0, 0, 0)
    const wd = next.getDay()
    if (wd === 0) next.setDate(next.getDate() + 1)
    else if (wd === 6) next.setDate(next.getDate() + 2)
    else if (next <= now) next.setDate(next.getDate() + 1)
    while (next.getDay() === 0 || next.getDay() === 6) next.setDate(next.getDate() + 1)
    return formatNext(next)
  }
  return '自定义表达式不显示下次执行时间'
}

function formatNext(d: Date): string {
  const y = d.getFullYear()
  const M = String(d.getMonth() + 1).padStart(2, '0')
  const D = String(d.getDate()).padStart(2, '0')
  const h = String(d.getHours()).padStart(2, '0')
  const m = String(d.getMinutes()).padStart(2, '0')
  return `${y}-${M}-${D} ${h}:${m}`
}

function onPresetChange() {
  if (
    presetKey.value === 'every_minute' ||
    presetKey.value === 'every_hour' ||
    presetKey.value === 'every_5_minutes' ||
    presetKey.value === 'every_30_minutes' ||
    presetKey.value === 'weekdays_9am'
  ) {
    emit('update:modelValue', PRESETS[presetKey.value] ?? '')
    return
  }
  if (presetKey.value === 'daily') emitDaily()
  else if (presetKey.value === 'weekly') emitWeekly()
  else if (presetKey.value === 'monthly') emitMonthly()
  else if (presetKey.value === 'custom') emitCustom()
}

function onMonthlyDayTypeChange() {
  emitMonthly()
}

function emitDaily() {
  emit('update:modelValue', `0 ${dailyMinute.value} ${dailyHour.value} * * ?`)
}

function emitWeekly() {
  emit('update:modelValue', `0 ${weeklyMinute.value} ${weeklyHour.value} ? * ${weeklyDow.value}`)
}

function emitMonthly() {
  const dom = monthlyDayType.value === 'last' ? 'L' : monthlyDay.value
  emit('update:modelValue', `0 ${monthlyMinute.value} ${monthlyHour.value} ${dom} * ?`)
}

function emitCustom() {
  const v = customExpression.value.trim()
  if (v && !validateQuartzCron(v)) emit('update:modelValue', v)
}

function parseInitial(value: string) {
  const v = (value || '').trim()
  if (!v) {
    presetKey.value = ''
    return
  }
  if (v === PRESETS.every_minute) {
    presetKey.value = 'every_minute'
    return
  }
  if (v === PRESETS.every_5_minutes) {
    presetKey.value = 'every_5_minutes'
    return
  }
  if (v === PRESETS.every_30_minutes) {
    presetKey.value = 'every_30_minutes'
    return
  }
  if (v === PRESETS.every_hour) {
    presetKey.value = 'every_hour'
    return
  }
  if (v === PRESETS.weekdays_9am) {
    presetKey.value = 'weekdays_9am'
    return
  }
  const parts = v.split(/\s+/)
  if (parts.length >= 6) {
  const [sec = '', min = '', hour = '', dom = '', month = '', dow = ''] = parts
    if (sec === '0' && hour !== '*' && dom === '*' && month === '*' && (dow === '?' || dow === '*')) {
      presetKey.value = 'daily'
      dailyHour.value = parseInt(hour, 10) || 0
      dailyMinute.value = parseInt(min, 10) || 0
      return
    }
    if (sec === '0' && dom === '?' && month === '*' && dow && dow !== '*' && dow !== '?') {
      presetKey.value = 'weekly'
      weeklyHour.value = parseInt(hour, 10) || 0
      weeklyMinute.value = parseInt(min, 10) || 0
      weeklyDow.value = parseInt(dow, 10) || 2
      return
    }
    if (sec === '0' && dow === '?') {
      const domVal = dom
      if (domVal === 'L') {
        presetKey.value = 'monthly'
        monthlyDayType.value = 'last'
        monthlyHour.value = parseInt(hour, 10) || 0
        monthlyMinute.value = parseInt(min, 10) || 0
        return
      }
      if (month === '*') {
        presetKey.value = 'monthly'
        monthlyDayType.value = 'day'
        monthlyHour.value = parseInt(hour, 10) || 0
        monthlyMinute.value = parseInt(min, 10) || 0
        monthlyDay.value = parseInt(domVal, 10) || 1
        return
      }
    }
  }
  presetKey.value = 'custom'
  customExpression.value = v
}

watch(
  () => props.modelValue,
  (val) => parseInitial(val || ''),
  { immediate: true }
)

watch(customExpression, () => {
  if (presetKey.value === 'custom' && !validateQuartzCron(customExpression.value.trim()))
    emit('update:modelValue', customExpression.value.trim())
})
</script>

<style scoped>
.cron-designer {
  min-width: 320px;
}
</style>
