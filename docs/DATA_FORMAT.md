# OpenMOBI 交换格式 v1

UTF-8（导出含 BOM），逗号分隔，双引号按 RFC 4180 规则转义；导出使用 CRLF，导入兼容 LF。字段名与界面语言无关。版本字段 `schema` 为 `1`。

## sessions.csv

`schema,session_id,start_utc,end_utc,time_zone,device,machine,protocol,elapsed_ms,distance_m,simulated,status`

- `session_id` 是 UUID；时间为 ISO 8601 UTC，时区是 IANA 时区标识。
- `machine` 为 ELLIPTICAL / BIKE / ROWER / TREADMILL / HEART / UNKNOWN。
- `protocol` 为 V1 / V2 / FTMS / HUANTONG / DEMO / UNKNOWN。
- `elapsed_ms` 是累计有效运动时间，暂停不计入；距离单位米。
- `simulated` 为 true / false，演示记录始终明确标注。
- `status` 为 active / completed / interrupted / stopped；恢复或导入活动记录会标为 interrupted，不自动开始运动。

## samples.csv

`schema,session_id,elapsed_ms,cadence_rpm,resistance,speed_mps,distance_m,heart_bpm,power_w,strokes`

会话 UUID 与有效运动时间组成唯一键。采样正常频率约 1 Hz，后台调度延迟可能形成间隔，不插值伪造测量。距离／桨数在记录中相对当前训练计算，暂停期间的累计变化不用于完成训练阶段。未知指标为空。

`power_w` 按 FTMS 有符号功率保留，允许 −32768 至 32767 W；不会在 CSV 往返时丢弃负值。

## workouts.csv

`schema,workout_id,title,step_index,condition,target,resistance_percent`

同一训练的行连续排列，`step_index` 从 0 递增。`condition` 为 TIME（秒）、DISTANCE（米）、STROKES（次数）。`resistance_percent` 为 0–100 或空值；它映射到设备报告或已知 profile 的阻力范围及增量，不代表功率百分比。每套训练 1–200 阶段。

文本字段 `device`、`title` 总是增加一个前导单引号，以避免电子表格把内容解释为公式。OpenMOBI 导入去掉恰好一个该前缀；原文本本身以单引号开头时可以正确往返。手工制作 CSV 可采用同一约定。

## ZIP 备份与事务

备份包含 `manifest.txt`（内容为 `OpenMobi backup 1\n`）和以上三份 CSV。解析在内存完成，不把 ZIP 路径解压到文件系统。

导入先预览，再事务提交。相同 ID、相同内容跳过；冲突取消整次导入。采样必须引用已有或同次导入的会话，时间不能超出会话长度。错误提示尽可能提供 CSV 行号。

当前解析器限制单文件／解压后总内容 32 MiB、单 CSV 200,000 数据行、单字段 4,096 字符，防止异常输入耗尽手机内存。单次运动详情中可以单独导出该次采样或完整备份，适合分批归档。首个 alpha 的超大归档仍需进一步实现流式分页；不要删除原始备份。官方旧 App 的私有缓存和已关闭云端记录不在此格式承诺内。

## Schema 2（alpha.2）

导出的 sessions.csv 和 samples.csv 使用 schema=2；仍接受 schema=1 的原有列头。workouts.csv 继续使用 schema=1。ZIP manifest version=2，读取器同时接受 version=1 和 2。

`sessions.csv` 在旧字段后增加：`calories_kcal,calories_estimated,distance_estimated,weight_kg,met`。布尔值为 true／false；空读数仍为空单元格，不等于零。热量与距离估算标记指本次累计中含有估算部分。

`samples.csv` 增加：`calories_kcal,incline_percent,stride_m,force_n,step_rate,step_count,target_cadence`。距离、热量和桨数均为本次有效训练的相对累计值。沿用的 cadence_rpm 字段在划船机上表示桨频，在相应跑步机协议中表示步频；请结合 session.machine 解读，不能跨器材直接比较。force_n 与 power_w 允许设备协议中的有符号值。

本地 Room 数据库从版本 1 无损迁移到 2，旧记录的新读数为 null、估算标记为 false，不回填虚构热量。升级与双版本导入均有自动化测试。旧版 OpenMOBI 无法读取 schema=2 导出；请用 alpha.2 或更新版。

## alpha.4：样本 v3 与备份 v3

摘要仍用 v2，训练方案仍用 v1。样本 CSV 的 `schema` 改为 `3`，在 v2 字段末尾追加 `power_estimated`（true / false）。它标记 `power_w` 是否由旧版计算模型估算，随 Room v3、CSV 与 ZIP 备份保存。V1 支持的椭圆机／单车使用实际回传踏频与档位计算，不使用待确认的调阻目标；设备直接上报的功率为 false。设备测量值仍限制为 FTMS 的有符号范围；v3 中标记为估算的功率允许 0–50,000 W，以保证模型在最高可解析频率边界的导出也可原样导入，这不是正常运动功率范围。

v1 / v2 样本仍可导入，缺失标记按 false 读取。备份清单现在是 `OpenMOBI backup 3`，仍接受历史 `OpenMobi backup 1` 和 `OpenMobi backup 2`。数据库从 v1 或 v2 升级均保留原训练和样本；仅新增默认 false 的功率来源字段。

v3 样本和备份需用 alpha.4 或更新版读取；跨设备传输时，两端应使用支持该格式的版本。
