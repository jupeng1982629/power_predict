<template>
  <div class="container">
    <div class="content__block">
      <div v-if="pendingRefresh.visible" class="operation-notice" :class="`operation-notice--${pendingRefresh.type}`">
        <span class="operation-notice__text">{{ pendingRefresh.message }}</span>
      </div>
      <div class="table">
        <zc-tree-selector
          class="terminal-tree-selector"
          :strict-concentrator-selection="true"
          :selectable-tree-types="[2]"
          :first-selectable-tree-types="[2]"
          :hide-children-tree-types="[2]"
          placeholder="请选择终端"
          @selectTree="handleTerminalTreeSelect"
        ></zc-tree-selector>

        <div class="table__block table__block--single">
          <div class="table__block__item">
            <div class="table__block__item__header table__block__item__header--terminal">
              <span>终端档案</span>
              <span class="terminal-address-badge">{{ currentTerminalAddressText }}</span>
              <el-tooltip content="开始校对" placement="top">
                <el-button size="small" type="primary" icon="el-icon-document-checked" circle @click="handleOpenCompare"></el-button>
              </el-tooltip>
              <el-tooltip content="批量删除" placement="top">
                <el-button v-auth="'Commands.I.MeterDelArchives'" size="small" type="primary" icon="el-icon-delete" circle @click="handleBatchDelete"></el-button>
              </el-tooltip>
              <el-tooltip content="召测(刷新)" placement="top">
                <el-button v-auth="'Commands.I.MeterCalledArchives'" size="small" type="primary" icon="el-icon-refresh" circle @click="handleCallTest"></el-button>
              </el-tooltip>
            </div>
            <page-table class="table__right" ref="jzqPageTable" @selection-change="handleSelectionChange">
              <template v-for="(item, index) in tableCols" :key="index">
                <table-column :item="item"></table-column>
              </template>
              <el-table-column v-if="columnPermissionRight" label="操作" width="70" align="center" fixed="right">
                <template #default="scope">
                  <div style="height: 40px">
                    <el-button v-if="Object.keys(scope.row).length" type="text" icon="el-icon-delete"
                      class="zc-button--danger" @click="handleDelete(scope.row)">删除</el-button>
                  </div>
                </template>
              </el-table-column>
            </page-table>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import bus from "@/utils/bus";
import request from "@/utils/request";
import zcTreeSelector from "@/components/zcTreeSelector.vue";
import PageTable from "./components/PageTable.vue";
import TableColumn from "./components/TableColumn.vue";
import {
  issueConcentratorArchives,
  issueDelMeterArchives,
} from "@/api/Command";

