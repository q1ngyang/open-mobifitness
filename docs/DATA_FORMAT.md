# OpenMobi 交换格式 v1

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

文本字段 `device`、`title` 总是增加一个前导单引号，以避免电子表格把内容解释为公式。OpenMobi 导入去掉恰好一个该前缀；原文本本身以单引号开头时可以正确往返。手工制作 CSV 可采用同一约定。

## ZIP 备份与事务

备份包含 `manifest.txt`（内容为 `OpenMobi backup 1\n`）和以上三份 CSV。解析在内存完成，不把 ZIP 路径解压到文件系统。

导入先预览，再事务提交。相同 ID、相同内容跳过；冲突取消整次导入。采样必须引用已有或同次导入的会话，时间不能超出会话长度。错误提示尽可能提供 CSV 行号。

当前解析器限制单文件／解压后总内容 32 MiB、单 CSV 200,000 数据行、单字段 4,096 字符，防止异常输入耗尽手机内存。单次运动详情中可以单独导出该次采样或完整备份，适合分批归档。首个 alpha 的超大归档仍需进一步实现流式分页；不要删除原始备份。官方旧 App 的私有缓存和已关闭云端记录不在此格式承诺内。
