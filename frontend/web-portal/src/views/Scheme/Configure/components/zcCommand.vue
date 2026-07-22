<template>
  <el-dialog v-dialogDrag :title="dialogTitle" v-model:visible="visible" width="560px" custom-class="scheme-command-dialog">
    <div class="dialog-tip">请选择需要下发的终端，点击“确认下发”后将向所选终端批量下发当前任务。</div>
    <div class="dispatch-steps">
      <el-steps :active="activeStepIndex" align-center finish-status="success" process-status="process">
        <el-step
          v-for="step in dispatchSteps"
          :key="step.key"
          :title="step.title"
          :status="step.status"
          :description="step.description"
        ></el-step>
      </el-steps>
    </div>
    <div class="treebox" v-loading="treeLoading" element-loading-text="终端列表加载中...">
      <el-tree ref="tree" node-key="id" :highlight-current="true" :data="treeData" show-checkbox :props="defaultProps"
        :default-expanded-keys="defaultExpandedkeys" :expand-on-click-node="false">
        <template #default="{ data }">
          <div class="custom-tree-node">
            <span class="node-name">{{ data.displayName }}</span>
            <span
              v-if="isTerminalNode(data)"
              class="node-status"
              :class="getConnectionStatusClass(data)"
            >
              {{ formatConnectionText(data) }}
            </span>
          </div>
        </template>
      </el-tree>
    </div>

    <!-- 动态进度区 -->
    <div v-if="dispatchProgress && dispatchProgress.length" class="dispatch-progress-panel">
      <div class="dispatch-progress-title">执行进度</div>
      <el-table :data="dispatchProgress" size="mini" border style="width: 100%; margin-bottom: 8px;">
        <el-table-column prop="target" label="终端地址" width="140" />
        <el-table-column prop="executStateText" label="状态" width="90">
          <template #default="scope">
            <el-tag v-if="scope.row.executState === 2" type="info" effect="dark">
              <i class="el-icon-loading"></i> 执行中
            </el-tag>
            <el-tag v-else-if="scope.row.executState === 3" type="success">成功</el-tag>
            <el-tag v-else-if="scope.row.executState === 4 || scope.row.executState === 5" type="danger">失败</el-tag>
            <el-tag v-else-if="scope.row.executState === 6" type="warning">部分成功</el-tag>
            <el-tag v-else type="info">{{ scope.row.executStateText }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="msg" label="后台消息">
          <template #default="scope">
            <span>{{ scope.row.msg || scope.row.gatewayResult || ((scope.row.dto && scope.row.dto.msg) || (scope.row.dto && scope.row.dto.gatewayResult)) || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="extendContent" label="详细信息">
          <template #default="scope">
            <span>{{ scope.row.extendContent || (scope.row.dto && scope.row.dto.extendContent) || '-' }}</span>
          </template>
        </el-table-column>
      </el-table>
    </div>
    <template #footer>
      <span class="dialog-footer">
        <el-button :disabled="dispatching || treeLoading" @click="visible = false">取 消</el-button>
        <el-button type="primary" :loading="dispatching" :disabled="dispatching || treeLoading" @click="handleSubmit">确认下发</el-button>
      </span>
    </template>
  </el-dialog>
</template>

<script>
import { getTreeTerritory } from "@/api/BnsTerritory";
import {
  batchTaskConfigCommand,
  batchCollectSchemeCommand,
  batchEventCollectSchemeCommand,
  batchReportingSchemeCommand,
  queryCommandList,
  queryCommandStatus,
  getTaskConfigCommand,
  getCollectSchemeCommand,
  getEventCollectSchemeCommand,
  getReportingSchemeCommand,
} from "@/api/Command";
import { getCollectSchemesPage, getEventCollectSchemesPage, getReportingSchemesPage } from "@/api/Scheme";
import {
  getConcentratorAllowActiveReportCache,
  getConcentratorAllowActiveReportCommandResult,
  queryConcentratorAllowActiveReport,
} from "@/api/BnsTerminal";
import {
  buildConcentratorSelectionTree,
  formatConcentratorConnectionText,
  getConcentratorConnectionStatusClass,
  isConcentratorNode,
} from "@/utils/territoryTree";

const ACTIVE_REPORT_STATUS_POLL_INTERVAL = 1500;
const ACTIVE_REPORT_STATUS_POLL_MAX_ATTEMPTS = 10;

export default {
  props: {
    itemData: {
      type: Object,
      default: () => {
        return {}
      }
    }
  },
  data() {
    return {
      visible: false, //弹窗
      dispatchProgress: [], // 动态进度区
      treeData: [],//数据项
      defaultExpandedkeys:[],//默认展开项
      treeLoading: false,
      treeLoaded: false,
      dispatching: false,
      dispatchSteps: [
        {
          key: "scheme",
          title: "下发方案",
          status: "wait",
          description: "等待执行",
        },
        {
          key: "task",
          title: "下发任务",
          status: "wait",
          description: "等待执行",
        },
      ],
      defaultProps: {
        children: "treeChildren",
      },
    };
  },
  computed: {
    dialogTitle() {
      const assignmentName = (this.itemData && this.itemData.assignmentName) || "未命名任务";
      return `任务列表指令下发 · ${assignmentName}`;
    },
    activeStepIndex() {
      const processIndex = this.dispatchSteps.findIndex((step) => step.status === "process");
      if (processIndex > -1) {
        return processIndex;
      }
      const errorIndex = this.dispatchSteps.findIndex((step) => step.status === "error");
      if (errorIndex > -1) {
        return errorIndex;
      }
      const allFinished = this.dispatchSteps.every((step) => step.status === "finish");
      if (allFinished) {
        return this.dispatchSteps.length;
      }
      const finishedCount = this.dispatchSteps.filter((step) => step.status === "finish").length;
      return finishedCount;
    },
  },
  watch: {
    async visible(newValue) {
      if(newValue){
        this.resetDispatchSteps();
        await this.ensureTreeLoaded();
        this.$nextTick(() => {
          if (this.$refs.tree && typeof this.$refs.tree.setCheckedKeys === "function") {
            this.$refs.tree.setCheckedKeys([]);
          }
        })
      } else {
        this.resetDispatchSteps();
      }
    }
  },
  methods: {
    async ensureTreeLoaded(forceReload = false) {
      if (this.treeLoading) {
        return;
      }
      if (this.treeLoaded && !forceReload) {
        return;
      }

      this.treeLoading = true;
      try {
        const treeData = await getTreeTerritory();
        const treeResult = this.buildDispatchTree(treeData);
        this.treeData = treeResult.treeData;
        this.treeLoaded = true;
      } catch (err) {
        this.treeData = [];
        this.defaultExpandedkeys = [];
        this.treeLoaded = false;
        if (this.visible) {
          this.$message.error(err || "终端列表加载失败，请稍后重试");
        }
      } finally {
        this.treeLoading = false;
      }
    },
    isTerminalNode(node) {
      return isConcentratorNode(node);
    },
    parseBooleanLike(value) {
      if (value === null || value === undefined || value === "") {
        return null;
      }
      if (typeof value === "boolean") {
        return value;
      }
      if (typeof value === "number") {
        if (value === 1) return true;
        if (value === 0) return false;
        return null;
      }
      const text = `${value}`.trim();
      if (!text) {
        return null;
      }
      const lower = text.toLowerCase();
      const trueWords = ["1", "true", "yes", "y", "online", "connected", "on", "enable", "enabled", "active", "available"];
      const falseWords = ["0", "false", "no", "n", "offline", "disconnected", "off", "disable", "disabled", "inactive", "unavailable"];
      if (trueWords.includes(lower)) {
        return true;
      }
      if (falseWords.includes(lower)) {
        return false;
      }
      if (["在线", "已连接", "连接", "已上线", "正常", "启用"].some((word) => text.includes(word))) {
        return true;
      }
      if (["离线", "未连接", "未上线", "禁用", "停用"].some((word) => text.includes(word))) {
        return false;
      }
      return null;
    },
    readBooleanFromKeys(item, keys = []) {
      if (!item || typeof item !== "object") {
        return null;
      }
      for (let index = 0; index < keys.length; index++) {
        const key = keys[index];
        if (!Object.prototype.hasOwnProperty.call(item, key)) {
          continue;
        }
        const parsed = this.parseBooleanLike(item[key]);
        if (parsed !== null) {
          return parsed;
        }
      }
      return null;
    },
    getTerminalEnabledState(item) {
      const enabledStandard = this.parseBooleanLike(item && item.enabled);
      if (enabledStandard !== null) {
        return enabledStandard;
      }
      const enabledText = this.parseBooleanLike(item && item.enabledText);
      if (enabledText !== null) {
        return enabledText;
      }

      const negativeFlag = this.readBooleanFromKeys(item, ["isDisable", "isDisabled", "disabled", "isForbidden", "forbidden"]);
      if (negativeFlag !== null) {
        return !negativeFlag;
      }
      const positiveFlag = this.readBooleanFromKeys(item, ["isEnable", "isEnabled", "enabled", "enable", "isActive", "active", "isAvailable", "available"]);
      if (positiveFlag !== null) {
        return positiveFlag;
      }
      const statusText = [
        item && item.statusText,
        item && item.stateText,
        item && item.runStateText,
        item && item.terminalStateText,
      ].filter((value) => value !== null && value !== undefined).join(" ");
      if (["禁用", "停用"].some((word) => statusText.includes(word))) {
        return false;
      }
      return true;
    },
    getTerminalConnectionState(item) {
      const onlineType = Number(item && item.terminalOnlineType);
      if ([1, 2, 3].includes(onlineType)) {
        if (onlineType === 1) {
          return true;
        }
        if (onlineType === 2) {
          return false;
        }
        return null;
      }

  const onlineTypeText = this.toNonEmptyText(item && item.terminalOnlineTypeText);
      if (onlineTypeText) {
        if (["在线", "已连接", "连接", "已上线", "正常"].some((word) => onlineTypeText.includes(word))) {
          return true;
        }
        if (["离线", "未连接", "未上线", "断开"].some((word) => onlineTypeText.includes(word))) {
          return false;
        }
        if (onlineTypeText.includes("未知")) {
          return null;
        }
      }

      const directFlag = this.readBooleanFromKeys(item, [
        "isOnline",
        "online",
        "isConnected",
        "connected",
        "isLink",
        "linked",
        "connectState",
        "connectionState",
        "linkState",
        "onlineState",
        "terminalState",
        "communicationState",
      ]);
      if (directFlag !== null) {
        return directFlag;
      }

      const text = [
        item && item.connectionStateText,
        item && item.connectStateText,
        item && item.onlineStateText,
        item && item.terminalStateText,
        item && item.statusText,
        item && item.stateText,
      ].filter((value) => value !== null && value !== undefined).join(" ");

      if (!text) {
        return null;
      }
      if (["在线", "已连接", "连接", "已上线", "正常"].some((word) => text.includes(word))) {
        return true;
      }
      if (["离线", "未连接", "未上线", "断开"].some((word) => text.includes(word))) {
        return false;
      }
      return null;
    },
    buildDispatchTree(sourceNodes = []) {
      const treeResult = buildConcentratorSelectionTree(sourceNodes, {
        hideDisabledConcentrator: true,
        showConnectionState: true,
        onlyConnectedSelectable: true,
      });
      this.defaultExpandedkeys = treeResult.defaultExpandedKeys;
      return treeResult;
    },
    formatConnectionText(item) {
      return formatConcentratorConnectionText(item && item.connectionState);
    },
    getConnectionStatusClass(item) {
      return getConcentratorConnectionStatusClass(item && item.connectionState);
    },
    resetDispatchSteps() {
      this.dispatching = false;
      this.dispatchSteps = [
        {
          key: "scheme",
          title: "下发方案",
          status: "wait",
          description: "等待执行",
        },
        {
          key: "task",
          title: "下发任务",
          status: "wait",
          description: "等待执行",
        },
      ];
    },
    updateDispatchStep(key, status, description) {
      this.dispatchSteps = this.dispatchSteps.map((step) => {
        if (step.key !== key) {
          return step;
        }
        return {
          ...step,
          status,
          description,
        };
      });
    },
    toIntOrNull(value) {
      if (value === null || value === undefined || value === "") {
        return null;
      }
      const numberValue = Number(`${value}`.trim());
      if (!Number.isInteger(numberValue)) {
        return null;
      }
      return numberValue;
    },
    toNonEmptyText(value) {
      if (value === null || value === undefined) {
        return "";
      }
      return `${value}`.trim();
    },
    isGuid(value) {
      const text = this.toNonEmptyText(value);
      return /^[0-9a-fA-F]{8}\-[0-9a-fA-F]{4}\-[0-9a-fA-F]{4}\-[0-9a-fA-F]{4}\-[0-9a-fA-F]{12}$/.test(text);
    },
    pickGuidFromCandidates(candidates = []) {
      for (let index = 0; index < candidates.length; index++) {
        const text = this.toNonEmptyText(candidates[index]);
        if (this.isGuid(text)) {
          return text;
        }
      }
      return null;
    },
    extractDispatchErrorMessage(error, fallback = "下发失败") {
      const text = this.toNonEmptyText(error);
      if (text) {
        return text;
      }
      const nestedMessage = this.toNonEmptyText(
        (error && error.message) ||
        (error && error.error && error.error.message) ||
        (error && error.msg)
      );
      if (nestedMessage) {
        return nestedMessage;
      }
      return fallback;
    },
    sleep(ms) {
      return new Promise((resolve) => {
        setTimeout(resolve, ms);
      });
    },
    normalizeAllowActiveReportStatus(status) {
      if (!status || typeof status !== "object") {
        return null;
      }
      const allowActiveReport = this.parseBooleanLike(status.allowActiveReport);
      if (allowActiveReport === null) {
        return null;
      }
      return {
        allowActiveReport,
        stateConfirmed: this.parseBooleanLike(status.stateConfirmed) === true,
      };
    },
    isFinalActiveReportCommandState(commandState) {
      return [3, 4, 5, 6].includes(Number(commandState));
    },
    async waitForActiveReportQueryResult(terminalAddress, commandId) {
      for (let attempt = 0; attempt < ACTIVE_REPORT_STATUS_POLL_MAX_ATTEMPTS; attempt++) {
        const result = await getConcentratorAllowActiveReportCommandResult(terminalAddress, commandId, { load: false });
        const resolvedStatus = this.normalizeAllowActiveReportStatus(result && result.resolvedStatus);
        const lastKnownStatus = this.normalizeAllowActiveReportStatus(result && result.lastKnownStatus);

        if (resolvedStatus && resolvedStatus.stateConfirmed) {
          return resolvedStatus;
        }

        if (this.isFinalActiveReportCommandState(result && result.commandState)) {
          if (resolvedStatus) {
            return resolvedStatus;
          }
          if (lastKnownStatus) {
            return lastKnownStatus;
          }
          break;
        }

        if (attempt < ACTIVE_REPORT_STATUS_POLL_MAX_ATTEMPTS - 1) {
          await this.sleep(ACTIVE_REPORT_STATUS_POLL_INTERVAL);
        }
      }

      return null;
    },
    async getAllowActiveReportStatusForTerminal(terminalAddress) {
      const queryResult = await queryConcentratorAllowActiveReport(terminalAddress, { load: false });
      if (queryResult && queryResult.commandId) {
        const queriedStatus = await this.waitForActiveReportQueryResult(terminalAddress, queryResult.commandId);
        if (queriedStatus) {
          return queriedStatus;
        }
      }

      const cachedStatus = await getConcentratorAllowActiveReportCache(terminalAddress, { load: false });
      return this.normalizeAllowActiveReportStatus(cachedStatus);
    },
    async resolveReportingSchemeBusinessType({ schemeNum, schemeName }) {
      const directValue = this.toIntOrNull(
        (this.itemData && this.itemData.businessType) ||
        (this.itemData && this.itemData.reportingBusinessType) ||
        (this.itemData && this.itemData.schemeBusinessType)
      );
      if ([0, 1].includes(Number(directValue))) {
        return directValue;
      }

      const normalizedName = this.normalizeSchemeName(schemeName);
      const query = {
        SkipCount: 0,
        MaxResultCount: 1000,
        Sorting: "schemeNum asc",
      };
      if (normalizedName) {
        query.SchemeName = normalizedName;
        query.Name = normalizedName;
        query.Filter = normalizedName;
        query.Keyword = normalizedName;
      }
      if (schemeNum !== null) {
        query.SchemeNum = schemeNum;
      }

      try {
        const response = await getReportingSchemesPage(query);
        const list = Array.isArray(response && response.items) ? response.items : Array.isArray(response) ? response : [];
        const matched = list.find((item) => {
          const currentNum = this.toIntOrNull((item && item.schemeNum) || (item && item.value));
          const currentName = this.normalizeSchemeName((item && item.schemeName) || (item && item.displayName));
          if (schemeNum !== null && currentNum === schemeNum) {
            return true;
          }
          return normalizedName && currentName === normalizedName;
        });
        const businessType = this.toIntOrNull(
          (matched && matched.businessType) ||
          (matched && matched.reportingBusinessType) ||
          (matched && matched.schemeBusinessType)
        );
        return [0, 1].includes(Number(businessType)) ? businessType : null;
      } catch (error) {
        return null;
      }
    },
    async ensureActiveReportSwitchBeforeDispatch(terminalAddresses = []) {
      const schemeType = this.toIntOrNull(this.itemData && this.itemData.schemeType);
      if (Number(schemeType) !== 4) {
        return true;
      }

      const schemeNum = this.toIntOrNull(this.itemData && this.itemData.schemeNum);
      const schemeName = this.normalizeSchemeName(this.itemData && this.itemData.schemeName);
      const businessType = await this.resolveReportingSchemeBusinessType({ schemeNum, schemeName });
      if (Number(businessType) !== 1) {
        return true;
      }

      const normalizedAddresses = Array.isArray(terminalAddresses)
        ? Array.from(new Set(terminalAddresses.map((item) => this.toNonEmptyText(item)).filter((item) => item)))
        : [];

      const disabledAddresses = [];
      for (let index = 0; index < normalizedAddresses.length; index++) {
        const terminalAddress = normalizedAddresses[index];
        let status = null;
        try {
          status = await this.getAllowActiveReportStatusForTerminal(terminalAddress);
        } catch (error) {
          this.$message.error(`召测集中器 ${terminalAddress} 的允许主动上报状态失败，请稍后重试。`);
          return false;
        }
        if (!status || status.allowActiveReport !== true) {
          disabledAddresses.push(terminalAddress);
        }
      }

      if (disabledAddresses.length === 0) {
        return true;
      }

      const targetTerminalAddress = disabledAddresses[0];
      const terminalText = disabledAddresses.join("、");
      const detailText = disabledAddresses.length > 1
        ? `当前选择的集中器中，以下设备未开启“允许主动上报”：${terminalText}。请先在终端侧完成开关处理后再重新下发。`
        : `集中器 ${targetTerminalAddress} 未开启“允许主动上报”。请先处理开关后再重新下发。`;

      this.$alert(detailText, "主动上报提醒", {
        type: "warning",
        confirmButtonText: "知道了",
      });
      return false;
    },
    getSchemeGuidFromTaskItem(item) {
      if (!item || typeof item !== "object") {
        return null;
      }
      return this.pickGuidFromCandidates([
        item.schemeId,
        item.schemeID,
        item.reportingSchemeId,
        item.collectSchemeId,
        item.ordinaryCollectSchemeId,
      ]);
    },
    getTaskGuidFromTaskItem(item) {
      if (!item || typeof item !== "object") {
        return null;
      }
      return this.pickGuidFromCandidates([
        item.assignmentGuid,
        item.assignmentGUID,
        item.assignmentConfigUnitId,
        item.assignmentConfigGuid,
        item.taskGuid,
        item.taskID,
        item.taskId,
        item.parameter,
        item.value,
        item.id,
      ]);
    },
    getSchemeGuidFromSchemeItem(item) {
      if (!item || typeof item !== "object") {
        return null;
      }
      return this.pickGuidFromCandidates([
        item.schemeId,
        item.schemeID,
        item.reportingSchemeId,
        item.collectSchemeId,
        item.ordinaryCollectSchemeId,
        item.parameter,
        item.value,
        item.id,
      ]);
    },
    getSchemeListRequest(schemeType) {
      const map = {
        1: getCollectSchemesPage,
        2: getEventCollectSchemesPage,
        4: getReportingSchemesPage,
      };
      return map[Number(schemeType)] || null;
    },
    normalizeSchemeName(value) {
      if (value === null || value === undefined) {
        return "";
      }
      return `${value}`.trim();
    },
    async resolveSchemeCommandParameter({ schemeType, schemeNum, schemeName }) {
      const directGuid = this.getSchemeGuidFromTaskItem(this.itemData);
      if (directGuid) {
        return directGuid;
      }

      const requestFn = this.getSchemeListRequest(schemeType);
      if (typeof requestFn !== "function") {
        return null;
      }

      const normalizedName = this.normalizeSchemeName(schemeName);
      const query = {
        SkipCount: 0,
        MaxResultCount: 1000,
        Sorting: "schemeNum asc",
      };
      if (normalizedName) {
        query.SchemeName = normalizedName;
        query.Name = normalizedName;
        query.Filter = normalizedName;
        query.Keyword = normalizedName;
      }
      if (schemeNum !== null) {
        query.SchemeNum = schemeNum;
      }

      try {
        let response = await requestFn(query);
        let list = Array.isArray(response && response.items) ? response.items : Array.isArray(response) ? response : [];
        if (!Array.isArray(list) || list.length === 0) {
          response = await requestFn({
            SkipCount: 0,
            MaxResultCount: 1000,
            Sorting: "schemeNum asc",
          });
          list = Array.isArray(response && response.items) ? response.items : Array.isArray(response) ? response : [];
        }
        const candidates = list
          .map((item) => ({
            item,
            commandParameter: this.getSchemeGuidFromSchemeItem(item),
            schemeNum: this.toIntOrNull((item && item.schemeNum) || (item && item.value)),
            schemeName: this.normalizeSchemeName((item && item.schemeName) || (item && item.displayName)),
          }))
          .filter((entry) => entry.commandParameter !== null);

        const exactMatch = candidates.find((entry) => entry.schemeNum === schemeNum && entry.schemeName === normalizedName);
        if (exactMatch) {
          return exactMatch.commandParameter;
        }

        const numberMatch = candidates.find((entry) => entry.schemeNum === schemeNum);
        if (numberMatch) {
          return numberMatch.commandParameter;
        }

        const nameMatch = normalizedName
          ? candidates.find((entry) => entry.schemeName === normalizedName)
          : null;
        if (nameMatch) {
          return nameMatch.commandParameter;
        }
      } catch (error) {
        // ignore and fallback
      }

      return null;
    },
    async getSchemeDispatchInfo() {
      const schemeType = this.toIntOrNull(this.itemData && this.itemData.schemeType);
      const schemeNum = this.toIntOrNull(this.itemData && this.itemData.schemeNum);
      const schemeName = this.normalizeSchemeName(this.itemData && this.itemData.schemeName);
      if (![1, 2, 4].includes(Number(schemeType))) {
        return null;
      }
      const schemeParameter = await this.resolveSchemeCommandParameter({ schemeType, schemeNum, schemeName });
      if (schemeParameter === null) {
        return null;
      }
      const commandMap = {
        1: batchCollectSchemeCommand,
        2: batchEventCollectSchemeCommand,
        4: batchReportingSchemeCommand,
      };
      const readbackMap = {
        1: getCollectSchemeCommand,
        2: getEventCollectSchemeCommand,
        4: getReportingSchemeCommand,
      };
      const commandFn = commandMap[Number(schemeType)] || null;
      const readbackFn = readbackMap[Number(schemeType)] || null;
      if (!commandFn || !readbackFn) {
        return null;
      }
      return {
        commandFn,
        readbackFn,
        payload: {
          parameter: schemeParameter,
        },
      };
    },
    getTaskDispatchInfo() {
      const parameter = this.getTaskGuidFromTaskItem(this.itemData);
      if (parameter === null) {
        return null;
      }
      return {
        payload: {
          parameter,
        },
      };
    },
    unwrapResponseData(response) {
      if (response && typeof response === "object" && response.data !== undefined) {
        return response.data;
      }
      return response;
    },
    unwrapResponseItems(response) {
      const source = this.unwrapResponseData(response);
      if (Array.isArray(source && source.items)) {
        return source.items;
      }
      if (Array.isArray(source)) {
        return source;
      }
      return [];
    },
    toIsoTimeWithOffset(offsetMs = 0) {
      return new Date(Date.now() + offsetMs).toISOString();
    },
    normalizeExecutState(value) {
      const parsed = this.toIntOrNull(value);
      return parsed === null ? 0 : parsed;
    },
    getExecutStateDisplayText(executState, executStateText = "") {
      const text = this.toNonEmptyText(executStateText);
      if (text) {
        return text;
      }
      const stateTextMap = {
        1: "等待执行",
        2: "执行中",
        3: "执行成功",
        4: "执行失败",
        5: "已取消",
        6: "部分成功",
        7: "已加载未执行",
      };
      return stateTextMap[executState] || "状态未知";
    },
    summarizeTerminalStates(results = []) {
      const safeResults = Array.isArray(results) ? results : [];
      const summary = {
        total: safeResults.length,
        success: 0,
        failed: 0,
        partial: 0,
        processing: 0,
        unknown: 0,
      };

      safeResults.forEach((item) => {
        const state = this.normalizeExecutState(item && item.executState);
        if (state === 3) {
          summary.success += 1;
          return;
        }
        if (state === 4 || state === 5) {
          summary.failed += 1;
          return;
        }
        if (state === 6) {
          summary.partial += 1;
          return;
        }
        if (state === 1 || state === 2 || state === 7) {
          summary.processing += 1;
          return;
        }
        summary.unknown += 1;
      });

      return summary;
    },
    buildStageSummaryText(summary = {}) {
      return `成功${summary.success || 0}，失败${summary.failed || 0}，部分成功${summary.partial || 0}，处理中${summary.processing || 0}，未知${summary.unknown || 0}`;
    },
    stringifyCommandMessage(message) {
      if (message === null || message === undefined || message === "") {
        return "";
      }
      if (typeof message === "string") {
        return message.trim();
      }
      if (typeof message === "object") {
        const preferred = this.toNonEmptyText(
          (message && message.msg) ||
          (message && message.message) ||
          (message && message.deviceResult) ||
          (message && message.gatewayResult)
        );
        if (preferred) {
          return preferred;
        }
        try {
          return JSON.stringify(message);
        } catch (error) {
          return "";
        }
      }
      return this.toNonEmptyText(message);
    },
    getTerminalFailureReason(item = {}) {
      const reasonCandidates = [
        item && item.dto && item.dto.msg,
        item && item.dto && item.dto.message,
        item && item.dto && item.dto.error,
        item && item.dto && item.dto.extendContent,
        item && item.msg,
        item && item.message,
      ];
      for (let index = 0; index < reasonCandidates.length; index += 1) {
        const text = this.stringifyCommandMessage(reasonCandidates[index]);
        if (text) {
          return text;
        }
      }
      return "";
    },
    buildStageFailureMessage(stageName, terminalResults = []) {
      const summary = this.summarizeTerminalStates(terminalResults);
      const failedTargets = (terminalResults || [])
        .filter((item) => this.normalizeExecutState(item && item.executState) !== 3)
        .slice(0, 3)
        .map((item) => {
          const target = this.toNonEmptyText(item && item.target) || "未知终端";
          const stateText = this.getExecutStateDisplayText(this.normalizeExecutState(item && item.executState), item && item.executStateText);
          const reason = this.getTerminalFailureReason(item);
          return reason ? `${target}(${stateText}：${reason})` : `${target}(${stateText})`;
        });
      const details = failedTargets.length > 0 ? `；异常终端：${failedTargets.join("，")}` : "";
      return `${stageName}下发未全部成功：${this.buildStageSummaryText(summary)}${details}`;
    },
    updateDispatchProgressItem(index, patch) {
      if (index < 0 || index >= this.dispatchProgress.length) {
        return;
      }
      const nextList = this.dispatchProgress.slice();
      nextList[index] = {
        ...(nextList[index] || {}),
        ...patch,
      };
      this.dispatchProgress = nextList;
    },
    async findLatestCommandId({ target, beginTimeIso, logType = null, retryTimes = 10 }) {
      const targetText = this.toNonEmptyText(target);
      if (!targetText) {
        return null;
      }

      for (let index = 0; index < retryTimes; index += 1) {
        const query = {
          Target: targetText,
          BeginTime: beginTimeIso,
          EndTime: this.toIsoTimeWithOffset(3000),
          SkipCount: 0,
          MaxResultCount: 5,
          Sorting: "CreationTime DESC",
        };
        const logTypeValue = this.toIntOrNull(logType);
        if (logTypeValue !== null) {
          query.LogType = logTypeValue;
        }

        const response = await queryCommandList(query, { load: false });
        const list = this.unwrapResponseItems(response);
        if (Array.isArray(list) && list.length > 0) {
          const first = list[0] || {};
          const commandId = this.toNonEmptyText(first.id || first.commandId);
          if (commandId) {
            return commandId;
          }
        }

        await this.sleep(1000);
      }

      return null;
    },
    async pollCommandToTerminalState({ commandId, timeoutMs = 90000, intervalMs = 2000 }) {
      const terminalStateSet = new Set([3, 4, 5, 6]);
      const startTime = Date.now();
      let latestDto = null;

      while (Date.now() - startTime < timeoutMs) {
        const response = await queryCommandStatus(commandId, { load: false });
        const dto = this.unwrapResponseData(response) || {};
        const executState = this.normalizeExecutState(dto.executState);
        latestDto = {
          ...dto,
          executState,
          executStateText: this.getExecutStateDisplayText(executState, dto.executStateText),
        };

        if (terminalStateSet.has(executState)) {
          return {
            timeout: false,
            dto: latestDto,
          };
        }

        await this.sleep(intervalMs);
      }

      return {
        timeout: true,
        dto: latestDto,
      };
    },
    async trackTerminalCommandState({ target, beginTimeIso, logType = null }) {
      const targetText = this.toNonEmptyText(target);
      if (!targetText) {
        return {
          target: "",
          commandId: "",
          executState: 0,
          executStateText: "终端地址为空",
        };
      }

      try {
        const commandId = await this.findLatestCommandId({
          target: targetText,
          beginTimeIso,
          logType,
        });
        if (!commandId) {
          return {
            target: targetText,
            commandId: "",
            executState: 0,
            executStateText: "命令创建中，请稍后继续查询",
          };
        }

        const pollResult = await this.pollCommandToTerminalState({ commandId });
        const dto = pollResult.dto || {};
        if (pollResult.timeout) {
          return {
            target: targetText,
            commandId,
            executState: 0,
            executStateText: "状态未知（查询超时）",
            dto,
          };
        }

        return {
          target: targetText,
          commandId,
          executState: this.normalizeExecutState(dto.executState),
          executStateText: this.getExecutStateDisplayText(this.normalizeExecutState(dto.executState), dto.executStateText),
          dto,
        };
      } catch (error) {
        return {
          target: targetText,
          commandId: "",
          executState: 0,
          executStateText: this.extractDispatchErrorMessage(error, "状态查询失败"),
        };
      }
    },
    // 动态进度实时刷新（彻底消除假死感）
    async waitBatchCommandStates({ terminalAddresses, beginTimeIso, logType = null }) {
      const targets = Array.isArray(terminalAddresses) ? terminalAddresses : [];
      // 初始化进度
      this.dispatchProgress = targets.map(target => ({ target, executState: 1, executStateText: '等待执行', msg: '', gatewayResult: '', extendContent: '' }));

      // 每个终端独立异步轮询，且每轮都刷新
      const promises = targets.map((target, idx) => {
        return (async () => {
          let finished = false;
          let commandId = '';
          // 查找 commandId
          const commandIdVal = await this.findLatestCommandId({ target, beginTimeIso, logType });
          if (!commandIdVal) {
            this.updateDispatchProgressItem(idx, { target, executState: 0, executStateText: '命令创建中，请稍后继续查询', msg: '', gatewayResult: '', extendContent: '' });
            return { target, commandId: '', executState: 0, executStateText: '命令创建中，请稍后继续查询' };
          }
          commandId = commandIdVal;
          // 轮询状态
          const startTime = Date.now();
          while (!finished && Date.now() - startTime < 90000) {
            const response = await queryCommandStatus(commandId, { load: false });
            const dto = this.unwrapResponseData(response) || {};
            const executState = this.normalizeExecutState(dto.executState);
            const executStateText = this.getExecutStateDisplayText(executState, dto.executStateText);
            // 优先展示后端有效信息
            let msg = '';
            if (dto.msg && typeof dto.msg === 'string') msg = dto.msg;
            else if (dto.msg && dto.msg.msg) msg = dto.msg.msg;
            else if (dto.items && dto.items.msg) msg = dto.items.msg;
            else if (dto.gatewayResult) msg = dto.gatewayResult;
            else if (dto.items && dto.items.gatewayResult) msg = dto.items.gatewayResult;
            // "执行中"兜底
            if (executState === 2 && !msg) msg = '执行中';
            let gatewayResult = dto.gatewayResult || (dto.items && dto.items.gatewayResult) || '';
            let extendContent = dto.extendContent || '';
            // 每轮都刷新
            this.updateDispatchProgressItem(idx, {
              target,
              executState,
              executStateText,
              msg,
              gatewayResult,
              extendContent,
              dto
            });
            // 判断是否结束
            if ([3, 4, 5, 6].includes(executState)) {
              finished = true;
              return {
                target,
                commandId,
                executState,
                executStateText,
                msg,
                gatewayResult,
                extendContent,
                dto
              };
            }
            // 继续轮询
            await this.sleep(2000);
          }
          // 超时
          this.updateDispatchProgressItem(idx, {
            target,
            executState: 0,
            executStateText: '状态未知（查询超时）',
            msg: '',
            gatewayResult: '',
            extendContent: '',
            dto: null
          });
          return {
            target,
            commandId,
            executState: 0,
            executStateText: '状态未知（查询超时）',
            msg: '',
            gatewayResult: '',
            extendContent: '',
            dto: null
          };
        })();
      });
      // 等待全部完成
      const finalResults = await Promise.all(promises);
      const summary = this.summarizeTerminalStates(finalResults);
      return {
        results: finalResults,
        summary,
        allSuccess: summary.total > 0 && summary.success === summary.total,
      };
    },
    async triggerReadbackSync(terminalAddresses = [], schemeDispatchInfo = null) {
      const targets = Array.isArray(terminalAddresses) ? terminalAddresses.filter((item) => this.toNonEmptyText(item)) : [];
      if (targets.length === 0) {
        return [];
      }

      const requestList = [];
      targets.forEach((terminalAddress) => {
        requestList.push({
          requestName: `任务配置回读(${terminalAddress})`,
          promise: getTaskConfigCommand({ terminalAddress }),
        });

        if (schemeDispatchInfo && typeof schemeDispatchInfo.readbackFn === "function") {
          requestList.push({
            requestName: `方案回读(${terminalAddress})`,
            promise: schemeDispatchInfo.readbackFn({ terminalAddress }),
          });
        }
      });

      const settledList = await Promise.allSettled(requestList.map((item) => item.promise));
      return settledList
        .map((result, index) => ({
          requestName: (requestList[index] && requestList[index].requestName) || "回读同步",
          result,
        }))
        .filter((item) => item.result && item.result.status === "rejected")
        .map((item) => ({
          requestName: item.requestName,
          message: this.extractDispatchErrorMessage(item.result && item.result.reason, "回读失败"),
        }));
    },
    async handleSubmit(){
      if (this.dispatching) {
        return;
      }
      const checkedNodes = this.$refs.tree.getCheckedNodes();
      const terminalAddresses = checkedNodes.filter(item => item.treeType == 2).map(item => item.code)

      if (terminalAddresses.length === 0) {
        this.$message.warning("请先选择要下发的终端");
        return;
      }

      const canContinueDispatch = await this.ensureActiveReportSwitchBeforeDispatch(terminalAddresses);
      if (!canContinueDispatch) {
        return;
      }

      const schemeDispatchInfo = await this.getSchemeDispatchInfo();
      if (!schemeDispatchInfo) {
        this.$message.warning("当前任务未找到可下发的方案标识（Guid），请检查任务绑定的方案");
        return;
      }

      const taskDispatchInfo = this.getTaskDispatchInfo();
      if (!taskDispatchInfo) {
        this.$message.warning("当前任务未找到可下发的任务标识（Guid）");
        return;
      }

      const schemePayload = {
        ...schemeDispatchInfo.payload,
        terminalAddresses,
      };
      const taskPayload = {
        ...taskDispatchInfo.payload,
        terminalAddresses,
      };

      this.dispatching = true;
      this.dispatchProgress = [];
      this.updateDispatchStep("scheme", "process", "正在下发方案...");

      try {
        const schemeBeginTime = this.toIsoTimeWithOffset(-3000);
        await schemeDispatchInfo.commandFn(schemePayload);

        this.updateDispatchStep("scheme", "process", "方案命令已创建，正在确认终端执行状态...");
        const schemeTrack = await this.waitBatchCommandStates({
          terminalAddresses,
          beginTimeIso: schemeBeginTime,
        });
        if (!schemeTrack.allSuccess) {
          throw new Error(this.buildStageFailureMessage("方案", schemeTrack.results));
        }

        this.updateDispatchStep("scheme", "finish", `方案下发成功（${this.buildStageSummaryText(schemeTrack.summary)}）`);
        this.updateDispatchStep("task", "process", "任务命令已创建，正在确认终端执行状态...");

        const taskBeginTime = this.toIsoTimeWithOffset(-3000);
        await batchTaskConfigCommand(taskPayload);

        const taskTrack = await this.waitBatchCommandStates({
          terminalAddresses,
          beginTimeIso: taskBeginTime,
        });
        if (!taskTrack.allSuccess) {
          throw new Error(this.buildStageFailureMessage("任务", taskTrack.results));
        }

        const readbackFailures = await this.triggerReadbackSync(terminalAddresses, schemeDispatchInfo);

        this.updateDispatchStep("task", "finish", `任务下发成功（${this.buildStageSummaryText(taskTrack.summary)}）`);
        if (readbackFailures.length === 0) {
          this.$message.success("下发成功：方案与任务均已确认终端执行成功，并已发起终端回读同步");
        } else {
          const summary = readbackFailures.map((item) => `${item.requestName}：${item.message}`).join("；");
          this.$message.warning(`方案与任务已确认终端执行结果，但部分回读同步失败：${summary}`);
        }
      } catch (err) {
        const errorText = this.extractDispatchErrorMessage(err, "下发失败");
        const hasStartedTaskStep = this.dispatchSteps.some((step) => step.key === "task" && ["process", "finish"].includes(step.status));
        if (hasStartedTaskStep) {
          this.updateDispatchStep("task", "error", errorText);
        } else {
          this.updateDispatchStep("scheme", "error", errorText);
        }
        this.$message.error(errorText);
      } finally {
        this.dispatching = false;
        // 结束后保留进度 2 秒再清空
        setTimeout(() => { this.dispatchProgress = []; }, 2000);
      }
    },
  },
};
</script>

<style scoped>
.dialog-tip {
  margin-bottom: 8px;
  color: var(--form-label-color);
  line-height: 20px;
}

.dispatch-steps {
  margin-bottom: 10px;
  padding: 8px 8px 4px;
  border: 1px solid var(--table-border-color);
  border-radius: 4px;
  background-color: var(--panel-soft-bg);
}

.dispatch-steps :deep(.el-step__description) {
  font-size: 12px;
}

.treebox {
  width: 100%;
  max-height: 400px;
  overflow-y: auto;
  background-color: var(--panel-soft-bg);
  border: 1px solid var(--table-border-color);
  border-radius: 4px;
  padding: 10px;
  box-sizing: border-box;
}

.treebox :deep(.el-tree) {
  background: transparent;
  color: var(--panel-text-color);
}

.treebox :deep(.el-tree-node__content:hover) {
  background-color: var(--table-row-even-bg);
}

.treebox :deep(.el-tree-node:focus > .el-tree-node__content) {
  background-color: var(--table-row-even-bg);
}

.treebox :deep(.el-tree--highlight-current .el-tree-node.is-current > .el-tree-node__content) {
  background-color: var(--table-row-odd-bg);
}

.custom-tree-node {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: var(--panel-text-color);
}

.node-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.node-status {
  display: inline-flex;
  align-items: center;
  font-size: 12px;
  flex-shrink: 0;
}

.node-status::before {
  content: "";
  width: 6px;
  height: 6px;
  border-radius: 50%;
  margin-right: 6px;
  background-color: #909399;
}

.node-status--connected {
  color: #67c23a;
}

.node-status--connected::before {
  background-color: #67c23a;
}

.node-status--disconnected {
  color: #f56c6c;
}

.node-status--disconnected::before {
  background-color: #f56c6c;
}

.node-status--unknown {
  color: #909399;
}
</style>
