<template>
  <div class="power-page">
    <el-card shadow="never">
      <div slot="header" class="page-header">
        <span>电站列表</span>
        <el-button type="primary" size="small" @click="openCreate">新增电站</el-button>
      </div>
      <el-form inline>
        <el-form-item>
          <el-input v-model="filters.keyword" placeholder="按电站 ID / 名称 / 时区搜索" clearable @keyup.enter.native="handleSearch"></el-input>
        </el-form-item>
        <el-form-item>
          <el-select v-model="filters.status" placeholder="状态" clearable>
            <el-option label="运行中" value="active"></el-option>
            <el-option label="停用" value="inactive"></el-option>
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-input v-model="filters.timezone" placeholder="按时区筛选" clearable @keyup.enter.native="handleSearch"></el-input>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>
      <el-alert v-if="errorMessage" :title="errorMessage" type="warning" :closable="false" class="power-alert"></el-alert>
      <el-table :data="plants" stripe border @sort-change="handleSortChange">
        <el-table-column prop="plantId" label="电站 ID" min-width="180" sortable="custom"></el-table-column>
        <el-table-column prop="plantName" label="电站名称" min-width="180" sortable="custom"></el-table-column>
        <el-table-column prop="capacityMw" label="容量(MW)" min-width="120" sortable="custom">
          <template slot-scope="scope">{{ formatNumber(scope.row.capacityMw, 4) }}</template>
        </el-table-column>
        <el-table-column label="容量(kW)" min-width="120">
          <template slot-scope="scope">{{ formatNumber(toCapacityKw(scope.row.capacityMw), 2) }}</template>
        </el-table-column>
        <el-table-column prop="latitude" label="纬度" min-width="110" sortable="custom"></el-table-column>
        <el-table-column prop="longitude" label="经度" min-width="110" sortable="custom"></el-table-column>
        <el-table-column prop="elevationM" label="海拔(m)" min-width="110" sortable="custom"></el-table-column>
        <el-table-column prop="tiltAngle" label="倾角" min-width="100" sortable="custom"></el-table-column>
        <el-table-column prop="azimuthAngle" label="方位角" min-width="100" sortable="custom"></el-table-column>
        <el-table-column prop="timezone" label="时区" min-width="140" sortable="custom"></el-table-column>
        <el-table-column prop="status" label="状态" min-width="100" sortable="custom">
          <template slot-scope="scope">
            <el-tag :type="scope.row.status === 'active' ? 'success' : 'info'">{{ scope.row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" min-width="180" sortable="custom"></el-table-column>
        <el-table-column prop="updatedAt" label="更新时间" min-width="180" sortable="custom"></el-table-column>
        <el-table-column label="操作" fixed="right" min-width="210">
          <template slot-scope="scope">
            <el-button type="text" size="small" @click="openDetail(scope.row)">查看</el-button>
            <el-button type="text" size="small" @click="openEdit(scope.row)">编辑</el-button>
            <el-button type="text" size="small" class="danger-text" @click="removePlant(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager-wrap">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next, jumper"
          :current-page="pagination.pageNo"
          :page-sizes="[10, 20, 50, 100]"
          :page-size="pagination.pageSize"
          :total="pagination.total"
          @current-change="handlePageChange"
          @size-change="handleSizeChange">
        </el-pagination>
      </div>
    </el-card>

    <el-dialog :title="dialogTitle" :visible.sync="dialogVisible" width="760px" @close="resetDialog">
      <el-form ref="plantForm" :model="form" :rules="formRules" label-width="110px" :disabled="dialogMode === 'detail'">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="电站 ID" prop="plantId">
              <el-input v-model="form.plantId" :disabled="dialogMode !== 'create'"></el-input>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="电站名称" prop="plantName">
              <el-input v-model="form.plantName"></el-input>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="容量(MW)" prop="capacityMw">
              <el-input-number v-model="form.capacityMw" :min="0" :precision="4" :step="0.1" controls-position="right" class="full-width"></el-input-number>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="时区" prop="timezone">
              <el-input v-model="form.timezone"></el-input>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="纬度" prop="latitude">
              <el-input-number v-model="form.latitude" :min="-90" :max="90" :precision="6" :step="0.000001" controls-position="right" class="full-width"></el-input-number>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="经度" prop="longitude">
              <el-input-number v-model="form.longitude" :min="-180" :max="180" :precision="6" :step="0.000001" controls-position="right" class="full-width"></el-input-number>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="海拔(m)">
              <el-input-number v-model="form.elevationM" :precision="2" :step="0.1" controls-position="right" class="full-width"></el-input-number>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态" prop="status">
              <el-select v-model="form.status" class="full-width">
                <el-option label="运行中" value="active"></el-option>
                <el-option label="停用" value="inactive"></el-option>
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="倾角">
              <el-input-number v-model="form.tiltAngle" :precision="2" :step="0.1" controls-position="right" class="full-width"></el-input-number>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="方位角">
              <el-input-number v-model="form.azimuthAngle" :precision="2" :step="0.1" controls-position="right" class="full-width"></el-input-number>
            </el-form-item>
          </el-col>
          <el-col v-if="dialogMode === 'detail'" :span="12">
            <el-form-item label="创建时间">
              <el-input :value="form.createdAt" disabled></el-input>
            </el-form-item>
          </el-col>
          <el-col v-if="dialogMode === 'detail'" :span="12">
            <el-form-item label="更新时间">
              <el-input :value="form.updatedAt" disabled></el-input>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button @click="dialogVisible = false">{{ dialogMode === 'detail' ? '关闭' : '取消' }}</el-button>
        <el-button v-if="dialogMode !== 'detail'" type="primary" :loading="submitLoading" @click="submitForm">保存</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { createPlant, deletePlant, getPlant, getPlants, updatePlant } from '@/api/powerPredict'

function createEmptyForm () {
  return {
    plantId: '',
    plantName: '',
    capacityMw: 0,
    latitude: 0,
    longitude: 0,
    elevationM: null,
    tiltAngle: null,
    azimuthAngle: null,
    timezone: 'Asia/Shanghai',
    status: 'active',
    createdAt: '',
    updatedAt: ''
  }
}

export default {
  data () {
    return {
      filters: {
        keyword: '',
        status: '',
        timezone: ''
      },
      pagination: {
        pageNo: 1,
        pageSize: 10,
        total: 0
      },
      sorter: {
        sortBy: 'updatedAt',
        sortDir: 'desc'
      },
      plants: [],
      errorMessage: '',
      dialogVisible: false,
      dialogMode: 'create',
      submitLoading: false,
      form: createEmptyForm(),
      formRules: {
        plantId: [{ required: true, message: '请输入电站 ID', trigger: 'blur' }],
        plantName: [{ required: true, message: '请输入电站名称', trigger: 'blur' }],
        capacityMw: [{ required: true, message: '请输入容量(MW)', trigger: 'change' }],
        latitude: [{ required: true, message: '请输入纬度', trigger: 'change' }],
        longitude: [{ required: true, message: '请输入经度', trigger: 'change' }],
        timezone: [{ required: true, message: '请输入时区', trigger: 'blur' }],
        status: [{ required: true, message: '请选择状态', trigger: 'change' }]
      }
    }
  },
  computed: {
    dialogTitle () {
      if (this.dialogMode === 'create') {
        return '新增电站'
      }
      if (this.dialogMode === 'edit') {
        return '编辑电站'
      }
      return '查看电站'
    }
  },
  created () {
    this.loadPlants()
  },
  methods: {
    toCapacityKw (value) {
      return (Number(value) || 0) * 1000
    },
    formatNumber (value, precision) {
      if (value === null || value === undefined || value === '') {
        return '--'
      }
      return Number(value).toFixed(precision)
    },
    buildListParams () {
      return {
        pageNo: this.pagination.pageNo,
        pageSize: this.pagination.pageSize,
        keyword: this.filters.keyword || undefined,
        status: this.filters.status || undefined,
        timezone: this.filters.timezone || undefined,
        sortBy: this.sorter.sortBy || undefined,
        sortDir: this.sorter.sortDir || undefined
      }
    },
    async loadPlants () {
      this.errorMessage = ''
      try {
        const { data } = await getPlants(this.buildListParams())
        const payload = data && data.data ? data.data : {}
        this.plants = Array.isArray(payload.items) ? payload.items : []
        this.pagination.total = payload.total || 0
        this.pagination.pageNo = payload.pageNo || this.pagination.pageNo
        this.pagination.pageSize = payload.pageSize || this.pagination.pageSize
      } catch (error) {
        this.plants = []
        this.pagination.total = 0
        this.errorMessage = error && error.response && error.response.data && error.response.data.message
          ? error.response.data.message
          : '电站服务暂不可用'
      }
    },
    handleSearch () {
      this.pagination.pageNo = 1
      this.loadPlants()
    },
    resetFilters () {
      this.filters = {
        keyword: '',
        status: '',
        timezone: ''
      }
      this.sorter = {
        sortBy: 'updatedAt',
        sortDir: 'desc'
      }
      this.pagination.pageNo = 1
      this.loadPlants()
    },
    handlePageChange (pageNo) {
      this.pagination.pageNo = pageNo
      this.loadPlants()
    },
    handleSizeChange (pageSize) {
      this.pagination.pageSize = pageSize
      this.pagination.pageNo = 1
      this.loadPlants()
    },
    handleSortChange ({ prop, order }) {
      this.sorter.sortBy = prop || 'updatedAt'
      this.sorter.sortDir = order === 'ascending' ? 'asc' : 'desc'
      if (!order) {
        this.sorter.sortBy = 'updatedAt'
        this.sorter.sortDir = 'desc'
      }
      this.loadPlants()
    },
    resetDialog () {
      this.submitLoading = false
      this.form = createEmptyForm()
      if (this.$refs.plantForm) {
        this.$refs.plantForm.resetFields()
      }
    },
    openCreate () {
      this.dialogMode = 'create'
      this.form = createEmptyForm()
      this.dialogVisible = true
    },
    async openEdit (row) {
      await this.openDialogWithRow('edit', row.plantId)
    },
    async openDetail (row) {
      await this.openDialogWithRow('detail', row.plantId)
    },
    async openDialogWithRow (mode, plantId) {
      this.dialogMode = mode
      this.dialogVisible = true
      try {
        const { data } = await getPlant(plantId)
        const payload = data && data.data ? data.data : {}
        this.form = Object.assign(createEmptyForm(), payload)
      } catch (error) {
        this.dialogVisible = false
        this.$message.error(error && error.response && error.response.data && error.response.data.message
          ? error.response.data.message
          : '加载电站详情失败')
      }
    },
    buildPayload () {
      return {
        plantId: this.form.plantId,
        plantName: this.form.plantName,
        capacityMw: this.form.capacityMw,
        latitude: this.form.latitude,
        longitude: this.form.longitude,
        elevationM: this.form.elevationM,
        tiltAngle: this.form.tiltAngle,
        azimuthAngle: this.form.azimuthAngle,
        timezone: this.form.timezone,
        status: this.form.status
      }
    },
    submitForm () {
      this.$refs.plantForm.validate(async valid => {
        if (!valid) {
          return
        }
        this.submitLoading = true
        try {
          const payload = this.buildPayload()
          if (this.dialogMode === 'create') {
            await createPlant(payload)
          } else {
            await updatePlant(this.form.plantId, payload)
          }
          this.dialogVisible = false
          this.$message.success('保存成功')
          this.loadPlants()
        } catch (error) {
          this.$message.error(error && error.response && error.response.data && error.response.data.message
            ? error.response.data.message
            : '保存失败')
        } finally {
          this.submitLoading = false
        }
      })
    },
    removePlant (row) {
      this.$confirm(`确认将电站 ${row.plantId} 标记为停用吗？`, '提示', {
        type: 'warning'
      }).then(async () => {
        try {
          await deletePlant(row.plantId)
          this.$message.success('删除成功')
          if (this.plants.length === 1 && this.pagination.pageNo > 1) {
            this.pagination.pageNo -= 1
          }
          this.loadPlants()
        } catch (error) {
          this.$message.error(error && error.response && error.response.data && error.response.data.message
            ? error.response.data.message
            : '删除失败')
        }
      }).catch(() => {})
    }
  }
}
</script>

<style scoped>
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.power-alert {
  margin-bottom: 16px;
}

.pager-wrap {
  margin-top: 16px;
  text-align: right;
}

.full-width {
  width: 100%;
}

.danger-text {
  color: #f56c6c;
}
</style>
