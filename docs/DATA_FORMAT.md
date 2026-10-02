# CSV 与备份格式 / CSV and backup formats

[文档目录](README.md) · [English](#english)

## 普通用户怎么选？

- **导出统计 CSV**：用 Excel 等软件阅读，一次运动一行，有日期、器材、时长、距离、热量、平均／峰值与数据来源。列名含单位，跟随 App 语言。导出当前筛选结果，或详情中的单次训练。
- **导出完整备份**：换设备或重装时使用 ZIP，包含训练记录、逐秒采样和自定义方案，可重新导入。
- **原始 CSV**：供需要进一步分析或交换数据的人使用。统计 CSV 不能当作原始 CSV 导入。

导入会先显示预览。完全相同的记录跳过；同一 ID 的内容冲突会取消整次导入，不覆盖本机内容。大备份可按单次训练分别导出。卸载前请保留好原文件。

## 当前原始格式

UTF-8（导出含 BOM），逗号分隔，按 CSV 规则转义双引号，导出 CRLF、导入也接受 LF。空单元格表示未知，不等于零。字段名不随界面语言改变。

| 文件 | 当前版本 | 说明 |
| --- | --- | --- |
| `sessions.csv` | 5 | 每次训练的摘要和估算来源 |
| `samples.csv` | 3 | 有效训练时间上的采样，通常约 1 Hz |
| `workouts.csv` | 1 | 每行一个训练阶段 |
| `manifest.txt` | 5 | 内容为 `OpenMOBI backup 5\n` |

ZIP 不向文件系统解压。继续支持备份 v1–v4、会话 v1/v2/v4、采样 v1/v2，缺失字段使用旧版兼容默认值。本地数据库为 Room v5，v1–v4 迁移保留历史数据。

### 字段

`sessions.csv`：

```csv
schema,session_id,start_utc,end_utc,time_zone,device,machine,protocol,elapsed_ms,distance_m,simulated,status,calories_kcal,calories_estimated,distance_estimated,weight_kg,met,workout_id,workout_title,archived,energy_model
```

`samples.csv`：

```csv
schema,session_id,elapsed_ms,cadence_rpm,resistance,speed_mps,distance_m,heart_bpm,power_w,strokes,calories_kcal,incline_percent,stride_m,force_n,step_rate,step_count,target_cadence,power_estimated
```

`workouts.csv`：

```csv
schema,workout_id,title,step_index,condition,target,resistance_percent
```

- 训练 ID 为 UUID，开始／结束时间为 ISO 8601 UTC，时区使用 IANA 名称。有效时长不含暂停；距离与桨数是本次有效训练的累计值。
- 器材类型：ELLIPTICAL / BIKE / ROWER / TREADMILL / HEART / UNKNOWN；协议：V1 / V2 / FTMS / HUANTONG / DEMO / UNKNOWN。
- 状态为 active / completed / interrupted / stopped。恢复或导入活动记录会标为 interrupted，不会自动开始运动。
- `energy_model` 为 device / legacy-v1 / met / mixed，旧记录可能为空。估算标记为 true/false。
- `archived` 仅用于兼容旧数据；当前界面没有归档操作，默认包含所有标记的记录。
- `cadence_rpm` 为历史字段名，划船机表示桨频，部分跑步机表示步频，请结合器材类型解读。
- 测量功率允许 −32768–32767 W；估算功率允许 0–50000 W 以便模型边界值能往返导入，不表示正常运动功率范围。拉力也允许协议中的有符号值。
- 方案每套 1–200 阶段，序号从 0 递增；TIME 为秒、DISTANCE 为米、STROKES 为次数；阻力为 0–100% 或空值，按器材档位范围换算。
- 设备名、方案 ID／名称等文本用一个前导单引号避免表格公式执行；导入仅去掉一个该前缀，因此原有单引号可往返保留。

### 大小和统计约定

输入与解压后总内容上限 32 MiB，单 CSV 最多 200000 数据行，单字段最多 4096 字符。采样必须引用已有或同批会话，时间不得超出会话有效长度；异常时整批回滚。

统计平均值按有效采样时间加权，单个读数最多覆盖前 5 秒。心率 0 为缺测，零速度／功率有效；曲线最多绘制 240 点并保留端点与极值。日期按当前设备时区分组。统计 CSV 显示本地日期及 UTC 偏移、带单位列名和 `—` 缺测标记，不用于原始数据往返。

完整备份用于训练数据与方案迁移，**不承诺复制所有界面偏好或收藏设置**。官方 App 缓存及停服后的云端记录不属于此格式。

## English

### Which export should I use?

- **Export summary CSV:** for reading in a spreadsheet. One workout per row, including date, equipment, duration, distance, energy, averages/peaks and data sources. Localized headings include units. Exports the current filter or a single workout from its details.
- **Export full backup:** a ZIP for moving devices or reinstalling, containing sessions, samples and custom plans. It can be imported again.
- **Raw CSV:** for data analysis and exchange. Summary CSV cannot be imported as raw data.

Import shows a preview first. Identical records are skipped; different content with the same ID cancels the whole import without overwriting local data. Individual workout backups help split large exports. Keep original files before uninstalling.

### Current raw format

UTF-8 with a BOM on export, comma-separated, CSV quote escaping, CRLF on export and LF accepted on import. Empty cells mean unknown, not zero. Raw column names do not depend on interface language.

| File | Current schema | Contents |
| --- | --- | --- |
| `sessions.csv` | 5 | Workout summaries and estimate sources |
| `samples.csv` | 3 | Samples on active workout time, usually around 1 Hz |
| `workouts.csv` | 1 | One stage per row |
| `manifest.txt` | 5 | Literal `OpenMOBI backup 5\n` |

ZIPs are parsed without extracting paths to disk. Backup versions 1–4, session schemas 1/2/4 and sample schemas 1/2 remain readable with compatibility defaults. Room v5 migrates v1–v4 without replacing historical values. Exact CSV headers are listed in the Chinese section above; field names are identical in every language.

- Session IDs are UUIDs; start/end use ISO 8601 UTC and IANA time-zone names. Active duration excludes pauses. Distance/strokes are relative to this workout's active time.
- Machines: ELLIPTICAL / BIKE / ROWER / TREADMILL / HEART / UNKNOWN. Protocols: V1 / V2 / FTMS / HUANTONG / DEMO / UNKNOWN.
- Status: active / completed / interrupted / stopped. Recovered/imported active records become interrupted; they never start a workout automatically.
- `energy_model`: device / legacy-v1 / met / mixed, or empty for older data. Estimate flags are true/false.
- `archived` preserves legacy round trips only. There is no archive action in the current UI; normal queries include every flag value.
- The historical `cadence_rpm` name also represents rowing cadence or step rate on some treadmills. Interpret it with the machine type.
- Measured power accepts −32768–32767 W; estimated power accepts 0–50000 W so model boundary values can round-trip. This is not a normal exercise range. Force also preserves signed protocol values.
- Plans have 1–200 stages with indices starting at zero. TIME is seconds, DISTANCE meters and STROKES counts. Resistance is 0–100% or empty, mapped to equipment levels.
- Device names, plan IDs/titles and similar text receive one leading apostrophe to prevent spreadsheet formula execution. Import removes exactly one, preserving original apostrophes.

### Limits and statistics

Input and total uncompressed contents are limited to 32 MiB, each CSV to 200000 data rows, and each field to 4096 characters. Samples must reference an existing or same-import session and cannot exceed its active duration. Validation failure rolls back the whole import.

Averages are time-weighted, with each sample covering at most the preceding five seconds. Zero heart rate is missing; zero speed/power is valid. Charts retain endpoints/extremes with at most 240 points. Dates use the current device time zone. Summary CSV uses local dates with UTC offsets, unit-bearing headings and `—` for missing data; it is not a round-trip backup format.

A full backup transfers workout data and plans, **not necessarily every display preference or favorite setting**. Original-app caches and unavailable cloud history are outside this format.
