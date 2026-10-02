# 设备支持 / Device support

[首页](../README.md) · [English](#english)

## 安装要求

Android 10 或以上，64 位设备（arm64；另提供 x86_64 以便模拟器测试）。界面支持简体中文、繁体中文、英语、日语、韩语、德语。

## 哪些器材可以使用？

**目前只有莫比 MB-EP 系列 V1 椭圆机有用户实机反馈。** “代码已支持”不等于每个型号都试过，连接成功也不等于已收到运动数据。请先确认数据更新与相邻档位调节。

| 器材或连接方式 | 当前情况 |
| --- | --- |
| 白色经典款 MB-EP 椭圆机，V1 | 根据用户实机日志修复数据接收和调阻，支持频率、阻力、心率及功率等估算；功率与热量未经仪器标定 |
| 其他 V1 单车／椭圆机 | 已实现部分已知型号的数据与调阻，待实机验证；未知子型号不开放调阻 |
| Mobi V2 器材 | 已实现设备能力、运动数据和受支持的调阻，待实机验证；部分型号的距离／热量仍缺少可靠换算 |
| FTMS 标准蓝牙器材 | 已实现标准数据与阻力控制流程，待逐型号验证 |
| 旧款 V1 划船机、HuanTong 器材 | 支持有限；部分数据、握手和控制尚未恢复 |
| 跑步机 | 仅接收已支持的数据，不启动电机，也不控制速度或坡度 |
| 独立蓝牙心率带 | 支持标准 BLE 心率服务；需设备广播该服务，待更多实机验证 |

连接椭圆机不需要另配心率带。器材若提供心率，会在设备卡片中注明来源。独立心率设备只在连接过程中或已连接时显示，未连接时不会多出一个“未连接”提示。

手机／平板横竖屏、深色模式和放大字体经过模拟器检查。折叠窗口经过布局测试，**尚未使用真实折叠屏设备验证**。不同厂商的后台限制也可能影响长时间悬浮训练。

## 第一次连接怎么确认？

轻踩或拉动几秒，观察频率／档位是否更新，再尝试相邻一档。不能调阻、数值不动、掉线时，请按[日志指引](HELP.md)反馈；无需自己判断协议。断连会暂停训练，重连后不会重放断线前的控制指令。

## 协议信息（供排查使用）

| 路径 | 识别与限制 |
| --- | --- |
| V1 / FFE0 | FFE3 写入、FFE4 通知；运动状态头为 AB 04。椭圆机子型 17/19 为 24 档、18 为 8 档；单车子型 18/21/23 为 24 档、19/20/22 为 32 档。0B11 每圈两次脉冲 |
| V2 / 8800 | 读取类别、阻力范围和磁铁数；处理 8811/8812/8813，88FF 握手及 880F 调阻；部分私有型号字段尚待核实 |
| FTMS / 1826 | 处理 2ACE/2AD2/2AD1/2ACD；先订阅控制响应，再请求控制权，按能力与范围发送阻力命令 |
| HuanTong / FFF0 | 识别、订阅及诊断；不开放控制 |
| 心率 / 180D | 8/16 位心率，独立 BLE 连接；新数据中断 10 秒后不再显示旧值为实时读数 |

自动调阻每两秒最多变化一个设备增量。当前档位始终以设备反馈为准；发送成功不代表器材已执行，反馈超时会提示未确认。没有测量距离／桨数时，不启动依赖这些读数的阶段。完整指标来源见[训练说明](TRAINING.md)。

## English

### Requirements

Android 10 or later on a 64-bit device (arm64; x86_64 is also included for emulators). Interface languages: Simplified Chinese, Traditional Chinese, English, Japanese, Korean and German.

### Which equipment works?

**User hardware feedback currently covers Mobi MB-EP ellipticals using V1 only.** Implemented code does not mean every model has been tested. A connection alone does not prove readings are arriving: check live readings and a one-level resistance change first.

| Equipment or connection | Current status |
| --- | --- |
| White classic MB-EP elliptical, V1 | Data reception and resistance fixes based on user logs; cadence, resistance, heart rate and model-based estimates supported. Power and energy have not been calibrated against instruments |
| Other V1 bikes/ellipticals | Data and resistance implemented for some known submodels; awaiting hardware tests. Unknown submodels remain read-only |
| Mobi V2 | Capabilities, readings and supported resistance control implemented; awaiting hardware tests. Some models lack confirmed distance/energy scaling |
| Standard FTMS equipment | Standard data and resistance control implemented; model-by-model testing needed |
| Older V1 rowers and HuanTong equipment | Limited support; some readings, handshakes and controls remain unavailable |
| Treadmills | Supported readings only; no motor start, speed or incline commands |
| Separate Bluetooth heart-rate straps | Standard BLE heart-rate service supported when advertised; more hardware testing needed |

An elliptical does not require a separate heart-rate strap. Equipment-provided heart rate is labeled on its device card. A separate accessory appears only while connecting or connected.

Phone/tablet orientations, dark mode and enlarged fonts have been checked in an emulator. Foldable-sized windows were tested, but **physical foldable devices have not been verified**. Manufacturer background restrictions may affect long floating workouts.

### First connection

Move gently for a few seconds, check cadence/resistance feedback, then try one resistance step. If control fails, readings freeze or the connection drops, follow the [log guide](HELP.md#english); you do not need to identify the protocol. Disconnection pauses the workout. Reconnecting does not replay queued commands.

### Protocol details for troubleshooting

| Path | Identification and limits |
| --- | --- |
| V1 / FFE0 | FFE3 writes, FFE4 notifications; AB 04 status header. Elliptical subtypes 17/19: 24 levels; 18: 8 levels. Bike subtypes 18/21/23: 24 levels; 19/20/22: 32 levels. 0B11 uses two pulses per revolution |
| V2 / 8800 | Reads type, resistance range and magnet count; handles 8811/8812/8813, 88FF handshake and 880F resistance writes. Some proprietary fields still need confirmation |
| FTMS / 1826 | Handles 2ACE/2AD2/2AD1/2ACD; subscribes to responses before requesting control, and gates resistance commands on capabilities/range |
| HuanTong / FFF0 | Identification, subscription and diagnostics; no control |
| Heart rate / 180D | 8/16-bit heart rate on a separate BLE connection; stale values disappear after 10 seconds |

Automatic resistance changes by at most one equipment increment every two seconds. Current resistance always comes from feedback; a successful write does not prove mechanical execution, and missing confirmation is shown. Distance/stroke-based stages require measured cumulative readings. See [workout notes](TRAINING.md#english) for metric sources.
