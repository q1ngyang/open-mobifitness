# 训练与数据说明 / Workouts and readings

[首页](../README.md) · [English](#english)

## 选择训练方案

内置 21 套离线方案，包含热身、恢复、稳态、耐力、渐进和多种间歇训练。可以搜索、按时长等条件筛选、收藏，或复制后修改。它们是 OpenMOBI 制作的方案，不是已停服的官方在线课程。

| 方案示例 | 时长 | 安排 |
| --- | --- | --- |
| 轻度训练 | 20 分钟 | 热身 5 分钟，低阻力 10 分钟，冷身 5 分钟 |
| 中度训练 | 30 分钟 | 热身 5 分钟，持续训练 20 分钟，冷身 5 分钟 |
| 有氧进阶 | 40 分钟 | 10% → 35% → 50% → 60% → 45% → 10%，每段分别 5／5／10／10／5／5 分钟 |
| HIIT 递进 | 30 分钟 | 热身与冷身各 5 分钟，中间 10 组「工作 1 分钟＋恢复 1 分钟」；工作段目标逐步变化 |
| HIIT 长间歇 | 40 分钟 | 热身后进入较长工作段，每组 3 分钟工作、2 分钟恢复，最后降低阻力冷身 |

阻力百分比按官方 App 的方式计算：`档位 ÷ 最高档位 × 100%`，显示时四舍五入。24 档设备的 1 档是 4%、12 档是 50%。方案会换算到器材支持的实际档位，并保留最低档；这不是心率或个人运动强度百分比。

方案中的热量只作参考。例如 40 分钟有氧进阶以 70 kg、平均 6–8 MET 估算约 294–392 kcal，不能保证每个人消耗 300–400 kcal。实际记录不会为了符合这个参考值而提高热量。可以按体感调低阻力、暂停或复制修改方案。

## 哪些指标可以显示？

在「显示设置」选择和排序：主运动页最多 12 项，小窗 2 项，大窗 4 项，分别保存。全部可选项有时长、距离、热量、心率、速度、平均速度、阻力、频率、桨频、划桨次数、拉力、坡度、步频、步幅、目标桨频、功率、500 米配速。

能否显示取决于器材有没有对应数据。缺失显示 `—`；`≈` 表示估算，不是传感器实测。

| 指标 | 主要来源 |
| --- | --- |
| 时长 | 本地有效训练时间；暂停与断连暂停不累计 |
| 频率 | 器材反馈，按类型使用 rpm／spm |
| 阻力 | 器材回报的档位，不把尚未确认的请求当成当前档位 |
| 心率 | 器材或独立蓝牙心率设备；有新数据时优先使用独立设备，过期数据不继续显示 |
| 功率 | 支持的 V2／FTMS 设备上报，或已知 V1 单车／椭圆机的原厂模型估算 |
| 距离、速度 | 设备读数；缺少距离时可能由速度积分估算，旧 V1 使用原厂虚拟速度模型 |
| 热量 | 优先设备累计值；其次为已知 V1 的功率＋体重模型，其他设备使用体重＋MET 备用估算 |
| 桨数、步频、步幅、坡度、拉力 | 仅显示已支持且数据可用的字段；未知机型不套用其他型号的换算 |

旧 V1 椭圆机的速度为 `rpm ÷ 2.68 ÷ 4` km/h，单车不除以 4。它是虚拟运动距离，并非实际向前移动的距离。依靠距离／桨数结束的阶段需要器材累计读数，不能用虚拟估算替代。

## 为什么功率、热量可能与体感不同？

V1 功率采用官方旧版的数学模型，不是功率计读数，部分高档位会算出较高结果。已知 V1 的热量按功率和体重累计，MET 不影响这一路径；其他无热量读数的器材才用固定 MET 估算。

请在「设置 → 热量估算」填写自己的体重。新参数从下一次训练生效；旧记录不会重新计算。默认体重 70 kg，备用 MET 为 5。要和官方 App 比较，需要相同体重、实际档位与频率，单看一个瞬间不容易判断。

详细公式与复核依据见[算法说明](ALGORITHM_AUDIT.md)。尚未完成仪器功率标定或个人热量测量。

## English

### Choose a plan

There are 21 offline plans covering warm-up, recovery, steady work, endurance, progressive work and several interval formats. Search, filter by duration and other options, save favorites, or copy and edit a plan. These are OpenMOBI plans, not recovered official online courses.

| Example | Duration | Structure |
| --- | --- | --- |
| Light workout | 20 min | 5 min warm-up, 10 min low resistance, 5 min cool-down |
| Moderate workout | 30 min | 5 min warm-up, 20 min steady work, 5 min cool-down |
| Advanced aerobic | 40 min | 10% → 35% → 50% → 60% → 45% → 10%, lasting 5/5/10/10/5/5 minutes |
| Progressive HIIT | 30 min | 5 min warm-up and cool-down, with ten 1-minute work/1-minute recovery pairs; work targets vary progressively |
| Long-interval HIIT | 40 min | Warm-up, longer work intervals of 3 min with 2 min recovery, then easier work and cool-down |

Resistance percentage follows the original app: `level ÷ maximum level × 100%`, rounded for display. On 24-level equipment, level 1 is 4% and level 12 is 50%. Plan targets map to supported levels and never fall below the equipment minimum. This is not heart-rate or personal exercise-intensity percentage.

Plan energy figures are only references. The 40-minute aerobic plan assumes 70 kg and an average of 6–8 MET for roughly 294–392 kcal, not a guaranteed 300–400 kcal for everyone. Recorded energy is not increased to match this reference. Lower resistance, pause, or copy and edit a plan to suit your effort.

### Available metrics

Use Display settings to choose and order up to 12 workout-screen metrics, 2 compact-panel metrics and 4 expanded-panel metrics, saved separately. Choices include duration, distance, energy, heart rate, speed, average speed, resistance, cadence, stroke rate, stroke count, force, incline, step rate, stride length, target stroke rate, power and 500 m pace.

Availability depends on equipment data. `—` means unavailable; `≈` means estimated rather than directly measured.

| Metric | Main source |
| --- | --- |
| Duration | Local active workout time; excludes pauses and disconnection pauses |
| Cadence | Equipment feedback, using rpm/spm as appropriate |
| Resistance | Reported level; unconfirmed requests are not displayed as current feedback |
| Heart rate | Equipment or a separate BLE accessory; fresh accessory readings take priority, stale values are cleared |
| Power | Supported V2/FTMS readings or the original model for known V1 bikes/ellipticals |
| Distance/speed | Equipment readings; distance may be integrated from speed. Older V1 uses the original virtual-speed model |
| Energy | Equipment total first, then known V1 power-and-weight calculation, otherwise weight-and-MET estimation |
| Stroke/step data, stride, incline and force | Only implemented fields with available data; unknown models do not inherit another model's calibration |

Older V1 ellipticals use `rpm ÷ 2.68 ÷ 4` km/h; bikes omit the division by four. This represents virtual distance, not ground travel. Distance/stroke-ended stages require measured cumulative readings, not virtual estimates.

### Why might power or energy feel too high?

V1 power comes from the original app's mathematical model, not a power meter. It can produce high values at high resistance. Known V1 energy uses power and body weight; MET does not affect it. Other devices without energy readings use the fixed-MET fallback.

Set your weight in Settings → Energy estimates. Changes apply to the next workout and do not recalculate history. Defaults are 70 kg and 5 MET for the fallback. Compare with the original app using the same weight, actual level and cadence; a single moment is rarely enough.

See [calculation details](ALGORITHM_AUDIT.md#english). These estimates have not been calibrated against measured mechanical power or personal energy expenditure.
