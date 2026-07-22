<template>
  <div class="mod-home power-home">
    <el-row :gutter="20">
      <el-col :span="8">
        <el-card shadow="hover">
          <div slot="header">当前用户</div>
          <div class="metric-value">{{ session.userName || '--' }}</div>
          <div class="metric-meta">认证模式：{{ session.authMode || '--' }}</div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover">
          <div slot="header">权限数量</div>
          <div class="metric-value">{{ permissionCount }}</div>
          <div class="metric-meta">租户：{{ session.tenantId || '--' }}</div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover">
          <div slot="header">电站数量</div>
          <div class="metric-value">{{ plantTotal }}</div>
          <div class="metric-meta">网关连通后自动刷新</div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" class="section-row">
      <el-col :span="12">
        <el-card shadow="never">
          <div slot="header">角色 / 权限</div>
          <el-tag v-for="item in session.roles || []" :key="item" size="small" class="tag-item">{{ item }}</el-tag>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never">
          <div slot="header">服务状态</div>
          <el-alert
            v-if="serviceMessage"
            :title="serviceMessage"
            :type="serviceHealthy ? 'success' : 'warning'"
            :closable="false">
          </el-alert>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" class="section-row">
      <div slot="header">最近电站</div>
      <el-table :data="plants" stripe>
        <el-table-column prop="plantId" label="电站 ID" min-width="160"></el-table-column>
        <el-table-column prop="plantName" label="电站名称" min-width="180"></el-table-column>
        <el-table-column label="容量(kW)" min-width="120">
          <template slot-scope="scope">{{ formatKw(scope.row.capacityMw) }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" min-width="100">
          <template slot-scope="scope">
            <el-tag :type="scope.row.status === 'active' ? 'success' : 'info'">{{ scope.row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="updatedAt" label="更新时间" min-width="200"></el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script>
import { getPlants, getSession } from '@/api/powerPredict'

export default {
  data () {
    return {
      session: {},
      plants: [],
      plantTotal: 0,
      serviceHealthy: false,
      serviceMessage: ''
    }
  },
  computed: {
    permissionCount () {
      return Array.isArray(this.session.permissions) ? this.session.permissions.length : 0
    }
  },
  created () {
    this.loadData()
  },
  methods: {
    async loadData () {
      await this.loadSession()
      await this.loadPlants()
    },
    async loadSession () {
      try {
        const { data } = await getSession()
        this.session = data && data.data ? data.data : {}
        this.serviceHealthy = true
        this.serviceMessage = '认证网关已连通'
      } catch (error) {
        this.session = {}
        this.serviceHealthy = false
        this.serviceMessage = '认证网关访问失败'
      }
    },
    async loadPlants () {
      try {
        const { data } = await getPlants({ pageNo: 1, pageSize: 5 })
        const payload = data && data.data ? data.data : {}
        this.plants = Array.isArray(payload.items) ? payload.items : []
        this.plantTotal = payload.total || 0
      } catch (error) {
        this.plants = []
        this.plantTotal = 0
        if (!this.serviceMessage) {
          this.serviceMessage = '电站服务暂不可用'
        }
      }
    },
    formatKw (capacityMw) {
      const value = Number(capacityMw || 0) * 1000
      return value.toFixed(2)
    }
  }
}
</script>

<style lang="scss">
.power-home {
  .metric-value {
    font-size: 30px;
    font-weight: 600;
    color: #3e8ef7;
    line-height: 1.2;
  }
  .metric-meta {
    margin-top: 10px;
    color: #909399;
  }
  .section-row {
    margin-top: 20px;
  }
  .tag-item {
    margin-right: 8px;
    margin-bottom: 8px;
  }
}
</style>

