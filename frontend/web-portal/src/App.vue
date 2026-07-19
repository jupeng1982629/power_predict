<template>
  <main class="page-shell">
    <section class="hero-card">
      <div>
        <p class="eyebrow">Power Predict</p>
        <h1>单电站光伏功率预测 MVP</h1>
        <p class="subtitle">
          基于本地 FastAPI 推理服务、Java 网关与阶段 4 调试鉴权上下文，展示日前 24 小时 96 点预测、实际值与误差指标。
        </p>
      </div>
      <div class="status-chip">Stage 4 Auth + Gateway + FastAPI + Vue</div>
    </section>

    <section class="toolbar panel">
      <div class="toolbar-group">
        <label>
          <span class="label">电站</span>
          <input v-model="plantId" />
        </label>
        <label>
          <span class="label">预测日期</span>
          <input v-model="forecastDate" type="date" />
        </label>
      </div>
      <div class="toolbar-group">
        <label>
          <span class="label">调试用户</span>
          <input v-model="auth.userName" />
        </label>
        <label>
          <span class="label">租户</span>
          <input v-model="auth.tenantId" />
        </label>
      </div>
      <div class="toolbar-actions">
        <button type="button" class="refresh-button" @click="loadAll">刷新数据</button>
        <button type="button" class="secondary-button" :disabled="jobRunning" @click="triggerForecast">
          {{ jobRunning ? '预测中...' : '手动触发日前预测' }}
        </button>
      </div>
    </section>

    <section class="grid metrics-grid">
      <article class="panel accent">
        <span class="label">链路状态</span>
        <strong>{{ loading ? '加载中...' : '已连接' }}</strong>
        <p>{{ message }}</p>
      </article>

      <article class="panel">
        <span class="label">电站容量</span>
        <strong>{{ overview?.data?.plant?.capacityKw?.toFixed?.(0) ?? '-' }} kW</strong>
        <p>{{ overview?.data?.plant?.plant_name ?? '-' }}</p>
      </article>

      <article class="panel">
        <span class="label">最新模型</span>
        <strong>{{ forecastPayload?.data?.modelVersion ?? overview?.data?.latestEvaluation?.modelVersion ?? '-' }}</strong>
        <p>RMSE: {{ overview?.data?.latestEvaluation?.rmse ?? '-' }}</p>
      </article>

      <article class="panel">
        <span class="label">最新任务</span>
        <strong>{{ overview?.data?.latestJob?.status ?? '-' }}</strong>
        <p>{{ overview?.data?.latestJob?.forecast_date ?? '-' }}</p>
      </article>
    </section>

    <section class="split-grid">
      <article class="panel wide-panel">
        <div class="panel-header">
          <span class="label">阶段 4 会话上下文</span>
          <span class="pill">{{ session?.data?.authMode ?? 'local-debug-stage4' }}</span>
        </div>
        <div class="session-grid">
          <div>
            <span class="mini-label">用户</span>
            <strong>{{ session?.data?.userName ?? '-' }}</strong>
          </div>
          <div>
            <span class="mini-label">租户</span>
            <strong>{{ session?.data?.tenantId ?? '-' }}</strong>
          </div>
          <div>
            <span class="mini-label">角色</span>
            <strong>{{ (session?.data?.roles ?? []).join(', ') || '-' }}</strong>
          </div>
        </div>
      </article>

      <article class="panel wide-panel">
        <div class="panel-header">
          <span class="label">天气输入快照</span>
          <span class="pill">{{ overview?.data?.nextWeather?.target_time ?? '-' }}</span>
        </div>
        <div class="session-grid">
          <div>
            <span class="mini-label">GHI</span>
            <strong>{{ overview?.data?.nextWeather?.ghi ?? '-' }}</strong>
          </div>
          <div>
            <span class="mini-label">温度</span>
            <strong>{{ overview?.data?.nextWeather?.temperature ?? '-' }}</strong>
          </div>
          <div>
            <span class="mini-label">云量</span>
            <strong>{{ overview?.data?.nextWeather?.cloud_cover ?? '-' }}</strong>
          </div>
        </div>
      </article>
    </section>

    <section class="split-grid">
      <article class="panel wide-panel">
        <div class="panel-header">
          <span class="label">预测曲线与实际值对比</span>
          <span class="pill">96 点 / 15 分钟</span>
        </div>
        <table>
          <thead>
            <tr>
              <th>时间</th>
              <th>预测(kW)</th>
              <th>下界</th>
              <th>上界</th>
              <th>实际(kW)</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in mergedRows" :key="row.targetTime">
              <td>{{ row.displayTime }}</td>
              <td>{{ row.predPowerKw }}</td>
              <td>{{ row.lowerBoundKw }}</td>
              <td>{{ row.upperBoundKw }}</td>
              <td>{{ row.actualPowerKw }}</td>
            </tr>
          </tbody>
        </table>
      </article>

      <article class="panel wide-panel">
        <div class="panel-header">
          <span class="label">误差趋势</span>
          <span class="pill">最近评估</span>
        </div>
        <table>
          <thead>
            <tr>
              <th>日期</th>
              <th>模型版本</th>
              <th>RMSE</th>
              <th>MAE</th>
              <th>MAPE</th>
              <th>R2</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in evaluationRows" :key="`${item.forecastDate}-${item.modelVersion}`">
              <td>{{ item.forecastDate }}</td>
              <td>{{ item.modelVersion }}</td>
              <td>{{ item.rmse }}</td>
              <td>{{ item.mae }}</td>
              <td>{{ item.mape }}</td>
              <td>{{ item.r2 }}</td>
            </tr>
          </tbody>
        </table>
      </article>
    </section>

    <section class="panel wide-panel">
      <div class="panel-header">
        <span class="label">服务返回原始数据</span>
        <span class="pill">调试视图</span>
      </div>
      <pre>{{ prettyPayload }}</pre>
    </section>
  </main>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import {
  defaultAuthContext,
  fetchActuals,
  fetchEvaluations,
  fetchForecasts,
  fetchOverview,
  fetchSession,
  runDayAheadForecast,
} from './api/platform'

