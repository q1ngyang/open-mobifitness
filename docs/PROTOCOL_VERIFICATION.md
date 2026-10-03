# 官方协议复核 / Protocol verification

[设备支持](COMPATIBILITY.md) · [对照工具](../tools/protocol-oracle/README.md) · [算法](ALGORITHM_AUDIT.md)

这次适配以国际版 2.1.14 和中国版 4.5.16.1 的本地 APK 为参考，恢复连接流程、型号能力、数据解析与计算规则。相同合成输入由原始 DEX 和 Kotlin 实现分别处理；期望值来自原 APK 的运行结果。**这是本地差分验证，不是所有设备、固件和 Android 蓝牙栈均已实测的证明。** 本轮以代码和本地验证完成适配，并额外对比 v0.1.1 已实测 V1 椭圆机基线；详见 [v0.1.1 兼容性复核](V011_COMPATIBILITY_REVIEW.md)。其他型号不以新增实机验证作为本阶段完成条件。

## 已恢复的路径

| 路径 | 实现与验证范围 |
| --- | --- |
| 识别与连接 | 服务优先级 8800 → FFE0 → 满足名称条件的 HuanTong FFF0 → FTMS 1826；MTU 128；按照发现顺序交错读取和订阅；原队列每次操作后间隔 300 ms |
| V1 | AB03 旧水阻／磁阻划船机，AB04 划船机、单车、椭圆机、跑步机；FFE1/FFE4/FFEA/FFEB/FFF4；MB-HW 的 FFE4 延迟 2 秒；调阻保留收到的配置字节，不补造模板 |
| V2 | 8801 上传模式、8802 型号、8803 硬件、8805 磁铁数、8806 阻力能力；8811 脉冲、8812 阻力／哑铃重量、8813 汇总、8814 跑步步频；88FF 解锁、880F 控制；模式 3 的脉冲不会覆盖汇总速度／功率 |
| HuanTong | FFF1 优先订阅、体重初始化、每秒 FFF2 阻力帧与校验、BCD 频率；断连取消周期任务；设备不回报实际阻力时，界面单列“设定阻力” |
| MOBI FTMS | 原厂控制权请求及两字节阻力写入；椭圆机两／三字节 flags；速度取整顺序、范围取整与各类型阻力分辨率；普通 FTMS 保留标准三字节阻力与控制响应确认 |
| 划船计算 | RowingAnalysis、RowingAnalysis3209、旧水阻／磁阻入口；桨状态、速度、桨频、拉力、功率和热量；重置与反向模式保持原语义 |
| 单车／椭圆机计算 | 默认 type-0、多种最高档位、0B11 系数、异常频率回退、虚拟速度、未截断功率热量；计算档位按原 MotionData 每个有效秒逐步接近反馈档位 |
| 状态与能力补齐 | 8807／8808、8901／8902、880E 急停；V1 AE 折叠状态、首次 FFED 查询、设备时长和错误码；88E1／8A01 与官方一样只记录原始诊断，不猜测含义 |
| FTMS 处理器补齐 | 划船速度取整、阻力／坡度整数化、跑步频率／步幅、时长、缺失功率的估算与逐秒计算档位；2AD4／2AD5 范围、2ADA 状态 |
| 小器材 | V2 类别 0x10，子型 1 跳绳、2 哑铃；15／7 字节实时数据及哑铃重量；次数、连续跳绳、中断、重量和原始动作字段可保存并往返导入 |

型号默认能力来自原处理器的分支，8806 明确能力优先于默认值：

| 类别 | 已恢复子型及阻力能力（十进制） |
| --- | --- |
| 划船机 | 0–6：水阻、磁阻、小水阻、风阻、动态风阻、标准磁阻、立式水阻；不凭名字推断电子调阻能力 |
| V1 单车 | 18/21/23：读写 24 档；19/20/22：只读 32 档；1 保留原无控制默认 |
| V2 单车 | 18/23：读写 24 档；21/24/32：读写 32 档；19/20/22：只读 32 档；33：只读 8 档；1/25 不主动开放控制 |
| 椭圆机 | 17/19：读写 24 档；18：只读 8 档；V2 20/21：读写 24 档、22：读写 40 档；1 无控制 |
| 跑步机 | 0/1/17/18/19/20/21 的协议识别；18 的折叠型步频路径；V2 硬件 0x18 的距离／热量倍率和 0x16 + 子型 19 的坡度换算 |

