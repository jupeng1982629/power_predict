<template>
  <div class="power-page">
    <el-card shadow="never">
      <div slot="header">预测查询</div>
      <el-form inline>
        <el-form-item label="电站ID">
          <el-input v-model="plantId" placeholder="例如 plant-demo-001"></el-input>
        </el-form-item>
        <el-form-item label="预测日期">
          <el-date-picker v-model="forecastDate" type="date" placeholder="选择日期" value-format="yyyy-MM-dd"></el-date-picker>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadForecast">查询</el-button>
        </el-form-item>
      </el-form>
      <el-alert v-if="errorMessage" :title="errorMessage" type="warning" :closable="false" class="power-alert"></el-alert>
      <el-row :gutter="20">
        <el-col :span="12">
          <el-card shadow="never">
            <div slot="header">预测结果</div>
            <pre class="json-box">{{ forecastText }}</pre>
          </el-card>
        </el-col>
        <el-col :span="12">
          <el-card shadow="never">
            <div slot="header">实际结果</div>
            <pre class="json-box">{{ actualText }}</pre>
          </el-card>
        </el-col>
      </el-row>
    </el-card>
  </div>
</template>

<script>
import { getActuals, getForecasts } from '@/api/powerPredict'

export default {
  data () {
    return {
      plantId: 'plant-demo-001',
      forecastDate: '',
      forecastText: '暂无数据',
      actualText: '暂无数据',
      errorMessage: ''
    }
  },
  methods: {
    async loadForecast () {
      if (!this.plantId || !this.forecastDate) {
        this.$message.warning('请先填写电站ID和预测日期')
        return
      }

      this.errorMessage = ''
      try {
        const [forecastResponse, actualResponse] = await Promise.all([
          getForecasts(this.plantId, this.forecastDate),
          getActuals(this.plantId, this.forecastDate)
        ])
        this.forecastText = JSON.stringify(forecastResponse.data, null, 2)
        this.actualText = JSON.stringify(actualResponse.data, null, 2)
      } catch (error) {
        this.forecastText = '暂无数据'
        this.actualText = '暂无数据'
        this.errorMessage = error && error.response && error.response.data && error.response.data.message
          ? error.response.data.message
          : '预测服务暂不可用'
      }
    }
  }
}
</script>

<style scoped>
.power-alert {
  margin-bottom: 16px;
}
.json-box {
  min-height: 240px;
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
