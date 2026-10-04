# CSV 与备份格式 / CSV and backup formats

[文档目录](README.md) · [English](#english)

## 普通用户怎么选？

- **导出统计 CSV**：用 Excel 等软件阅读，一次运动一行，有日期、器材、时长、距离、热量、平均／峰值与数据来源。列名含单位，跟随 App 语言。导出当前筛选结果，或详情中的单次训练。
- **导出完整备份**：换设备或重装时使用 ZIP，包含全部用户（含已移除）、头像、个人偏好、训练记录、采样和自定义方案；设备共用设置可选。
- **原始 CSV**：供需要进一步分析或交换数据的人使用。统计 CSV 不能当作原始 CSV 导入。

导入会先显示预览。完全相同的记录跳过；同一 ID 的内容冲突会取消整次导入，不覆盖本机内容。已有用户资料与个人设置覆盖默认关闭；本机移除状态优先，恢复不切换当前用户。设备共用设置单独选择。卸载前请保留好原文件。

## 当前原始格式

UTF-8（导出含 BOM），逗号分隔，按 CSV 规则转义双引号，导出 CRLF、导入也接受 LF。空单元格表示未知，不等于零。字段名不随界面语言改变。

| 文件 | 当前版本 | 说明 |
| --- | --- | --- |
| `sessions.csv` | 6 | 每次训练的摘要和估算来源 |
| `samples.csv` | 6 | 有效训练时间上的采样，通常约 1 Hz |
| `workouts.csv` | 2 | 每行一个训练阶段 |
| `manifest.txt` | 8 | 内容为 `OpenMOBI backup 8\n` |
| `preferences.json` | 2 | 可选，按分组迁移本机偏好 |

v8 ZIP 使用 `sessions/0.csv`、`samples/0.csv`、`workouts/0.csv` 等从 0 连续编号的 CSV 分块；各块使用上述相同表头及 CSV 版本。导出分页读取，导入先流式复制到应用私有暂存目录，用独立数据库完成校验，再以事务合并。文件路径只作白名单标识，不能指定解压目标。

继续支持备份 v1–v7、会话 v1/v2/v4/v5、采样 v1/v2/v3 和方案 v1。Room v8 从 v1–v7 迁移：旧方案和偏好进入稳定的原有用户，旧记录保持未分配。旧提示与目标桨频仅归档，不再驱动训练。用户偏好写入有持久日志，启动时幂等恢复。旧文件缺少 owner 字段不会清空本机已更正的归属。

### 字段

`sessions.csv`：

```csv
schema,session_id,start_utc,end_utc,time_zone,device,machine,protocol,elapsed_ms,distance_m,simulated,status,calories_kcal,calories_estimated,distance_estimated,weight_kg,met,workout_id,workout_title,archived,energy_model,owner_user_id,started_user_id,started_user_name,weight_source,met_source,workout_snapshot,capability_snapshot,identity_version,owner_history,entered_stage_ids
```

`samples.csv`：

```csv
schema,session_id,elapsed_ms,cadence_rpm,resistance,speed_mps,distance_m,heart_bpm,power_w,strokes,calories_kcal,incline_percent,stride_m,force_n,step_rate,step_count,target_cadence,power_estimated,jump_count,continuous_jumps,jump_interruptions,repetitions,load_kg,device_duration_sec,dumbbell_few_actions,dumbbell_action_number
```

`workouts.csv`：

```csv
schema,workout_id,title,step_index,condition,target,resistance_percent,owner_user_id,machine,stage_id,stage_kind,frequency_mode,frequency_lower,frequency_upper,heart_mode,heart_lower,heart_upper,default_frequency_enabled,default_frequency_lower,default_frequency_upper,default_heart_enabled,default_heart_lower,default_heart_upper,sound,vibration,speed_target_mps,incline_target_percent,legacy_unclassified,cloned_from
```

`users/N.csv`（schema 1，每块最多 8 档案）：

```csv
schema,user_id,name,weight_kg,met,met_source,avatar,created_at,removed_at,deleted,preferences,legacy_hints
```

头像资源为 `avatars/<sha256>.jpg`，校验散列、JPEG 和 512×512 尺寸；每张不超过 2 MiB。个人偏好 JSON 嵌在用户行，必须只含个人作用域键；不保存外部图片 URI。用户、当前归属、开始身份、方案归属和归属变更引用须存在，损坏引用整批拒绝。

- `owner_user_id` 可更正，`started_user_id`／`started_user_name` 和历史身体参数不可随更正重写。来源为 user/default/legacy 或兼容空值。`identity_version=1` 要求当前归属与开始身份；旧记录为 0。
- `workout_snapshot` 是当时方案的 workouts v2 CSV；`entered_stage_ids` 用分号保存已进入的稳定阶段 ID 前缀。不计算尚未实现的阶段达标率。
- `capability_snapshot` 为 JSON v1：machine/model/protocol/evidence，resistanceWrite/speedWrite/inclineWrite/resistanceFeedback 三态 supported/unsupported/unknown，阻力范围及单位、已观察指标。未知不伪装成不支持；快照不会启用额外器材控制。限制 4096 字符。
- `owner_history` 为 JSON 数组（最多 1000 项）：每项 id/at/from/to。校验 UUID、时间及用户引用。`legacy_hints` 为 JSON v1 的 personal_hints/target_cadence 归档，仅供显式导入。
- 新方案按器材分类；频率与心率独立 FOLLOW_PLAN/CUSTOM/OFF。频率有限非负、心率正整数，启用时至少一端且下限不大于上限。跑步机速度／坡度只作手动指导。

- 训练 ID 为 UUID，开始／结束时间为 ISO 8601 UTC，时区使用 IANA 名称。有效时长不含暂停；距离与桨数是本次有效训练的累计值。
- 器材类型：ELLIPTICAL / BIKE / ROWER / TREADMILL / HEART / UNKNOWN / JUMP_ROPE / DUMBBELL；协议：V1 / V2 / FTMS / HUANTONG / DEMO / UNKNOWN。
- 状态为 active / completed / interrupted / stopped。恢复或导入活动记录会标为 interrupted，不会自动开始运动。
- `energy_model` 为 device / legacy-v1 / legacy-mobi / legacy-rowing / met / mixed，旧记录可能为空。估算标记为 true/false。
- `archived` 仅用于兼容旧数据；当前界面没有归档操作，默认包含所有标记的记录。
- `cadence_rpm` 为历史字段名，划船机表示桨频，部分跑步机表示步频、跳绳表示跳频，请结合器材类型解读。
- 测量功率允许 −32768–32767 W；估算功率允许 0–50000 W 以便模型边界值能往返导入，不表示正常运动功率范围。拉力也允许协议中的有符号值。
- 方案每套 1–200 阶段，序号从 0 递增；TIME 为秒、DISTANCE 为米、STROKES 为次数；阻力为 0–100% 或空值，按器材档位范围换算。
- 跳绳总数、中断数和哑铃动作次数按本次有效训练累计；连续跳绳和重量保留当前设备读数。`device_duration_sec` 保留设备时长；`dumbbell_few_actions`、`dumbbell_action_number` 原样保存官方实时帧中的字段，不推测其动作名称。
- 设备名、方案 ID／名称等文本用一个前导单引号避免表格公式执行；导入仅去掉一个该前缀，因此原有单引号可往返保留。

### 大小和统计约定

完整备份最多 2 GiB 解压内容、20000 个条目；普通 CSV 单元格上限 1 MiB、整行 3 MiB，具体字段另有限制：方案快照 512 KiB、能力快照 4096 字符、归属历史 256 KiB。导出每块最多 32 会话、2000 采样、100 方案或 8 用户。会话／采样按行导入，方案 CSV 限 32 MiB／20 万行。独立 CSV 和报告导出同样分页读取，导出前冻结用户 ID 范围、器材、来源、日期、语言和单位，文件选择器返回后不重新读取当前人。

采样必须引用已有或同批会话，时间不得超出会话有效长度。校验、空间不足或提交前取消会保留原数据；提交阶段禁用取消，提交后的偏好恢复使用持久日志。不要手动删除正在读取的备份。

统计平均值按有效采样时间加权，单个读数最多覆盖前 5 秒。心率 0 为缺测，零速度／功率有效；曲线最多绘制 240 点并保留端点与极值。日期按当前设备时区分组。统计 CSV 显示本地日期及 UTC 偏移、带单位列名和 `—` 缺测标记，不用于原始数据往返。

偏好 v2 区分个人与设备共用域。完整备份始终包含各档案个人设置；可选共用部分为语言、身份确认策略、诊断开关和常用设备。个人收藏、快捷档位、外观、单位、指标与悬浮位置分别存储。旧 v1 的五组设置仍可读取，个人内容迁到原有用户，旧全局提示仅归档。系统权限、蓝牙配对和绝对屏幕坐标不迁移。恢复常用设备后须主动连接验证。

统计仅输出器材适用指标；有效零值保留、缺测显示 `—`、不适用列留空。划船 500 米配速用有效时长与匹配速度积分的距离计算，最快值取最小配速。稀疏采样保留缺口，不连成虚假的连续曲线。

## English

### Which export should I use?

- **Export summary CSV:** for reading in a spreadsheet. One workout per row, including date, equipment, duration, distance, energy, averages/peaks and data sources. Localized headings include units. Exports the current filter or a single workout from its details.
- **Export full backup:** a ZIP for moving devices or reinstalling, containing all profiles (including removed profiles), avatars, personal settings, sessions, samples and custom plans. Shared device settings are optional.
- **Raw CSV:** for data analysis and exchange. Summary CSV cannot be imported as raw data.

Import shows a preview first. Identical records are skipped; different content with the same ID cancels the whole import without overwriting local data. Overwriting existing profile details and personal settings is off by default. Local removal state wins; restoring never switches the active user. Shared settings are selected separately. Keep original files before uninstalling.

### Current raw format

UTF-8 with a BOM on export, comma-separated, CSV quote escaping, CRLF on export and LF accepted on import. Empty cells mean unknown, not zero. Raw column names do not depend on interface language.

| File | Current schema | Contents |
| --- | --- | --- |
| `sessions.csv` | 6 | Workout summaries and estimate sources |
| `samples.csv` | 6 | Samples on active workout time, usually around 1 Hz |
| `workouts.csv` | 2 | One stage per row |
| `manifest.txt` | 8 | Literal `OpenMOBI backup 8\n` |
| `preferences.json` | 2 | Optional, grouped local preferences |

Version 8 ZIPs use sequential chunks such as `sessions/0.csv`, `samples/0.csv` and `workouts/0.csv`, retaining the CSV schemas above. Export reads database pages; import streams to private temporary files and validates in a separate database before merging transactionally. Entry paths are allowlisted identifiers, never extraction destinations.

Backups 1–7, sessions 1/2/4/5, samples 1/2/3 and workouts 1 remain readable. Room 8 migrates versions 1–7: old plans and personal settings move to a stable legacy profile; old sessions remain unassigned. Old hints are archived, not activated. Interrupted preference commits recover from a durable journal. A legacy file without ownership cannot erase a corrected local owner. The CSV headers above apply in every language.

- Session IDs are UUIDs; start/end use ISO 8601 UTC and IANA time-zone names. Active duration excludes pauses. Distance/strokes are relative to this workout's active time.
- Machines: ELLIPTICAL / BIKE / ROWER / TREADMILL / HEART / UNKNOWN / JUMP_ROPE / DUMBBELL. Protocols: V1 / V2 / FTMS / HUANTONG / DEMO / UNKNOWN.
- Status: active / completed / interrupted / stopped. Recovered/imported active records become interrupted; they never start a workout automatically.
- `energy_model`: device / legacy-v1 / legacy-mobi / legacy-rowing / met / mixed, or empty for older data. Estimate flags are true/false.
- `archived` preserves legacy round trips only. There is no archive action in the current UI; normal queries include every flag value.
- The historical `cadence_rpm` name also represents rowing cadence, step rate on some treadmills, or jump-rope frequency. Interpret it with the machine type.
- Measured power accepts −32768–32767 W; estimated power accepts 0–50000 W so model boundary values can round-trip. This is not a normal exercise range. Force also preserves signed protocol values.
- Plans have 1–200 stages with indices starting at zero. TIME is seconds, DISTANCE meters and STROKES counts. Resistance is 0–100% or empty, mapped to equipment levels.
- Jump counts, interruption counts and dumbbell repetitions are session-relative. Continuous jumps and load retain current device readings. `device_duration_sec` preserves device time; `dumbbell_few_actions` and `dumbbell_action_number` preserve the corresponding vendor fields without inventing exercise names.
- Device names, plan IDs/titles and similar text receive one leading apostrophe to prevent spreadsheet formula execution. Import removes exactly one, preserving original apostrophes.

### Limits and statistics

Full backups support 2 GiB uncompressed data and 20000 entries. CSV cells are bounded to 1 MiB and rows to 3 MiB; plan snapshots allow 512 KiB, capability snapshots 4096 characters and owner history 256 KiB. Chunks contain 32 sessions, 2000 samples, 100 plans or 8 profiles. Session/sample import streams rows; plan files retain the 32 MiB/200000-row limit. Standalone exports are paged too. Owner IDs, filters, language and units are frozen before the system file picker.

`users/N.csv` schema 1 includes UUID, name, optional weight, MET/source, avatar, creation/removal/deletion state, personal preferences and archived hints. Avatars use hash-addressed 512×512 JPEG files under `avatars/`, with a 2 MiB resource limit. External content URIs are never backed up. Broken owner/start-user/plan-owner/history references reject the entire import.

Session ownership may change; start identity, body parameters and historical targets remain immutable. Plan snapshots contain workouts v2 CSV. Entered stage IDs form a semicolon-separated prefix of the snapshot stages. Capability JSON v1 preserves machine/model/protocol/evidence, three-state write/feedback support, units, observed metrics and resistance range. It never grants new control capabilities. Owner history contains at most 1000 UUID/time/from/to changes. Legacy hint JSON v1 is available only for explicit import.

Workouts are equipment-specific. Frequency and heart ranges independently follow plan defaults, use custom bounds or turn off. New recovery/cooldown stages start off. Treadmill speed/incline targets remain manual guidance.

Samples must reference an existing or same-import session and cannot exceed its active duration. Validation errors, insufficient space and cancellation before commit preserve existing records. Cancellation is disabled during finalization; a durable journal recovers preferences after commit.

Averages are time-weighted, with each sample covering at most the preceding five seconds. Zero heart rate is missing; zero speed/power is valid. Charts retain endpoints/extremes with at most 240 points. Dates use the current device time zone. Summary CSV uses local dates with UTC offsets, unit-bearing headings and `—` for missing data; it is not a round-trip backup format.

Preference schema 2 separates personal settings from optional shared language, identity policy, diagnostics and saved devices. Personal favorites, quick levels, appearance, units, metric choices and relative floating positions are always included with their profile. Legacy schema 1 groups remain readable; personal values migrate to the original profile and old hints are archived. Permissions, Bluetooth pairing and absolute display coordinates are excluded. Saved devices still require manual connection.

Reports retain every applicable metric with valid data. Zero is valid where supported; missing data uses `—`, and inapplicable CSV fields are empty. Rowing pace uses matching valid time and speed-integrated distance; fastest pace is the minimum. Sparse samples preserve chart gaps.