RowingAnalysis3209 类的计算已逐样本比较，但两份参考 APK 中未发现可确认的实例选择入口；连接层不根据相似型号名自行启用它。跑步机状态／速度／坡度编码已有对照，当前产品仍只接收跑步机数据，“开始记录”不启动电机。8901／8902 能力与交互反馈、8903 小器材目标及 V1／V2 折叠编码已恢复；训练 UI 使用现有记录／调阻入口。工厂改型、校准、固件升级、官方云服务及设备离线历史同步属于其他产品功能，不作为本次实时训练设备支持的验收项。按键／语音事件已解码，未映射为自动运动控制。

## 数据差分结果

每个 APK 执行 1,663 个请求，提取后的 16 张表逐字节相同；原始元数据中的显示名称／图片可因版本不同而不同。参考 APK、输入集和期望表的 SHA-256 见 [manifest.json](../tools/protocol-oracle/manifest.json)。

| 数据集 | 数量 |
| --- | ---: |
| 划船连续输入 | 2,363 个采样 × 19 个状态／计算输出，27 个场景 |
| V1/V2/HuanTong 数据解析 | 5,564 个字段 |
| FTMS 可选字段及 flags 变体 | 3,338 个字段 |
| 跳绳／哑铃回调 | 1,662 个字段 |
| 默认功率／热量 | 504 组 |
| 计算阻力逐秒过渡 | 680 个状态，比较档位、功率、热量 |
| 可选 type-1 数学模型 | 504 组；默认训练仍采用 type-0 |
| 汇总划船功率／热量 | 28 组，含两种模型 |
| 读写模式／有符号坡度 | 全部 256 个字节值 |
| V2 型号默认能力 | 33 个配置 |
| 控制指令 | 183 组，包含两组原厂没有写入的命令 9；补齐 V1 全部 24 档与配置字节组合 |
| 折叠控制 | V1／V2 各 4 组状态，8 次原处理器入队写入 |
| 额外状态／能力 | 2,192 个字段：速度／坡度范围、折叠、急停、交互能力 |
| FTMS 完整回调 | 1,088 个字段；覆盖原始 flags 解码之后的二次转换 |
| FTMS 范围／机器状态 | 19 个原回调输出 |
| 小器材／按键交互 | 42 个字段；原 Bus 输出事件作为参考 |

测试资源是**合成输入的实际原 DEX 输出**，不是自写“参考公式”的输出，也不冒充设备抓包。比较涉及 64,039 个数值、布尔或命令结果。有限回归样本不能证明无限输入空间或未见固件的行为。

## 回查位置

相对于解包后的包名路径，核心证据如下。国际版核心处理器在 classes2，中文版对应处理器主要在 classes4；以类描述符定位，不依赖 JADX 行号。

| 类／方法 | 核对内容 |
| --- | --- |
| `com.anytum.mobi.device.bluetoothLe.handler.BaseHandler` | 服务选择、MTU、心率、RPM 范围回退 |
| `…handler.V1Handler` / `V2Handler` / `HuanTongHandler` / `FtmsHandler` | handlerData 的读／订阅顺序、通知回调、解锁、命令与周期写入 |
| `…bluetoothLe.bleTool.BleGattMangerQueue` | 300 ms 队列完成间隔 |
| `com.anytum.mobi.device.data.RowingAnalysis` | JADX 存在歧义的 `checkIsSlowMode`、`reportPull`，回查 Smali 的条件跳转与寄存器运算 |
| `…data.RowingAnalysis3209` | 两种 append 入口、flag 变化、重置 |
| `com.anytum.mobi.motionData.MotionData` | `getAvgResistanceInQueue` 实际返回 mTargetResistance；`updateCurveData` 的步进；`setInstant` 的计数和整数功率 |
| `…device.data.SkippingRopData` 构造函数 | Smali 确认 interrupts 在 continuousJump 之前：8813 字节 11–12 为中断、13–14 为连续跳绳 |
| `…handler.V2Handler$handlerData$6$1` / `kotlin.collections.h.h1` | 小器材目标反馈调用 `copyOfRange(3,4)`，上界不包含，只消费字节 3；不能臆补为双字节。与发出目标命令的双字节编码并不对称 |
| `…handler.FtmsHandler` 的 6／7／8／9 回调 | 四种器材完整回调，验证字段解码之后的取整、单位变换和设备时长 |
| `…handler.FtmsHandler.bleVerFtmsControl` | Smali 的长度 2 数组和 int-to-byte；不能自动改成标准编码用于已识别的 MOBI 旧变体 |
| `com.oversea.sport.ui.main.customview.g.e`（SportDataView） | 官方按器材类别构造可选指标集合；椭圆机／单车不包含桨数、坡度、步频、步幅 |

