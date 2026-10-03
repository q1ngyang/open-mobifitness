# 功率与热量计算 / Power and energy calculations

[训练说明](TRAINING.md) · [English](#english)

## 结论

V1、V2 脉冲模式及 HuanTong 单车／椭圆机使用国际版 2.1.14 的默认功率和热量模型，参考中国版 4.5.16.1 交叉核对。**实现与旧算法一致，不代表数值已经过功率计或人体能耗测试。** 原 APK 与完整反编译源码不随仓库发布。

国际版默认使用 type-0；中国版默认也是 type-0，但存在可配置的 type-1，不能假设所有中国版安装状态都相同。支持的 V2／FTMS 直接功率读数不使用 V1 模型。

## V1 功率

type-0 使用六系数指数多项式，变量为 `x = exp(rpm / 100)`、`y = exp(level × 32 / maximum / 10)`，结果不小于零。0B11 椭圆机额外乘以 1.5，最后截断为整数瓦显示。当前实现的系数、最高档位 24、型号修正和取整已与原调用链及 Smali 核对。

V1 毫秒周期换算为 `60000 / interval`；0B11 每圈两个脉冲，再除以 2。以下是合成输入的计算结果，不是实测功率：

| 0B11 输入 | 1 档 | 12 档 | 24 档 |
| --- | ---: | ---: | ---: |
| 1000 ms → 30 rpm | 20 W | 85 W | 211 W |
| 500 ms → 60 rpm | 66 W | 235 W | 900 W |

原模型在高档位会给出较高数值。没有实测校准依据时，不任意乘降低系数，界面继续标记 `≈`。

已恢复官方的计算档位过渡：`MotionData.getAvgResistanceInQueue` 返回的是内部计算档位，不是刚发送的调阻请求。每个有效秒按 `max(floor(最高档位 / 10), 1)` 向反馈档位移动；显示功率在新脉冲到达时更新，热量使用该秒未截断的模型功率。HuanTong 没有档位回报，按原实现使用周期发送的设定值，并在界面标明来源。

## V1 热量

使用未截断的模型功率，按有效运动时间累加：

```text
功率为 0 时，不增加热量
否则：kcal/s = 体重 kg / 3600 + 功率 W / 1000
```

旧 App 在没有用户体重时回退 60 kg。OpenMOBI 保留用户设置，未设置时为 70 kg，升级不会暗中更改体重。70 kg、60 rpm、1 档时约为 5.13 kcal/分钟；固定 5 MET 约为 6.125 kcal/分钟。高档位下 V1 模型也可能高于固定 MET，不能保证所有情况都降低。

优先顺序：设备累计热量 → 官方划船热量或 V1／V2 脉冲／HuanTong 模型 → 其他设备 MET 备用模型。已知 V1 缺少参数时不临时切换 MET。暂停、断连或缺失数据不补算，长采样间隔最多外推 5 秒。历史记录保留原值，新记录保存计算来源。

## 划船与对照验证

已恢复 RowingAnalysis 与 RowingAnalysis3209 的状态、速度、功率、拉力和热量规则，包括旧水阻／磁阻入口。2,363 个连续输入的 19 个输出已与两版原 DEX 对照。默认 type-0 及可选 type-1 数学函数也有原 DEX 对照；当前训练仍使用默认 type-0。详见[验证说明](PROTOCOL_VERIFICATION.md)和[重现工具](../tools/protocol-oracle/README.md)。

## 备用 MET 模型与统计

备用公式为 `MET × 3.5 × 体重 kg ÷ 200 × 分钟`，仅在有效训练且有运动反馈时累计。默认 5 MET 参考 [2024 成人活动代谢当量汇编的椭圆机项目](https://pacompendium.com/conditioning-exercise/)。这是含静息部分的总能量近似，固定 MET 不会精确反映每个人的用力。

平均值按有效采样时间加权，每个读数最多覆盖前 5 秒。心率 0 作为缺测，有效的零速度／零功率计入平均；图表保留端点与极值，并限制绘制点数。

`LegacyPowerTest` 和 `TelemetryTest` 检查参考功率、实际与请求档位分离、无效输入、暂停、缺包、零频率、设备值优先与导入兼容。要继续核验实机，请提供相同体重、实际档位和频率下的记录及日志。

## English

### Findings

V1, V2 pulse-mode and HuanTong bikes/ellipticals use the default power and energy model from international app 2.1.14, cross-checked against Chinese app 4.5.16.1. **Matching the old algorithm does not establish accuracy against a power meter or measured personal energy expenditure.** Official APKs and complete decompiled source are not distributed.

The international app uses type-0 by default. The Chinese app also defaults to type-0 but has a configurable type-1 path, so installations can differ. Supported V2/FTMS direct power readings bypass the V1 model.

### V1 power

Type-0 is a six-coefficient exponential polynomial with `x = exp(rpm / 100)` and `y = exp(level × 32 / maximum / 10)`, clamped to nonnegative output. The 0B11 elliptical adds a 1.5 multiplier; display power is truncated to integer watts. Coefficients, the 24-level maximum, model correction and truncation were checked against the original call chain and Smali.

V1 milliseconds per pulse convert to `60000 / interval`; 0B11 divides by two for two pulses per revolution. These are synthetic reference inputs, not measured power:

| 0B11 input | Level 1 | Level 12 | Level 24 |
| --- | ---: | ---: | ---: |
| 1000 ms → 30 rpm | 20 W | 85 W | 211 W |
| 500 ms → 60 rpm | 66 W | 235 W | 900 W |

The original model can produce high values at high resistance. Without calibration evidence, no arbitrary reduction factor is applied; `≈` remains visible.

The original calculation-resistance transition is restored. `MotionData.getAvgResistanceInQueue` returns an internal calculation level, not a newly requested target. Each active second moves it toward feedback by `max(floor(maximum / 10), 1)`. New pulses update displayed power; energy uses untruncated model watts for that second. HuanTong has no resistance feedback, so its estimate uses the periodically commanded level, labeled accordingly.

### V1 energy

Untruncated model power is accumulated over active workout time:

```text
When power is zero, energy does not increase.
Otherwise: kcal/s = weight_kg / 3600 + power_W / 1000
```

The original app falls back to 60 kg when no user weight exists. OpenMOBI preserves the user's setting, defaulting to 70 kg; updates do not silently change it. At 70 kg, 60 rpm and level 1, the result is about 5.13 kcal/min, versus 6.125 kcal/min with fixed 5 MET. At high resistance the V1 model can exceed fixed-MET energy, so values do not always decrease.

Priority is equipment energy → official rowing or V1/V2 pulse/HuanTong model → MET fallback for other devices. Missing V1 parameters do not cause a temporary switch to MET. Pauses, disconnections and missing data are not filled in; a long sample interval is extrapolated for at most five seconds. Existing history keeps its original values; new records preserve the calculation source.

### Rowing and executable references

RowingAnalysis and RowingAnalysis3209 state, speed, power, force and energy rules were restored, including old water/magnetic inputs. Both original APKs match 19 outputs across 2,363 sequential inputs. Default type-0 and optional type-1 math also have original-DEX comparisons; live workouts retain default type-0. See [verification](PROTOCOL_VERIFICATION.md) and the [reproducible harness](../tools/protocol-oracle/README.md).

### MET fallback and statistics

The fallback is `MET × 3.5 × weight_kg ÷ 200 × minutes`, accumulated only during active workouts with movement feedback. The 5 MET default references the elliptical entry in the [2024 Adult Compendium](https://pacompendium.com/conditioning-exercise/). It approximates total energy, including resting expenditure; fixed MET cannot precisely reflect an individual's effort.

Averages are weighted by valid sample time, with each sample covering at most the preceding five seconds. Zero heart rate means missing data; valid zero speed/power is included. Charts retain endpoints and extremes while limiting plotted points.

`LegacyPowerTest` and `TelemetryTest` cover reference power, actual versus requested resistance, invalid inputs, pauses, packet gaps, zero cadence, direct-reading priority and import compatibility. Further hardware comparisons require the same body weight, actual resistance and cadence, with records and diagnostic logs.


## 本次完整处理器补充

除 FTMS flags 字段外，现已直接比较原 `FtmsHandler` 的四种器材回调：划船速度在换算后取一位小数，椭圆机阻力／坡度转整数，跑步机使用原频率及步幅换算。设备未提供功率时，已识别的 MOBI FTMS 接入原 MotionData 的计算阻力过渡及相应默认估算；通用 FTMS 保持标准字段。设备已上报的累计距离／热量仍优先使用。

这些换算复刻原 App 的计算行为，包括其部分单位约定；不是新的机械功率或人体热量标定。v0.1.1 V1 椭圆机在相同稳定输入下的功率／热量输入不变，见 [394 条历史帧与发布 APK 对比](V011_COMPATIBILITY_REVIEW.md)。