export default {
  name: "FileMaintainList",
  autoRefreshDelaySeconds: 10,
  data() {
    return {
      columnPermissionRight: this.$columnPermission(["Commands.I.MeterDelArchives"]),
      query: {},
      jzqData: [],
      selectionList: [],
      tableCols: [
        { prop: "tagNo", label: "配置序号", width: 70 },
        { prop: "address", label: "通讯地址", width: 110 },
        { prop: "bnsTerminalAddress", label: "终端地址", width: 106 },
        { prop: "collectorAddress", label: "采集器地址", width: 104 },
        { prop: "assetNumber", label: "资产号", width: 180 },
        { prop: "userNo", label: "户号", width: 86 },
        { prop: "userName", label: "用户名", width: 96 },
        { prop: "portText", label: "通信接口", width: 90 },
        { prop: "portBaudText", label: "波特率", width: 92 },
        { prop: "password", label: "通讯密码", width: 98 },
        { prop: "rateTheNumberOf", label: "费率个数", width: 90 },
        { prop: "connectionTypeText", label: "接线方式", width: 92 },
        { prop: "ratedVoltage", label: "额定电压", width: 92 },
        { prop: "ratedCurrent", label: "额定电流", width: 92 },
        { prop: "vtr", label: "电压互感器变比", width: 120 },
        { prop: "ctr", label: "电流互感器变比", width: 120 },
        { prop: "enabled", label: "启用状态", width: 88 },
        { prop: "syncStateText", label: "同步状态", width: 92 },
        { prop: "creationTime", label: "创建时间", width: 132 },
      ],
      isUnmounted: false,
      pendingRefresh: {
        visible: false,
        seconds: 0,
        action: "",
        message: "",
        type: "info",
      },
      pendingRefreshTimer: null,
      pendingRefreshCountdownTimer: null,
      pendingRefreshHideTimer: null,
      pendingRefreshToken: 0,
    };
  },
  computed: {
    currentTerminalAddressText() {
      return (this.query && this.query.displayName) || "未选择终端";
    },
  },
  components: {
    zcTreeSelector,
    PageTable,
    TableColumn,
  },
  created() {
    this.isUnmounted = false;
    this.onQueryArchive = (message) => {
      const { target, executState } = message || {};
      const { displayName: terminalAddress } = this.query || {};
      if (executState == 3 && target == terminalAddress) this.handleSearch();
    };
    bus.$on("queryJzqMeterArchives", this.onQueryArchive);
  },
  unmounted() {
    this.isUnmounted = true;
    this.clearPendingRefresh();
    if (this.onQueryArchive) {
      bus.$off("queryJzqMeterArchives", this.onQueryArchive);
      this.onQueryArchive = null;
    }
  },
  methods: {
    clearPendingRefresh() {
      if (this.pendingRefreshTimer) {
        clearTimeout(this.pendingRefreshTimer);
        this.pendingRefreshTimer = null;
      }
      if (this.pendingRefreshCountdownTimer) {
        clearInterval(this.pendingRefreshCountdownTimer);
        this.pendingRefreshCountdownTimer = null;
      }
      if (this.pendingRefreshHideTimer) {
        clearTimeout(this.pendingRefreshHideTimer);
        this.pendingRefreshHideTimer = null;
      }
      this.pendingRefresh = {
        visible: false,
        seconds: 0,
        action: "",
        message: "",
        type: "info",
      };
    },
    updatePendingRefreshMessage() {
      if (!this.pendingRefresh.visible) return;
      const seconds = Math.max(0, Number(this.pendingRefresh.seconds) || 0);
      const actionText = this.pendingRefresh.action || "操作";
      this.pendingRefresh.message = `${actionText}指令已提交，预计 ${seconds} 秒后自动刷新档案结果，请稍候。`;
    },
    scheduleAutoRefresh(actionText) {
      this.clearPendingRefresh();
      const refreshToken = ++this.pendingRefreshToken;
      this.pendingRefresh = {
        visible: true,
        seconds: this.$options.autoRefreshDelaySeconds,
        action: actionText,
        message: "",
        type: "warning",
      };
      this.updatePendingRefreshMessage();
      this.$message({
        type: "success",
        message: `${actionText}指令已提交，${this.$options.autoRefreshDelaySeconds}秒后自动刷新档案结果。`,
        duration: 3000,
      });

      this.pendingRefreshCountdownTimer = setInterval(() => {
        if (this.isUnmounted) {
          this.clearPendingRefresh();
          return;
        }
        if (this.pendingRefresh.seconds <= 1) {
          this.pendingRefresh.seconds = 0;
          this.updatePendingRefreshMessage();
          clearInterval(this.pendingRefreshCountdownTimer);
          this.pendingRefreshCountdownTimer = null;
          return;
        }
        this.pendingRefresh.seconds -= 1;
        this.updatePendingRefreshMessage();
      }, 1000);

      this.pendingRefreshTimer = setTimeout(() => {
        this.pendingRefreshTimer = null;
        if (this.isUnmounted) {
          this.clearPendingRefresh();
          return;
        }
        this.pendingRefresh.type = "info";
        this.pendingRefresh.message = `${actionText}结果刷新中...`;
        this.handleSearch();
        this.pendingRefresh = {
          visible: true,
          seconds: 0,
          action: actionText,
          message: `${actionText}结果已刷新。`,
          type: "success",
        };
        this.pendingRefreshHideTimer = setTimeout(() => {
          this.pendingRefreshHideTimer = null;
          if (!this.isUnmounted && refreshToken === this.pendingRefreshToken) {
            this.clearPendingRefresh();
          }
        }, 2000);
      }, this.$options.autoRefreshDelaySeconds * 1000);
    },
    getTagNoSortValue(value) {
      if (value === null || value === undefined || value === "") return null;
      const text = String(value).trim();
      if (!text) return null;
      if (/^\d+$/.test(text)) return Number(text);
      const numeric = Number(text);
      return Number.isFinite(numeric) ? numeric : text;
    },
    sortByTagNo(list = []) {
      return [...list].sort((a, b) => {
        const aValue = this.getTagNoSortValue(a && a.tagNo);
        const bValue = this.getTagNoSortValue(b && b.tagNo);

        if (aValue === null && bValue === null) return 0;
        if (aValue === null) return 1;
        if (bValue === null) return -1;

        if (typeof aValue === "number" && typeof bValue === "number") {
          return aValue - bValue;
        }

        return String(aValue).localeCompare(String(bValue), "zh-Hans-CN", {
          numeric: true,
          sensitivity: "base",
        });
      });
    },
    getTerminalInfoIdFromTreeNode(data) {
      const candidates = [
        data && data.terminalInfoId,
        data && data.bnsTerminalInfoId,
        data && data.terminalId,
        data && data.bnsTerminalId,
        data && data.value,
        data && data.id,
      ];
      for (let index = 0; index < candidates.length; index++) {
        const value = candidates[index];
        if (value === null || value === undefined || value === "") {
          continue;
        }
        return value;
      }
      return null;
    },
    handleTerminalTreeSelect(node) {
      const data = (node && node.data) || {};
      if (Number(data.treeType) !== 2) {
        return;
      }
      const terminalInfoId = this.getTerminalInfoIdFromTreeNode(data);
      const terminalAddress = `${data.code || data.displayName || ""}`.trim();
      if (!terminalInfoId || !terminalAddress) {
        return;
      }

      this.query = {
        value: terminalInfoId,
        displayName: terminalAddress,
        data: {
          address: terminalAddress,
        },
      };
      this.handleSearch();
    },
    isEmpty() {
      if (!(this.query && this.query.value) || !(this.query && this.query.displayName)) {
        this.$message.error("请先选择终端");
        return true;
      }
      return false;
    },
    handleSelectionChange(e) {
      this.selectionList = e;
    },
    handleSearch() {
      if (this.isEmpty()) return;
      this.jzqFile();
    },
    handleOpenCompare() {
      if (this.isEmpty()) return;
      this.$message.warning("终端档案校对功能已下线");
    },
    handleCallTest() {
      if (this.isEmpty()) return;
      const { displayName: terminalAddress } = this.query || {};
      this.$confirm(`您确定要召测终端"${terminalAddress}"档案吗?`, "提示", {
        type: "warning",
      })
        .then(() => issueConcentratorArchives({ terminalAddress }))
        .then(() => {
          this.scheduleAutoRefresh("终端档案召测");
        })
        .catch((err) => {
          if (err !== "cancel") this.$message.error(err || "召测失败");
        });
    },
    deleteAxios(tagNosList, tips) {
      const { displayName: terminalAddress } = this.query || {};
      const data = {
        terminalAddress,
        parameter: { tagNos: tagNosList },
      };
      this.$confirm(`${tips}`, "提示", {
        type: "warning",
      })
        .then(() => issueDelMeterArchives(data))
        .then(() => {
          this.scheduleAutoRefresh("档案删除");
        })
        .catch((err) => {
          if (err !== "cancel") this.$message.error(err || "删除失败");
        });
    },
    handleDelete(row) {
      const { address, tagNo } = row || {};
      const tagNosList = [tagNo];
      const tips = `您确定要将地址为"${address}"的光伏逆变器档案删除吗?`;
      this.deleteAxios(tagNosList, tips);
    },
    handleBatchDelete() {
      if (!this.selectionList.length) {
        this.$message.error("至少选择1项进行删除");
        return;
      }
      const tagNosList = this.selectionList.map((item) => item.tagNo);
      const tips = "您确定要将所选光伏逆变器档案从终端删除吗?";
      this.deleteAxios(tagNosList, tips);
    },
    jzqFile() {
      const { value: TerminalInfoId } = this.query;
      request({
        url: "/api/app/iot-data-record/iot-am-meters/" + TerminalInfoId,
        method: "GET",
      })
        .then((res = []) => {
          this.jzqData = this.sortByTagNo(res);
          this.$refs.jzqPageTable.tableData = this.jzqData;
        })
        .catch((err) => {
          this.jzqData = [];
          this.$refs.jzqPageTable.tableData = [];
          this.$message.error(err || "终端档案加载失败");
        });
    },
  },
};
</script>