## 指标分组

主训练页、小悬浮窗和大悬浮窗均以当前识别的器材类型筛选，配置按“器材类型 × 显示位置”保存。椭圆机仅提供通用项、频率、阻力、功率、速度和平均速度；划船机提供桨频、桨数、拉力、配速等；跑步机提供速度、坡度、步频、步幅等；跳绳、哑铃有各自计数／重量项目。OpenMOBI 的配速、平均速度等扩展指标也限制在适用类型。

旧的全局自定义设置迁移时先过滤不适用项再限制数量；短暂缺少传感数据不会删除偏好。选择窗口打开期间识别结果改变，选项与草稿同步换组。连接另一类设备后，旧训练保持暂停，需结束后再创建新训练，防止混入不同器材的数据。

## 本地检查与实机边界

本地完成核心 55 项、Debug 应用单元 37 项、Release 应用单元 38 项测试，Lint、Debug／Release 构建。模拟 GATT 覆盖初始化、读写能力、生命周期命令、实际反馈确认、HuanTong 周期任务取消、原厂与通用 FTMS 控制差异，以及并发控制响应串行处理。模拟器 2 项界面回归通过，覆盖设备切换、排序、分类型保存、重新打开和不显示其他器材指标。

连接层保留合理边界：截断帧不补字节；未知控制能力不猜测；MOBI 两字节 FTMS 无法表示的目标（>25.5）不截断回绕；独立心率服务遵守标准小端；暂停／断连不补记运动，过期数值不继续当实时读数；GATT 超时会释放连接。原厂有些陈旧值保留或错误边界行为没有照搬。

v0.1.1 发布 APK、当前实现及两版官方 APK 的历史 MB-EP 回放已完成：394 条实机帧、768 个官方有效阻力／频率回调、每帧 24 档共 9,456 次编码比较通过。另有 576 组公开合成旧版对照和模拟 GATT 的全档位反馈闭环。初始化 MTU 拒绝回退、连接超时、截断帧状态污染均有回归。

本阶段可以宣告初步完成：参考 APK 中的实时训练协议适配、本地对照、已有 V1 实机基线回归和指标分组均完成；没有依赖其他型号实机的后续验收项。本轮未新增实体连接／机械调阻或功率标定，不能将以上证据扩大为所有固件和 Android 蓝牙栈均实测。

## English

The local adaptation uses unmodified international 2.1.14 and Chinese 4.5.16.1 DEX as an executable reference. Each APK receives 1,663 synthetic requests; the resulting 16 golden tables match byte for byte. Kotlin regression tests compare 64,039 scalar/command results. The harness and manifest make these results reproducible without distributing APKs or full decompilations.

Recovered paths include V1/V2 identification and capabilities, ordered initialization, legacy rowers, HuanTong weight/periodic writes, MOBI-specific versus standard FTMS, small-equipment live packets, default/alternate calculations and the original per-second calculation-resistance transition. Ambiguous JADX branches were checked against Smali. The table above identifies source classes and the supported model defaults.

Metric choices and preferences are scoped by machine and display surface. Switching a machine while the picker is open replaces its choices; old global preferences are filtered on migration. Recordings cannot resume on a different machine category.

Core tests (55), Debug app tests (37), Release app tests (38), both lint variants and both builds passed locally. Simulated GATT and emulator UI checks are separate from hardware proof. This round performs no physical-device tests. Treadmill motor controls, factory configuration, firmware updates, automatic gamepad actions, cloud services and device-stored history synchronization are outside the product integration here. Numeric guards, stale-data handling and disconnect protection intentionally avoid certain vendor edge-case bugs. The phase is preliminarily complete under the agreed code/local-test acceptance: 394 historical MB-EP frames, 768 emitted vendor resistance/cadence values and all 24 resistance commands per frame match the shipped v0.1.1 baseline. Another 576 synthetic baseline cases are public. Other-model hardware is not a dependency or an outstanding acceptance item. No new physical connection or mechanical calibration is claimed.