const auth = ref(defaultAuthContext())
const plantId = ref('plant-demo-001')
const forecastDate = ref(new Date().toISOString().slice(0, 10))
const session = ref(null)
const overview = ref(null)
const forecastPayload = ref(null)
const actualPayload = ref(null)
const evaluations = ref(null)
const loading = ref(true)
const jobRunning = ref(false)
const message = ref('正在加载预测平台数据。')

const mergedRows = computed(() => {
  const actualMap = new Map(
    (actualPayload.value?.data?.points ?? []).map((point) => [point.ts, point.activePowerKw])
  )
  return (forecastPayload.value?.data?.points ?? []).slice(0, 24).map((point) => ({
    targetTime: point.targetTime,
    displayTime: point.targetTime?.slice(11, 16) ?? '-',
    predPowerKw: point.predPowerKw?.toFixed?.(2) ?? point.predPowerKw ?? '-',
    lowerBoundKw: point.lowerBoundKw?.toFixed?.(2) ?? point.lowerBoundKw ?? '-',
    upperBoundKw: point.upperBoundKw?.toFixed?.(2) ?? point.upperBoundKw ?? '-',
    actualPowerKw: actualMap.get(point.targetTime)?.toFixed?.(2) ?? actualMap.get(point.targetTime) ?? '-',
  }))
})

const evaluationRows = computed(() => evaluations.value?.data?.items ?? [])

const prettyPayload = computed(() =>
  JSON.stringify(
    {
      session: session.value,
      overview: overview.value,
      forecasts: forecastPayload.value,
      actuals: actualPayload.value,
      evaluations: evaluations.value,
    },
    null,
    2
  )
)

async function loadAll() {
  loading.value = true
  message.value = '正在通过网关加载预测、实际、评估与鉴权会话数据。'
  try {
    const currentAuth = auth.value
    const [sessionResult, overviewResult, forecastResult, actualResult, evaluationResult] = await Promise.all([
      fetchSession(currentAuth),
      fetchOverview(plantId.value, currentAuth),
      fetchForecasts(plantId.value, forecastDate.value, currentAuth),
      fetchActuals(plantId.value, forecastDate.value, currentAuth),
      fetchEvaluations(plantId.value, currentAuth),
    ])
    session.value = sessionResult
    overview.value = overviewResult
    forecastPayload.value = forecastResult
    actualPayload.value = actualResult
    evaluations.value = evaluationResult
    message.value = '预测服务、网关和阶段 4 调试鉴权链路均已就绪。'
  } catch (error) {
    message.value = error instanceof Error ? error.message : String(error)
  } finally {
    loading.value = false
  }
}

async function triggerForecast() {
  jobRunning.value = true
  try {
    await runDayAheadForecast(plantId.value, forecastDate.value, auth.value)
    await loadAll()
  } catch (error) {
    message.value = error instanceof Error ? error.message : String(error)
  } finally {
    jobRunning.value = false
  }
}

onMounted(loadAll)
</script>