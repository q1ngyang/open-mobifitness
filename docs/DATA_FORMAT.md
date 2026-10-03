# CSV 与备份格式 / CSV and backup formats

[文档目录](README.md) · [English](#english)

## 普通用户怎么选？

- **导出统计 CSV**：用 Excel 等软件阅读，一次运动一行，有日期、器材、时长、距离、热量、平均／峰值与数据来源。列名含单位，跟随 App 语言。导出当前筛选结果，或详情中的单次训练。
- **导出完整备份**：换设备或重装时使用 ZIP，包含训练记录、逐秒采样和自定义方案，可选择一并保存偏好。
- **原始 CSV**：供需要进一步分析或交换数据的人使用。统计 CSV 不能当作原始 CSV 导入。

导入会先显示预览。完全相同的记录跳过；同一 ID 的内容冲突会取消整次导入，不覆盖本机内容。导入时可按分组选择覆盖哪些偏好，默认不覆盖。卸载前请保留好原文件。

## 当前原始格式

UTF-8（导出含 BOM），逗号分隔，按 CSV 规则转义双引号，导出 CRLF、导入也接受 LF。空单元格表示未知，不等于零。字段名不随界面语言改变。

| 文件 | 当前版本 | 说明 |
| --- | --- | --- |
| `sessions.csv` | 5 | 每次训练的摘要和估算来源 |
| `samples.csv` | 6 | 有效训练时间上的采样，通常约 1 Hz |
| `workouts.csv` | 1 | 每行一个训练阶段 |
| `manifest.txt` | 7 | 内容为 `OpenMOBI backup 7\n` |
| `preferences.json` | 1 | 可选，按分组迁移本机偏好 |

v7 ZIP 使用 `sessions/0.csv`、`samples/0.csv`、`workouts/0.csv` 等从 0 连续编号的 CSV 分块；各块使用上述相同表头及 CSV 版本。导出分页读取，导入先流式复制到应用私有暂存目录，用独立数据库完成校验，再以事务合并。文件路径只作白名单标识，不能指定解压目标。

继续支持备份 v1–v6、会话 v1/v2/v4、采样 v1/v2/v3，缺失字段使用旧版兼容默认值。本地数据库为 Room v7，v1–v6 迁移保留历史数据。v7 只新增偏好恢复日志表，记录格式没有随 ZIP 版本一起递增；提交数据库后若偏好写入中断，下次启动会重试该日志。

### 字段

`sessions.csv`：

```csv
schema,session_id,start_utc,end_utc,time_zone,device,machine,protocol,elapsed_ms,distance_m,simulated,status,calories_kcal,calories_estimated,distance_estimated,weight_kg,met,workout_id,workout_title,archived,energy_model
```

`samples.csv`：

```csv
schema,session_id,elapsed_ms,cadence_rpm,resistance,speed_mps,distance_m,heart_bpm,power_w,strokes,calories_kcal,incline_percent,stride_m,force_n,step_rate,step_count,target_cadence,power_estimated,jump_count,continuous_jumps,jump_interruptions,repetitions,load_kg,device_duration_sec,dumbbell_few_actions,dumbbell_action_number
```

`workouts.csv`：

```csv
schema,workout_id,title,step_index,condition,target,resistance_percent
```

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

完整备份导入／导出支持最多 2 GiB 解压后内容、20000 个条目，单字段最多 4096 字符。会话与采样按每块至多 2000 行导出；方案按每块至多 100 套导出。导入会话和采样按行读取，不再受旧版整表 20 万行限制。旧版单个方案 CSV 仍限 32 MiB／20 万行；超范围明确失败。原始 CSV 的其他单独导出工具仍沿用原来的内存路径，大量历史请使用完整备份。

采样必须引用已有或同批会话，时间不得超出会话有效长度。校验、空间不足或提交前取消会保留原数据；提交阶段禁用取消，提交后的偏好恢复使用持久日志。不要手动删除正在读取的备份。

统计平均值按有效采样时间加权，单个读数最多覆盖前 5 秒。心率 0 为缺测，零速度／功率有效；曲线最多绘制 240 点并保留端点与极值。日期按当前设备时区分组。统计 CSV 显示本地日期及 UTC 偏移、带单位列名和 `—` 缺测标记，不用于原始数据往返。

偏好清单有独立版本 1，并按五组恢复：外观与单位（含原有体重、MET、目标桨频）、指标与收藏、个人提示、常用设备与档位、悬浮窗。只替换用户选中的组；旧备份没有偏好清单时不改变偏好。系统权限、蓝牙配对、日志开关和旧设备绝对坐标不迁移；悬浮位置保存为横／竖屏各自的归一化坐标，在目标窗口重新限制边界。恢复常用设备后仍须主动连接验证。官方 App 缓存及停服后的云端记录不属于此格式。

## English

### Which export should I use?

- **Export summary CSV:** for reading in a spreadsheet. One workout per row, including date, equipment, duration, distance, energy, averages/peaks and data sources. Localized headings include units. Exports the current filter or a single workout from its details.
- **Export full backup:** a ZIP for moving devices or reinstalling, containing sessions, samples and custom plans, with optional preferences.
- **Raw CSV:** for data analysis and exchange. Summary CSV cannot be imported as raw data.

Import shows a preview first. Identical records are skipped; different content with the same ID cancels the whole import without overwriting local data. Import lets you choose preference groups to replace; none are selected by default. Keep original files before uninstalling.

### Current raw format

UTF-8 with a BOM on export, comma-separated, CSV quote escaping, CRLF on export and LF accepted on import. Empty cells mean unknown, not zero. Raw column names do not depend on interface language.

| File | Current schema | Contents |
| --- | --- | --- |
| `sessions.csv` | 5 | Workout summaries and estimate sources |
| `samples.csv` | 6 | Samples on active workout time, usually around 1 Hz |
| `workouts.csv` | 1 | One stage per row |
| `manifest.txt` | 7 | Literal `OpenMOBI backup 7\n` |
| `preferences.json` | 1 | Optional, grouped local preferences |

Version 7 ZIPs use sequential chunks such as `sessions/0.csv`, `samples/0.csv` and `workouts/0.csv`, retaining the CSV schemas above. Export reads database pages; import streams to private temporary files and validates in a separate database before merging transactionally. Entry paths are allowlisted identifiers, never extraction destinations.

Backup versions 1–6, session schemas 1/2/4 and sample schemas 1/2/3 remain readable with compatibility defaults. Room v7 migrates v1–v6 without replacing historical values. Its only new table journals preference recovery after the record transaction commits. A later launch retries an interrupted preference write. CSV headers in the Chinese section are identical in every language.

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

Full backups support up to 2 GiB of uncompressed content, 20000 entries and 4096 characters per field. Export chunks contain up to 2000 sessions/samples or 100 plans. Session/sample import streams rows and no longer has the old 200000-row limit. A legacy single plan CSV retains its 32 MiB/200000-row limit; exceeding it fails explicitly. Other standalone raw-CSV export tools retain their original in-memory path; use full backups for large histories.

Samples must reference an existing or same-import session and cannot exceed its active duration. Validation errors, insufficient space and cancellation before commit preserve existing records. Cancellation is disabled during finalization; a durable journal recovers preferences after commit.

Averages are time-weighted, with each sample covering at most the preceding five seconds. Zero heart rate is missing; zero speed/power is valid. Charts retain endpoints/extremes with at most 240 points. Dates use the current device time zone. Summary CSV uses local dates with UTC offsets, unit-bearing headings and `—` for missing data; it is not a round-trip backup format.

Preference schema 1 has five groups: appearance/units (including existing weight, MET and target rowing cadence), metrics/favorites, personal hints, saved devices/presets, and floating panels. Only selected groups are replaced. Older backups without preferences leave settings intact. System permissions, Bluetooth pairing, logging options and absolute screen coordinates are excluded. Relative portrait/landscape panel positions are constrained to the new window. Saved devices still require a manual connection check. Original-app caches and unavailable cloud history are outside this format.
