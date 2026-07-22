# Monitor 采集数据 Excel 模板规范（草案）

## 1. 目标

本规范用于统一 monitor-service 的 Excel 导入字段定义，覆盖：

1. 发电采集数据（含三相电、功率因数、有功/无功、15 分钟电量、当日累计电量）。
2. 天气采集数据（训练和预测所需关键气象因子）。

适用对象：前端、后端、数据团队。

## 2. 通用规则

1. 文件格式：`.xlsx`。
2. Sheet 名称：默认 `data`。
3. 第一行为列头，不可空。
4. 时间列统一：`record_time`，推荐格式 `yyyy-MM-dd HH:mm:ss`。
5. 时区：默认 `Asia/Shanghai`，由导入参数 `timezone` 覆盖。
6. 数值列使用半角数字与小数点。
7. 空值策略：
- 必填列不可空。
- 可选列可空。
- 空字符串按 null 处理。
8. 去重键建议：`plant_id + record_time (+ device_id)`。

## 3. 模板 A：发电采集数据

## 3.1 列定义

| 列名 | 中文含义 | 必填 | 类型 | 单位 | 说明 |
|---|---|---|---|---|---|
| plant_id | 电站ID | 是 | string | - | 必须是已存在电站 |
| device_id | 设备ID | 否 | string | - | 逆变器或采集点 |
| record_time | 采样时间 | 是 | datetime | - | 建议 15 分钟对齐 |
| active_power_kw | 有功功率 | 是 | decimal | kW | 采样时刻有功 |
| reactive_power_kvar | 无功功率 | 是 | decimal | kvar | 采样时刻无功 |
| apparent_power_kva | 视在功率 | 否 | decimal | kVA | 可由P/Q推算 |
| power_factor_total | 总功率因数 | 是 | decimal | - | 范围 -1 到 1 |
| power_factor_phase_a | A相功率因数 | 否 | decimal | - | 范围 -1 到 1 |
| power_factor_phase_b | B相功率因数 | 否 | decimal | - | 范围 -1 到 1 |
| power_factor_phase_c | C相功率因数 | 否 | decimal | - | 范围 -1 到 1 |
| voltage_phase_a | A相电压 | 是 | decimal | V | 建议 > 0 |
| voltage_phase_b | B相电压 | 是 | decimal | V | 建议 > 0 |
| voltage_phase_c | C相电压 | 是 | decimal | V | 建议 > 0 |
| current_phase_a | A相电流 | 是 | decimal | A | 建议 >= 0 |
| current_phase_b | B相电流 | 是 | decimal | A | 建议 >= 0 |
| current_phase_c | C相电流 | 是 | decimal | A | 建议 >= 0 |
| frequency_hz | 电网频率 | 否 | decimal | Hz | 常见 50/60Hz |
| energy_15m_kwh | 15分钟发电量 | 是 | decimal | kWh | 当前时段电量 |
| energy_daily_kwh | 当日累计发电量 | 是 | decimal | kWh | 电站当日累计 |
| source | 数据来源 | 否 | enum | - | MANUAL/SCADA/IMPORT |
| remark | 备注 | 否 | string | - | 业务备注 |

## 3.2 校验规则

1. `plant_id` 必须存在于电站主数据。
2. `record_time` 必须可解析，且建议是 15 分钟整点。
3. `power_factor_total` 范围：`-1 <= x <= 1`。
4. 分相功率因数若提供，也必须在 `-1` 到 `1`。
5. 三相电压与电流必须全部提供或全部为空（用于保证相数据完整性）。
6. `active_power_kw`、`reactive_power_kvar`、`energy_15m_kwh`、`energy_daily_kwh` 建议 >= 0。
7. `energy_daily_kwh` 不应小于同日早先时刻的值（单调非递减）。
8. 同一 `plant_id + record_time + device_id` 出现重复时，按 `deduplicateStrategy` 处理：
- `UPSERT`：后写覆盖先写。
- `SKIP`：忽略重复行并计入错误报告。

## 3.3 示例（列头）

```text
plant_id,device_id,record_time,active_power_kw,reactive_power_kvar,apparent_power_kva,power_factor_total,power_factor_phase_a,power_factor_phase_b,power_factor_phase_c,voltage_phase_a,voltage_phase_b,voltage_phase_c,current_phase_a,current_phase_b,current_phase_c,frequency_hz,energy_15m_kwh,energy_daily_kwh,source,remark
```

## 4. 模板 B：天气采集数据

## 4.1 列定义

| 列名 | 中文含义 | 必填 | 类型 | 单位 | 说明 |
|---|---|---|---|---|---|
| plant_id | 电站ID | 是 | string | - | 必须是已存在电站 |
| record_time | 时间 | 是 | datetime | - | 建议 15 分钟对齐 |
| ghi | 总水平辐照度 | 是 | decimal | W/m2 | >= 0 |
| dni | 直射辐照度 | 否 | decimal | W/m2 | >= 0 |
| dhi | 散射辐照度 | 否 | decimal | W/m2 | >= 0 |
| temperature_c | 气温 | 是 | decimal | C | 合理范围建议 -50 到 70 |
| humidity_pct | 相对湿度 | 否 | decimal | % | 0 到 100 |
| cloud_cover_pct | 云量 | 否 | decimal | % | 0 到 100 |
| wind_speed_ms | 风速 | 否 | decimal | m/s | >= 0 |
| wind_direction_deg | 风向 | 否 | decimal | deg | 0 到 360 |
| source | 数据来源 | 否 | enum | - | MANUAL/API/IMPORT |
| remark | 备注 | 否 | string | - | 业务备注 |

## 4.2 校验规则

1. `plant_id` 必须存在。
2. 时间格式可解析，建议 15 分钟对齐。
3. 辐照度和风速应为非负值。
4. 百分比字段必须在 0 到 100 范围。

## 5. 导入接口约定

1. 发电数据导入：`POST /api/v1/monitor/plants/{plantId}/generation-records:import`
2. 天气数据导入：`POST /api/v1/monitor/plants/{plantId}/weather-records:import`

`multipart/form-data` 字段：

1. `file`：Excel 文件，必填。
2. `templateVersion`：模板版本，如 `v1.0`。
3. `timezone`：时区，如 `Asia/Shanghai`。
4. `deduplicateStrategy`：`UPSERT` 或 `SKIP`。

## 6. 导入结果

导入后返回任务 `jobId`，通过 `GET /api/v1/monitor/import-jobs/{jobId}` 查询：

1. `status`
2. `successCount`
3. `failedCount`
4. `errorReportUri`

## 7. 版本管理

1. 当前模板版本：`v1.0`。
2. 新增列仅允许向后兼容方式（新增可选列）。
3. 删除或重命名列必须升级到新版本，并保留旧版本兼容窗口。