<style lang="scss" scoped>
.container {
  width: 100%;
  height: 100%;
  padding: 10px;
  box-sizing: border-box;
  display: flex;
}

.content__block {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
}

.operation-notice {
  display: flex;
  align-items: center;
  min-height: 36px;
  margin-bottom: 10px;
  padding: 8px 12px;
  border-radius: 8px;
  border: 1px solid transparent;
  font-size: 13px;
  line-height: 20px;

  &__text {
    color: inherit;
  }

  &--warning {
    color: #8a5a00;
    background: #fff7e6;
    border-color: #f3d19e;
  }

  &--info {
    color: #1f5ea8;
    background: #edf5ff;
    border-color: #b3d8ff;
  }

  &--success {
    color: #1f7a3d;
    background: #edf9f0;
    border-color: #b3e2c0;
  }
}

.table {
  width: 100%;
  flex: 1;
  background-color: var(--panel-soft-bg);
  padding: 10px;
  box-sizing: border-box;
  display: flex;
  gap: 10px;

  .terminal-tree-selector {
    width: 300px;
    flex-shrink: 0;
  }

  &__block {
    width: 100%;
    height: 100%;
    display: flex;
    min-width: 0;

    &--single {
      .table__block__item {
        margin-right: 0;
      }
    }

    &__item {
      position: relative;
      flex: 1;
      margin-right: 10px;
      min-width: 0;

      &__header {
        min-height: 32px;
        font-weight: bold;
        margin-bottom: 0;
        display: flex;
        justify-content: flex-start;
        align-items: center;
        transform: translateY(-3px);
        gap: 8px;

        > span {
          color: var(--archive-filemaintain-label-color, #4bf3f9);
        }
      }

      &__header--terminal {
        :deep(.el-button + .el-button) {
          margin-left: 0;
        }
      }
    }
  }
}

.terminal-address-badge {
  display: inline-flex;
  align-items: center;
  padding: 0 8px;
  height: 24px;
  border-radius: 12px;
  color: var(--table-text-color);
  background: var(--table-row-even-bg);
  font-size: 12px;
  font-weight: 500;
  max-width: 220px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
