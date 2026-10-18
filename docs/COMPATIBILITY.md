# 设备支持 / Device support

[首页](../README.md) · [English](#english)

## 安装要求

Android 10 或以上，64 位设备（arm64；另提供 x86_64 以便模拟器测试）。界面支持简体中文、繁体中文、英语、日语、韩语、德语。

## 哪些器材可以使用？

**已有用户实机证据包括 MB-EP 系列 V1 椭圆机，以及 Issue #1 的 AT-R66146 协议反馈。** “代码已支持”不等于每个型号都试过，连接成功也不等于已收到运动数据。请先确认数据更新与相邻档位调节。

| 器材或连接方式 | 当前情况 |
| --- | --- |
| 白色经典款 MB-EP 椭圆机，V1 | 根据用户实机日志修复数据接收和调阻，支持频率、阻力、心率及功率等估算；功率与热量未经仪器标定 |
| 其他 V1 单车／椭圆机 | 已恢复官方型号默认能力、脉冲解析与调阻；保留默认只读限制；AT-R 子型 1 按 Issue #1 开放 1–24 写入，设定档位与反馈分开 |
| Mobi V2 器材 | 已恢复官方识别、上传模式、解锁、数据解析和型号换算；本地原 DEX 对照完成 |
| FTMS 标准蓝牙器材 | 已区分原厂 MOBI 变体与通用 FTMS 的数据及控制流程，本地对照完成 |
| 旧款 V1 划船机、HuanTong 器材 | 已恢复旧划船计算、HuanTong 体重初始化与每秒阻力通信；本地对照完成 |
| V2 跳绳／哑铃 | 已恢复实时次数、跳频／连续／中断、哑铃重量与记录保存，本地对照完成 |
| 跑步机 | 仅接收已支持的数据，不启动电机，也不控制速度或坡度 |
| 器材一键展开／收起 | 连接页仅对声明支持的器材显示；闲置且状态已知时确认后发送，等待状态回报，超时仅提供刷新；机械动作未实测 |
| 独立蓝牙心率带 | 支持标准 BLE 心率服务；需设备广播该服务 |

连接椭圆机不需要另配心率带。器材若提供心率，会在设备卡片中注明来源。独立心率设备只在连接过程中或已连接时显示，未连接时不会多出一个“未连接”提示。

手机／平板横竖屏、深色模式和放大字体经过模拟器检查。折叠窗口经过布局测试，**尚未使用真实折叠屏设备验证**。不同厂商的后台限制也可能影响长时间悬浮训练。

国际版 2.1.14 与中国版 4.5.16.1 的原始 DEX 差分回归、型号表和已知边界见[协议验证说明](PROTOCOL_VERIFICATION.md)。已完成 [v0.1.1 V1 椭圆机兼容性复核](V011_COMPATIBILITY_REVIEW.md)。本阶段按原 APK 对照和本地验证初步完成，无需提供其他型号实机作为完成条件；硬件证据限于已有 MB-EP 日志和 Issue #1 的 AT-R 反馈。

2026-10-18 的[补充审计](REA_AUDIT.md)发现了原有限定对照未覆盖的行为缺口。v0.3.4 已修复显式零档反馈及估算输入、逐指标过期、AT-R 转数累计等缺口，见[发布验证](V034_VALIDATION.md)。不能由“本地对照完成”推断任意型号、固件或档位范围均可完整使用。

完整六字节 FTMS 范围保留最小值、最大值及小数步长；旧四字节 MOBI 格式保留厂商规则。MOBI 两字节阻力指令不能表达大于 25.5 的档位，这类范围保持只读。折叠状态 0／1 没有可确认的机械姿态标签，因此使用合并的“展开／收起”操作，不猜测方向。

## 第一次连接怎么确认？

轻踩或拉动几秒，观察频率／档位是否更新，再尝试相邻一档。不能调阻、数值不动、掉线时，请按[日志指引](HELP.md)反馈；无需自己判断协议。断连会暂停训练，重连后不会重放断线前的控制指令。

## 协议信息（供排查使用）

| 路径 | 识别与限制 |
| --- | --- |
| V1 / FFE0 | FFE3 写入、FFE4 通知；运动状态头为 AB 04。椭圆机子型 17/19 为 24 档、18 为 8 档；单车子型 18/21/23 为 24 档、19/20/22 为 32 档。0B11 每圈两次脉冲 |
| V2 / 8800 | 读取类别、阻力读写模式、范围和磁铁数；处理 8811/8812/8813/8814，88FF 解锁及 880F 调阻 |
| FTMS / 1826 | 处理 2ACE/2AD2/2AD1/2ACD；MOBI 旧变体使用原厂控制顺序／编码，通用 FTMS 订阅并等待控制响应 |
| HuanTong / FFF0 | FFF1 订阅、FFF2 体重初始化和每秒阻力帧；仅显示设定阻力，不冒充实际反馈 |
| 心率 / 180D | 8/16 位心率，独立 BLE 连接；新数据中断 10 秒后不再显示旧值为实时读数 |

自动调阻每两秒最多变化一个设备增量。有反馈设备的当前档位以反馈为准，反馈超时会提示未确认；HuanTong／AT-R 只写设备显示“设定阻力”，不将它记录成实测档位。没有有效累计距离／桨数时，不启动依赖这些读数的阶段。完整指标来源见[训练说明](TRAINING.md)。

## English

### Requirements

Android 10 or later on a 64-bit device (arm64; x86_64 is also included for emulators). Interface languages: Simplified Chinese, Traditional Chinese, English, Japanese, Korean and German.

### Which equipment works?

**Existing user hardware evidence covers V1 MB-EP ellipticals and the AT-R66146 protocol report in Issue #1.** Implemented code does not mean every model has been tested. A connection alone does not prove readings are arriving: check live readings and a one-level resistance change first.

| Equipment or connection | Current status |
| --- | --- |
| White classic MB-EP elliptical, V1 | Data reception and resistance fixes based on user logs; cadence, resistance, heart rate and model-based estimates supported. Power and energy have not been calibrated against instruments |
| Other V1 bikes/ellipticals | Official model capabilities, pulse parsing and resistance encoding recovered; default read-only limits remain; AT-R subtype 1 permits writes from 1–24 based on Issue #1, labeled separately from feedback |
| Mobi V2 | Official identification, upload modes, unlock, parsing and model scaling recovered and compared with original DEX. Local comparisons complete |
| Standard FTMS equipment | MOBI-specific and standard FTMS parsing/control paths implemented; local comparisons complete |
| Older V1 rowers and HuanTong equipment | Legacy rowing calculations and HuanTong weight/periodic resistance communication recovered; local comparisons passed, local comparisons complete |
| V2 jump ropes/dumbbells | Live counts, jump rate/continuous/interruption data, dumbbell weight and persistence implemented; local comparisons complete |
| Treadmills | Supported readings only; no motor start, speed or incline commands |
| Equipment folding/unfolding | Shown on the connection page only with a declared capability. Requires idle equipment and known state; confirmation, feedback wait and read-only retry are implemented. Mechanical action is not hardware-tested |
| Separate Bluetooth heart-rate straps | Standard BLE heart-rate service supported when advertised; requires the advertised service |

An elliptical does not require a separate heart-rate strap. Equipment-provided heart rate is labeled on its device card. A separate accessory appears only while connecting or connected.

Phone/tablet orientations, dark mode and enlarged fonts have been checked in an emulator. Foldable-sized windows were tested, but **physical foldable devices have not been verified**. Manufacturer background restrictions may affect long floating workouts.

See [protocol verification](PROTOCOL_VERIFICATION.md) for original-DEX comparisons, model tables and limits. The [v0.1.1 compatibility review](V011_COMPATIBILITY_REVIEW.md) adds a shipped-APK comparison and historical MB-EP replay. This phase is preliminarily complete under local/code acceptance; other-model hardware is not a dependency. Hardware evidence is limited to existing MB-EP captures and the AT-R report in Issue #1.

The [2026-10-18 follow-up audit](REA_AUDIT.md) found gaps outside those earlier comparisons. v0.3.4 fixes declared zero-level feedback/calculation, per-metric freshness and AT-R revolution totals; see [release validation](V034_VALIDATION.md). Local comparisons do not establish complete support for every model, firmware or range.

Complete six-byte FTMS ranges retain fractional increments; legacy four-byte MOBI ranges retain vendor rules. MOBI two-byte commands cannot represent levels above 25.5, so those ranges remain read-only. Folding state bytes 0/1 lack a confirmed physical-pose mapping; the action is labeled “Fold / unfold” without guessing direction.

### First connection

Move gently for a few seconds, check cadence/resistance feedback, then try one resistance step. If control fails, readings freeze or the connection drops, follow the [log guide](HELP.md#english); you do not need to identify the protocol. Disconnection pauses the workout. Reconnecting does not replay queued commands.

### Protocol details for troubleshooting

| Path | Identification and limits |
| --- | --- |
| V1 / FFE0 | FFE3 writes, FFE4 notifications; AB 04 status header. Elliptical subtypes 17/19: 24 levels; 18: 8 levels. Bike subtypes 18/21/23: 24 levels; 19/20/22: 32 levels. 0B11 uses two pulses per revolution |
| V2 / 8800 | Reads type, resistance read/write mode, range and magnet count; handles 8811/8812/8813/8814, 88FF unlock and 880F resistance writes |
| FTMS / 1826 | Handles 2ACE/2AD2/2AD1/2ACD; identified MOBI variants use vendor ordering/encoding, generic FTMS subscribes to and waits for responses |
| HuanTong / FFF0 | FFF1 subscription, FFF2 weight initialization and periodic resistance writes; commanded levels are distinguished from feedback |
| Heart rate / 180D | 8/16-bit heart rate on a separate BLE connection; stale values disappear after 10 seconds |

Automatic resistance changes by at most one equipment increment every two seconds. Devices with resistance feedback display the confirmed level and report missing confirmation. Write-only HuanTong/AT-R devices display a commanded level; it is not recorded as measured feedback. Distance/stroke-based stages require valid cumulative readings. See [workout notes](TRAINING.md#english) for metric sources.
