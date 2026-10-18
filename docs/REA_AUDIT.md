# REA 蓝牙与算法复核 / REA protocol and algorithm audit

[协议验证](PROTOCOL_VERIFICATION.md) · [算法说明](ALGORITHM_AUDIT.md) · [文档目录](README.md)

**修复状态（v0.3.4）：以下为 v0.3.3 的历史发现，不代表当前实现。已将 A01–A14 对应的记录链、能力与零档回归纳入生产测试，并实现连接页展开／收起和计划完成选择。新的三方原始 DEX 复核及保留边界见 [v0.3.4 验证](V034_VALIDATION.md)。**

2026-10-18，核查代码基线 `59cf4fd`（v0.3.3）。使用 [morluto/rea](https://github.com/morluto/rea) 的本地 Android 静态分析接口重新检查两份参考 APK，并复跑当前 Kotlin/JVM 回归。

**补查修正：首轮核对选定 APK 方法与既有回归，不能据此认定训练协议和算法已完整接入。Issue #1 的 AT-R 转数计数遗漏、调阻能力差异应列入审计；后续序列探针又复现了数据过期、累计计数、派生指标与启动顺序的缺口。** 具体结果和证据边界见下方补查；默认公式的数值对照通过不等于这些应用行为正确。

**以下是 v0.3.3 的分阶段审计记录。** 三方动态差分后又完成展开／收起和零起始档位的定向补查，见下一节。文末“未重新执行官方 DEX／未启动模拟器”仅描述最初静态检查阶段；下文“当前”“尚未支持”“生产代码未修改”均指当时的审计基线，v0.3.4 的实现及验证以页首链接为准。

## 展开／收起与设备特殊规则补查（2026-10-18）

**尚未完成全型号、固件及特殊规则的全量核验；器材一键展开／收起也尚未接通产品控制流程。** 之前的 2,772 条序列是限定输入的执行覆盖，不能替代“型号 × 协议 × 能力声明 × 状态 × 控制 × 反馈 × 记录”的规则覆盖。四类器材的子型字节扫描也不等于逐型号验收。

### 展开／收起究竟支持到哪一层

| 层 | 两版官方 APK 证据 | OpenMOBI 当前状态 |
| --- | --- | --- |
| V1 能力 | `V1Handler` 在跑步机子型 **18（十进制）** 设置 supportFold；没有找到据此启用 V1 划船机折叠的分支 | 相同条件；支持 AE 状态接收和首次 FFED 查询。查询使用命令 0，并非展开／收起命令 7 |
| V2 能力 | `8901` 的 bit 2（`0x04`）声明可折叠；此处没有仅限跑步机的类型判断 | 同样读取该能力位，因此声明它的 V2 划船机也能进入能力／状态解析。合成声明不证明某个真实型号具有电动折叠机构 |
| 状态反馈 | V1 `AE 01 … state`、V2 `8902=[03,01,state]` | 已保存到 `MobiDeviceDetails.foldedState`；应用仅将变化进入状态和诊断，没有器材折叠状态的专门界面 |
| 控制编码 | V1 controlDevice 命令 7 → FFE0/FFED；V2 命令 7 → 8900/8903 的 `03 01 00/01` | 编码函数已有，原有 oracle 包含 8 次官方入队写入对照；应用没有调用 `v2Fold` 或 V1 命令 7 的发送路径 |
| 产品入口 | 官方 `EditBleActivity` 在已连接且 supportFold 时显示 btFold，调用 controlTreadmillFold；名字虽含 treadmill，显示条件本身不按器材类型限制 | 没有对应按钮、操作状态流程或完成确认。Android 折叠屏布局、面板“展开／收起”与器材机械折叠无关 |
| 实际动作 | 本轮未连接器材 | 未验证各型号完成、进行中、故障、中断、重连后的状态语义；不能宣称一键展开／收起已支持 |

官方命令按当前状态切换：`foldedState == 1` 发 0，否则发 1。它不是已经确认语义的两个幂等“展开”“收起”接口。本轮 0、1、2、255 状态都经过真实 DEX 接收；接受一个数字不等于确认其机械含义，不能把未知／过渡状态一律当作可以反向动作。

发现的兼容性差异也保留：V2 `8901=04 → 00` 时两版官方保持 supportFold=true，OpenMOBI 清为 false；官方初始 foldedState=1，OpenMOBI 为 null。没有设备状态证据时，不把官方的锁存／默认状态直接当作应复制的正确行为。

### 零起始档位：证据与新复现

重新读取公开 [Issue #1](https://github.com/q1ngyang/open-mobifitness/issues/1) 和唯一的维护者评论。正文把 `AB 03 00 0A 01 01 01` 标为 **LEVEL 1**，建议开放 **1–24**；没有报告 0 档测试，也没有证明“界面 1 档对应协议 0”。引用的 Codex 评估任务仍因缺少 `read_thread` 接口而未读取。因此不能将 AT-R 零起始档位写成已证实事实，也不能把所有单车统一改成 0–23。

即使暂不认定任何真实型号从 0 起档，显式能力声明仍暴露出可复现的不一致：

| ID／范围 | 同一输入与观察结果 | 意义 |
| --- | --- | --- |
| **A13，V2 零档反馈** | `8806=[0x05,0x00,0x18]` 声明读写、0–24；`8812` 反馈十进制 12 → 0 后，单车／椭圆机／划船机均保留旧的 12。0 档命令能编码为 `02 01 00`，但接收过滤 `level > 0`。应用探针中的待确认 0 档意图未被清除；1 档对照能确认 | 范围声明、命令编码和反馈消费互相不一致。探针只设置待确认意图，不声称物理发送成功或已测试超时 |
| **A14，MOBI FTMS 零档计算** | 0–24 范围，反馈 12 → 0：Metrics 已为 0，估算仍使用旧的 12。单车 60 rpm 为 157.172275 W；椭圆机 120 rpm 为 556.131388 W；12 个活动 tick 后仍不变。正值 1 能使估算下降 | 显示数据与功率／热量的计算输入脱节；这里的瓦数是模型输出，不是实测机械功率。影响估算路径，不把设备直报功率一概算作受影响 |
| **CONTROL-4，通用 FTMS** | 相同 12 → 0 的阻力变化会更新 Metrics，并确认待处理的 0 档目标 | 问题不能概括为所有 FTMS 都丢弃 0 |
| **官方也会忽略 0** | 两版原始 BaseHandler 在声明最小值为 0 后仍不发出 updateResistance(0)；MotionData 保留 12 | 再次证明两边相等不能代替独立的规则检查；不能仅靠差分寻找这类共同遗漏 |
| **步长和字节边界，待硬件语义确认** | 合成 FTMS 步长 0.5 在 MOBI 路径仍成为 1；V2 最大值字节 FF 在官方成为 -1，而 OpenMOBI 范围为 255，随后反馈 128／255 又因有符号字节被丢弃 | 记录为规则边界和兼容性差异，不宣称存在 255 档设备，不将厂商异常值照搬；小数步长及实际可用档位需按协议／固件核定 |

应将原始档位、显示档位、可写范围、步长、缺测／哨兵值和算法输入分开描述。0 可能是有效阻力、空挡或缺测，必须由具体协议及设备证据决定；不能在通用接收层无条件等同于缺测，也不能用统一的 +1／-1 修补所有型号。

### 本轮执行及剩余规则覆盖

新增 [special_rules.py](../tools/protocol-differential/special_rules.py) 生成 **136 条定向序列，每份 APK 4,430 步，三方共 13,290 步**，与前批输入／结果分开保存。范围包括九组 V2 类别／子型的读写模式、最小值 0／1、能力声明先后顺序；三类 FTMS 最小值与步长；14 条 V1/V2 折叠序列；两条字节边界序列。两版官方 APK 和当前 OpenMOBI APK 的原始接收／计算代码均执行完毕，初始化、回调及 MotionData 转发异常均为 **0**。

两版官方在本批的差异限于电量初值、型号名称和图片字段；未发现本批折叠／阻力计算行为的两版差异。11,028 次映射比较留下 2,980 条差异记录，包含缺省值、未对齐层和有意行为差异，**不是 2,980 个缺陷**。原始 trace 不被摘要替代。

新增应用审计 **3 个测试全部通过**，其中 A13、A14 通过表示缺口被复现，不表示修复；另一个是正常行为对照。之后常规应用 **97 项**测试通过，0 失败／0 跳过，且不含审计探针。前批 15 个审计测试没有在此数字中重复计数。本轮只修改审计工具、探针和说明，没有修改应用生产实现。

| 器材／规则族 | 已有证据 | 仍未完成的核验 |
| --- | --- | --- |
| 单车：默认读写模式、8／24／32 档、AT-R 子型 1 | 官方表、原 DEX 序列、A01/A02、此次 0／1 下限补查 | 型号和固件对应的实际起始档位、原始档位与面板编号映射、AT-R 写入及回报语义；不能仅据固定表认定不可写 |
| 椭圆机：0B11 双脉冲和功率修正、8／24／40 档 | 历史 MB-EP 帧与原 DEX 数值对照；此次零档反馈／估算探针 | 其他固件的脉冲数／量纲、半档等增量与实际可写范围、所有模式下的算法和最终记录组合 |
| 划船机：旧水阻／磁阻、新上传模式、型号 7–10、3209 | 已有连续状态机对照；新型号目录与模式序列；V2 折叠能力输入 | 各型号折叠机构和命令状态、3209 的真实选择条件、完整逐桨派生指标接入和计数回绕语义 |
| 跑步机：硬件码倍率、坡度映射、急停、折叠 | 0x18 距离／热量倍率、0x16＋子型 19 坡度分支已有代码／原 DEX 对照；此次 V1/V2 折叠状态补查 | 型号×硬件码×固件的完整状态组合、机械控制流程及状态枚举、过渡／故障状态和重连动作；生产端仍仅接收运动数据 |
| 跳绳／哑铃：计数、重量、交互目标 | 接收与存储字段已有映射、指令及反馈不对称已有原 DEX 证据 | 真实固件的上限／溢出、目标命令回报与产品操作流程；不能把官方构造但未写入的命令 9 算作支持 |
| HuanTong：BCD、体重初始化、周期发送 | 编码、解析和任务取消已有对照；产品区分设定档位和反馈 | 不同型号最低档位和扩展范围的实机证据，不能将 V2 的 0 档规则推广过去 |
| FTMS：MOBI 变体与通用协议 | 原 DEX flags／单位对照；此次最小值、步长与零档计算检查 | 分类型的范围、增量、无效值、控制回复与最终记录的全组合；MOBI 单字节阻力命令的限制不能应用于通用 FTMS |
| 跨协议状态生命周期 | 已有顺序／静默／部分更新／重连探针，缺口 A03–A12 保留 | 每个字段的有效期、源切换、回绕与复位、未开始上报时的能力确认；尚无完整设备／固件规则清单和逐项闭环 |

本轮原始输入、三份输出、比较、测试 XML 和日志位于 `$DEV_TEMP_BASE/work/openmobi-special-rules-20261018/`。可移植摘要见 [special-rules-results-20261018.json](../tools/protocol-differential/special-rules-results-20261018.json)，复现入口见 [工具说明](../tools/protocol-differential/README.md)。没有执行无线链路、机械动作、官方 UI／最终落库或完整固件矩阵测试；这份表明确保留了这些空缺。

## 三方动态差分补查（2026-10-18）

### 为什么逆向过仍会遗漏

前次把选定方法和既有测试的兼容性结论推广成了完整性结论，这是审计方法和表述的问题。已有 oracle 确实执行过官方 DEX，但大量用例预先设置型号、阻力模式、磁铁数，再调用单个回调；提取脚本只导出挑选的字段，零功率、缺省字段及未映射字段没有进入对照表。这能检验某些公式，不能证明初始化、部分更新、静默、暂停、记录等行为都正确。

另外，官方不是硬件能力或数据正确性的最终标准。AT-R 转数计数在两边都未使用；同样的合成输入可能让双方一起忽略它，差分本身就不会报警。需要同时保留实机反馈、字段变化试验和独立不变量，例如停转后不能一直延长旧速度的有效期。

### 实际执行与覆盖

在现有 LXC 内启动 API 34 软件模拟器，使用原 AVD 的只读实例；没有 KVM，冷启动约 194 秒。通过 `app_process` 加载未修改的两份官方 APK 和本轮构建核验的 OpenMOBI Debug APK。把**同一份原始报文字节及顺序**交给各自实现；不是重写官方公式后互相比较。运行和比对代码见 [protocol-differential](../tools/protocol-differential/README.md)。

| 范围 | 实际规模 |
| --- | --- |
| 每份 APK 的唯一输入 | **2,772 条序列，21,382 步**；三方共 64,146 步，不含重复性验证和纠正隔离环境后的重跑 |
| 设备范围 | 划船机、单车、椭圆机、跑步机、跳绳、哑铃；另有心率、电量 read/notify |
| 重点子型 | 39 个型号／子型组合，包括源码已有分支但未命名的子型；不等于 39 台实机通过 |
| 探索范围 | 四类大器材各扫描子型字节 0–255，共 1,024 个组合；不能把未知值算成真实支持型号 |
| 序列 | V1 新／旧帧、HuanTong、V2 mode 1/2/3、元数据先后顺序、不同磁铁数、9/11/13 字节汇总、小器材、四类 FTMS flags、双头长度、零值、截断和追加字节 |
| 算法层 | 18 个单车／椭圆机子型的真实 MotionData listener 与逐秒计算档位过渡；另有六类器材的汇总／零值／部分更新序列 |
| 重复性 | 每份 APK 80 条逆序重放，活动计算和状态一致；官方旧 LastUpload 构造时缓存的两个标签差异单独保留，不宣称最终上传记录已验证 |
| OpenMOBI 记录链 | **15 个**审计测试，含四类器材的 BleClient → Controller → Repository 复现；之后常规应用 **97 个**测试通过，均无失败／跳过 |

所有 2,772 条序列都到达各 APK 的接收/计算边界；只有明确标记的子集进入官方 MotionData，只有上述审计探针进入 OpenMOBI 数据库。没有把全体序列都运行到两边 UI、官方完整计时器或上传流程。没有进行真实 GATT、实机机械反馈、功率计或能耗标定。

### 新发现与已扩大的影响范围

| 结果 | 可复核证据与结论 |
| --- | --- |
| **旧速度问题跨四类器材，A11** | 单车 `2AD2`、椭圆机 `2ACE`、划船机 `2AD1`、MOBI 跑步机 `2ACD`，先给有效速度，再连续 6 秒只更新踏频／桨数／步频。旧速度仍被当作新鲜数据，分别多计 **60、60、30、60 米**并保存到历史。后两类是前次 A04 没覆盖到的影响范围。根因仍是字段缺省与一个共享 motionAt，不能仅为某型号打补丁。 |
| **极端值可以进入记录，A12** | FTMS 单车踏频字段 `FFFF` 被解析成 **32,767.5 rpm** 并写入样本。本轮证明缺少设备合理性检查；不靠这个合成值臆定各器材阈值。官方也会接受该数值，因此只做两边相等检查会漏过。应检查原始值、有效性标志、合理变化范围与落库策略。 |
| **四个划船型号名称未收录，B01** | 中文版 DEX 列出 `09/07` EMS 磁阻 MRH3208、`09/08` 普通磁阻 MRH3204、`09/09` 普通水阻 MRH1105A、`09/0A` 普通磁阻 MRH3202；OpenMOBI 显示 `0907`–`090A`。已追加 V1 和 V2 三种上传模式共 16 条完整序列。当前证据证明型号目录遗漏，未证明需要新公式：两版 RowingAnalysis 对 7–10 没有专门系数分支。不能凭名称启用 3209 算法。 |
| **纠正 8804 的语义，B02** | 两版原始回调会执行 `setSignalAcquisitionScale(unsignedByte(data[0]))`，动态状态也确认该字段。前次“官方只记录原始字节”的描述错误，现已更正。OpenMOBI 没保存它；目前未找到其参与蓝牙运动计算的读取链，故归为元数据/诊断遗漏，不认定数值算法错误。 |
| **官方也保留旧运动值** | V2 划船／单车／椭圆机先接收 3 m/s、200 W，再给全零汇总包；原始回调已经归零，但官方 MotionData 仍保留 3 m/s、200 W。OpenMOBI 在这个明确零值场景反而归零。跑步机的旧步幅则在两边都能残留。必须分别观察回调、活动状态和最终记录，不应照搬厂商缺陷。 |
| **初始化顺序差异有真实证据** | 例如 `8806=[3,1,24]` 的只读声明先于 `8802=[10,18]`：官方模式会从 2 被型号表改成 3；OpenMOBI 保留显式只读声明。反向顺序两边保留只读。这不是应把 OpenMOBI 改成官方行为的理由，说明必须测试真实元数据顺序。 |
| **现有计算的有效对照** | 在这次 18 个单车／椭圆机子型、正踏频输入和阻力过渡序列中，官方 MotionData 的整数功率、踏频与 OpenMOBI 一致。零脉冲时官方保留旧值、OpenMOBI 归零，差异被保留。此结论仅覆盖所选输入，不证明物理标定正确。 |

前次电量消费者、AT-R 计数和调阻限制、数据尾差、回绕条件、启动顺序等发现仍应保留，不能被本次多数常规字段一致抵消。V1／V2 未知字节的真实语义以及不同硬件的能力，仍需要带型号、固件、时间戳的实机数据。

### 差异与执行环境的区分

本轮未发现两版官方 APK 在所执行的活动运动数值上的不同；原始差异主要是型号名称、图片、电量初值（国际版 0，中文版 -1），以及 Firebase 报错中包含的进程名称。不能把这些元数据差异计成公式差异。

工具保留了数万条字段差异候选，其中包含“缺省与零”“回调与派生值”“拒绝无效输入”等正常差异，**不作为缺陷数量**。每版官方在主集有 96 个步骤抛出异常，其中 48 个截断范围报文触发了 Firebase 未初始化这一环境依赖；补集中另有 10 个截断心率异常。没有把 Firebase 依赖错误当作完整官方 App 的崩溃证据，也没有把异常用例当作匹配通过。OpenMOBI 在这些执行中无未捕获异常；内部拒绝帧或返回 null 仍需结合状态分析，不能算普遍正确。

首轮计算子集切换设备时曾误触发官方结算依赖。已修正隔离初始化、重跑全部 18 条，最终初始化／下游执行错误为 0，并保留初始结果。计数不包含这次重复运行。工具还把未映射字段列入清单，避免以前“没有导出就没有差异”的盲区。

代码、运行命令、紧凑结果与哈希在 [tools/protocol-differential](../tools/protocol-differential/README.md)；完整合成输入、三方逐步输出、初始失败与修正重跑、差异清单、重复性验证及测试 XML 位于 `$DEV_TEMP_BASE/work/openmobi-differential-20261018/`。现阶段应优先修复字段时效、派生值失效和启动顺序，再完善能力／型号证据与边界校验；这次交付的是扩大审计与可复现工具，没有声称问题已修复。

## 输入与工具

| 项目 | 国际版 | 中国版 |
| --- | --- | --- |
| APK | 莫比健身 2.1.14.apk | 莫比健身 4.5.16.1.apk |
| 包名 | `com.anytum.mobifitnessglobal2` | `com.anytum.mobirowinglite` |
| versionCode | 41 | 217 |
| minSdk / targetSdk | 26 / 34 | 26 / 34 |
| APK 内 DEX 文件数 | 4 | 8 |
| JADX 报告类数 | 10,424 | 17,014 |
| 核心协议 DEX | `classes2.dex` | `classes4.dex` |
| SHA-256 | `1bd01d47b07ca73235354c6a9b7d0cbf26d60d88fd0b8ca8b7ef0e47128775d8` | `99af8aeb883d56bd724ebdadd1114caf79ab7bef34c4b96488d0c2ced7be19e4` |

APK 哈希与现有 [oracle manifest](../tools/protocol-oracle/manifest.json) 一致。环境为 Debian 13 LXC，REA **6.5.0**、Node.js **24.15.0**、完整 JDK **21.0.12.1**、`jadx-headless-mcp` **0.7.1**。引擎 JAR 的 SHA-256 为 `6e5eacf500b64292bfb73c49797c1958f6ee44646e43e868039ae7feb573ff75`，与 REA 固定的发布信息一致。

首轮每份 APK 保留 49 份 REA Evidence：1 份文件清单检查、1 份包检查、1 份类搜索、16 份类成员检查、27 份方法反编译、3 份引用追踪。Issue #1 补查每版再增加 3 份方法证据，合计每版 52 份。另从原 APK 的核心 DEX 直接提取 674／688 个相关类的 Smali，供有歧义的方法复核。完整反编译、原始 Evidence 和日志仅保存在开发卷。

## 确认的遗漏与建议

| 项目 | 证据与影响 | 建议 |
| --- | --- | --- |
| V2／FTMS 电量没有进入状态 | 两版 `V2Handler.handlerData$mobidevice_release`、`FtmsHandler.handlerData$mobidevice_release` 的 `180F/2A19` read／notify 回调均保存 `data[0]`。OpenMOBI 的 [GattPlan](../core/src/main/kotlin/org/openmobifitness/core/GattPlan.kt) 已读取／订阅，但 [BleClient.receive](../app/src/main/java/org/openmobifitness/app/ble/BleClient.kt) 没有 `2A19` 分支；[MobiDeviceDetails.battery](../core/src/main/kotlin/org/openmobifitness/core/MobiDeviceDetails.kt) 目前仅由 V1 帧更新。 | 补齐有范围校验的电量状态与诊断展示，分别覆盖 V2、FTMS 的 read／notify。属于元数据遗漏，不是调阻编码错误。 |
| 诊断日志不能完整回放高频脉冲 | [AppLog.packet](../app/src/main/java/org/openmobifitness/app/data/AppLog.kt) 对相同方向／特征的接收报文执行 200 ms 限流；超过约 5 帧/秒时会主动丢弃日志。`8811` 和 V1 脉冲进入划船状态机，缺失中间采样会破坏回放条件。这不影响当前实时解析路径。 | 优先提供单独的有容量上限、带时间戳和丢包计数的协议采集模式。现有日志可定位一般连接问题，不应作为完整高频采样证据。 |
| V2 关键元数据缺少可回查记录 | `8801` 上传模式、`8803` 硬件码已影响解析，但未作为原始报文保存，也未暴露在当前诊断摘要；`8804` 已入读取队列并转交 `MobiSession`，随后被忽略。**动态补查纠正：官方把 8804 保存为 SignalAcquisitionScale；先前“仅记录原始字节”的表述错误。** `2A24` 型号和 `2A26` 固件字符串已有日志，不能说全部固件信息都缺失。 | 保存上传模式、硬件码和信号采集比例。尚未找到该比例参与蓝牙运动计算的读取链，不据此修改公式，也不把它误标为固件版本。 |
| 划船细节指标没有进入产品数据流 | [LegacyRowing](../core/src/main/kotlin/org/openmobifitness/core/LegacyRowing.kt) 已计算 `graphValue`、`pullTime`、`backTime`、`maxF`、`strokeEnergy`、`strokeDistance`、`pullLength`、`score`；[MobiSession.rower](../core/src/main/kotlin/org/openmobifitness/core/MobiSession.kt) 只输出桨频、速度、桨数、功率和平均拉力，[Metrics](../core/src/main/kotlin/org/openmobifitness/core/Model.kt) 没有其余字段。 | 如果需要逐桨曲线、拉回时间比或峰值拉力，再扩展采样、存储和展示。算法已恢复，属于可选产品能力。 |

上述四项仍成立；补查发现的训练数据正确性问题应优先处理，详见下节。本次只补充审计和复现场景，应用实现尚未修改。

## Issue #1 补查：为什么漏检，以及同类问题

### 漏检原因与证据来源

公开 [Issue #1](https://github.com/q1ngyang/open-mobifitness/issues/1) 的 AT-R 实机反馈早于本次审计；[已有维护者评论](https://github.com/q1ngyang/open-mobifitness/issues/1#issuecomment-5993476260) 也明确记载转数计数未用于距离、子型 1 调阻尚待实现与验证。首轮没有读取这些设备反馈，而是从已有方法和 oracle 输入集出发，漏掉了与现有预期相矛盾的证据。这是审计覆盖不足。

用户同时引用了一个 Codex 评估任务。本会话未提供 `read_thread`，未读取、未引用该任务正文；本节独立依据公开 Issue、评论、当前代码、两版 APK 与本地探针。不能把公开 Issue 的读取说成已读取所引用会话。

两版官方 V1 单车／椭圆机路径也没有消费第 11–12 字节作为转数，只由第 7–10 字节推算踏频。子型 1 的 mode 0 则确实来自官方型号表。因此，仅证明与官方实现一致，无法发现官方本身未使用的计数能力，也无法证明该硬件不可写。补查 REA 的 `MotionData.setRPM` 确认虚拟速度仍为单车 `rpm / 2.68 / 3.6` m/s、椭圆机再除以 4；`BaseHandler.updateResistance` 确认官方反馈也受 mode 2／3 限制。命令可编码、GATT 可写、设备实际调阻是三种不同证据。

另一个漏检原因是没有跨模块验证：原有测试可以分别证明解析字段正确、计数累计器正确、控制入口可启动，却未覆盖同一连接中的部分更新、静默、开始上报与方案准入组合。原先“未发现基础训练协议的新错误”的结论收缩为“所选方法和原有输入对照未出现新的不一致”。

### 可复现结果

以下 A 编号对应 [13 个审计探针](../tools/protocol-audit/README.md)。探针断言当前缺口，因此通过代表**成功复现**，不代表问题已修复。报文和时间序列为合成输入，未进行实机操作。

| 范围 | 结果与影响 | 证据与处理方向 |
| --- | --- | --- |
| AT-R 转数计数，A01 | `AB04 / 0A01` 的转数从 100 改成 110，输出 Metrics 完全相同，`distanceM` 仍为空。实际转数语义依据 Issue 作者实测。 | [MobiSession.v1](../core/src/main/kotlin/org/openmobifitness/core/MobiSession.kt) 在单车分支忽略第 11–12 字节。应保留原始计数并按已验证型号处理；若按官方虚拟速度换算，每转约 `1000 / (2.68 × 60) = 6.2189 m`，仍应标为虚拟估算距离。不能直接推广到其他子型／椭圆机。 |
| AT-R 调阻，A02 | 子型 1 固定 mode 0，命令生成直接返回空。Issue 作者报告 FFE3 命令可改变体感，说明型号表不应当作硬件能力的最终证明。 | [MobiProfiles](../core/src/main/kotlin/org/openmobifitness/core/MobiProfile.kt)、`MobiSession.resistanceCommand`。这是已知兼容性缺口线索；尚无本轮实机证据证明所有该子型都支持 1–24。后续应以设备识别信息和验证记录建立覆盖，不应直接把所有 subtype 1 改成可写或伪造反馈。 |
| 静默后的距离尾差，A03 | 最后一帧为 60 rpm，随后完全静默，按 1 秒采样仍会增加 **24.8756 m**，并保存到记录；第 5 秒读数过期后停止累计。 | [Controller.tick](../app/src/main/java/org/openmobifitness/app/OpenMobiApp.kt)、[TelemetryTotals](../core/src/main/kotlin/org/openmobifitness/core/Telemetry.kt)。现有超时有效，但超时不能消除前 4 个周期的积分误差；已知转数计数可以解决这类距离尾差。热量也会使用仍有效的旧运动输入。 |
| 局部报文延长其他字段寿命，A04、A05 | FTMS 单车每秒只更新踏频且值为 0，6 秒前的 10 m/s 仍被保留，6 秒记录额外累计 **60 m**。V2 mode 3 划船只来 8811，也会让旧 8813 的速度／功率继续有效。 | [BleClient.receive](../app/src/main/java/org/openmobifitness/app/ble/BleClient.kt) 以一个 `motionAt` 标记多种指标，[Metrics.merge](../core/src/main/kotlin/org/openmobifitness/core/Model.kt) 保留未更新字段。需要按字段或数据源记录时效；8811 曲线更新不能证明 8813 汇总数据新鲜。 |
| 派生步幅未失效，A06 | 跑步机从 2 m/s、120 spm 降到零并超过 5 秒，旧的 **1 m 步幅**仍被保存；速度／步频已经为空。 | V1 解析返回 `strideM=null`，merge 保留旧值；Controller 过期过滤未处理步幅。应明确“未提供”“明确无效”“静止”的区别，并让派生值随依赖字段失效。 |
| 计数回绕缺少来源信息，A07 | 合成的 16 位计数 `65534 → 1 → 2` 只累计 1；若设备采用模 65536 回绕，应累计 4。24 位步数有同样问题，V2 距离也经过相同负差归零逻辑。 | `TelemetryTotals` 将所有负差视为复位。这是**有条件的缺口**：本轮未证实各器材实际如何溢出。需要计数位宽、连接／会话代次和增量合理性校验，不能对所有负差直接加模数。 |
| 无效磁铁数形成正常读数，A08 | `8805=0` 后的 V2 脉冲，在实际 `MobiSession` 路径产生 **60 rpm**；`Protocols.intervalCadence` 辅助函数却会拒绝同一无效参数。 | 活跃路径除以零得到非有限数，随后进入厂商兼容的 60／30 rpm 回退。属于输入防护和测试路径脱节，影响后续距离／热量；应在读取元数据和实际计算入口验证，不能只测试未被使用的辅助函数。 |
| 先验读数阻止开始上报，A09 | V2 mode 2 器材已 ready、尚无累计数据时，距离或桨数方案被 `sensor_required` 拒绝，未到 `beginLiveUse()`。 | `BleClient.initialize` 已允许需开始指令的器材进入 ready；`Controller.beginRecording` 却先验证当前累计读数。应让具备能力的器材先进入受控上报准备阶段，收到有效累计值后建立记录基线；有界超时或取消不应留下记录。跑步机自动启动电机仍不属于此流程。 |
| 电量回调丢失，A10 | V2／FTMS 接收 `2A19=87` 后，应用电量仍为空。 | 补充验证首轮已发现的“读取／订阅了，但没有消费者”问题。 |

优先处理局部报文时效、派生值失效和 V2 启动顺序，再结合 AT-R 的时间戳抓包落实计数距离与设备能力覆盖；回绕策略需要先确认设备语义。每项修复应把上述合成场景转成断言正确结果的正式回归，并验证采样、历史和导出保持一致。本次没有修改应用行为或对外发送 Issue 评论。

### 同类范围交叉检查

| 检查方向 | 结论 |
| --- | --- |
| V1／V2 未消费字节 | V1 单车第 11–12 字节有 Issue 的转数语义证据；V2 单车 8811 的附加字节、跳绳 8813 的保留位置没有等价证据，不因位置相似解释成转数或距离。 |
| 型号默认能力与显式能力 | V1 subtype 1 被固定表屏蔽；V2 的 8806 显式只写能力确实能覆盖型号默认值，并在后到的 8802 识别后保持，CONTROL-1 验证通过。 |
| 完全静默、单字段更新、独立心率 | 完全静默的 5 秒过滤有效；局部运动包存在 A04／A05；独立 2A37 心率不会刷新 `motionAt`，CONTROL-2 验证通过。 |
| 复位、暂停、重连、溢出 | 现有重新建立基线逻辑避免计入离线运动，CONTROL-3 验证通过；不能用无条件回绕修复破坏它。 |
| 小器材字段的存储链 | 跳绳次数、连续次数、中断次数；哑铃次数、重量、动作字段和设备时长已有 Metrics、SampleRow、Repository 与 Exchange 对应映射。本次未发现类似 AT-R 的字段映射缺失，累计回绕仍受 A07 约束。 |
| 历史速度与实时可选项 | `HistoryStore` 会重建旧协议虚拟速度，不能误报“报告没有速度”。椭圆机坡度已有解析、存储和按证据展示报告的路径，但 `MetricCatalog` 实时选择仍没有坡度，是可见能力差异，应按实际器材能力评估。 |
| 有意限制 | 文档明确规定距离结束阶段需要累计读数，不能拿虚拟积分替代；这与 A09 的“已有上报能力却被启动顺序挡住”不同。电机控制、休眠历史入口与未确认的 3209 自动选择仍按原有证据边界处理。 |

### 本次补查证据

本轮实际执行 13 个探针，0 失败／0 跳过；10 个观察编号及 3 个对照场景均成功复现。随后移除审计启动参数，重新执行常规应用测试 **97 项**，0 失败／0 跳过，并核实结果中没有审计探针。测试源码与运行命令保存在 `tools/protocol-audit/`，正常测试不会自动纳入这些“当前缺口仍存在”的断言。

开发卷的 `followup-evidence-index.json` 索引新增 6 份 REA 原始证据；`gap-audit-results.xml`、`gap-audit-validation.json` 和 `gap-audit-tests.log` 保留执行结果。两版新检查的 `setRPM`、`zeroMovementData$sportstatemachine_release`、`updateResistance` 行为一致。它们支持对厂商计算链的理解，不能取代硬件抓包；没有重跑官方 DEX、没有 BLE 实机结果。

## 已核对的协议与算法

| 范围 | 本轮结果 |
| --- | --- |
| 协议选择 | 两版 `BaseHandler.Companion.createHandler$mobidevice_release` 的优先级仍为 `8800 → FFE0 → 满足名称条件的 FFF0 → FTMS`；OpenMOBI 已覆盖。 |
| V1 | 检查订阅入口、AB03／AB04 通知处理、阻力写入。`MB-HW` 的 FFE4 延迟、阻力命令保留配置字节均已有实现。 |
| V2 | 检查 `handlerData`、解锁后控制及小器材交互。上传模式、型号能力、磁铁数、`8811/8813` 分工、阻力与小器材字段已有对应实现和 oracle 回归。 |
| HuanTong | 检查 FFF1 初始化、体重帧、FFF2 定时阻力帧和 BCD 频率；现有实现保留这些行为。 |
| MOBI FTMS | 两版阻力命令均为 `04 + 单字节(level × 10)`，并非标准三字节编码。当前厂商分支与通用 FTMS 分支的区分应保留。 |
| 单车／椭圆机 | `SportStateMachineToolKt.calculatePower` 的六系数指数模型、最高档位归一化、0B11 修正，以及 `MotionData.updateCurveData` 的逐秒计算档位过渡均已恢复。默认 type-0 热量使用未截断功率：非零时 `weightKg / 3600 + watts / 1000` kcal/s。 |
| 划船 | `RowingAnalysis` 的慢速切换与 `reportPull` 回查原始 Smali，支持现有 Kotlin 实现；不采用有歧义的 Java 分支改写算法。 |

这里的“已有回归”是本轮重新运行 OpenMOBI 测试、读取既有原 DEX 期望表；**本轮没有重新执行官方 DEX oracle**。原 DEX 请求数和历史实机回放仍引用[已有验证记录](PROTOCOL_VERIFICATION.md)，不计为新增实机证据。

## 不应误判为确定遗漏的项目

**RowingAnalysis3209 的自动选择。** 两份 APK 都含该类及 V2 的类型判断分支，REA 类引用追踪均返回 5 个 using nodes；对无参构造方法的追踪均返回 0。现有证据无法确认正常连接时如何实例化它。OpenMOBI 已有算法和对照测试，但不能仅凭相似型号名启用。静态引用为零也不能排除反射或动态加载。

**8815／8816 离线历史。** 旧 `BluetoothLeService.readHistoryData()` 确有跳绳／哑铃历史读取代码，但两版 `MobiDeviceModule.readHistoryData()` 都转到 `BluetoothService.readHistoryData$mobidevice_release()`，而后者为空方法；REA 对旧入口的静态调用追踪均为 0。旧协议可以继续研究，但这两份 APK 不能证明当前官方正常入口仍提供完整历史同步。

**厂商的边界行为。** 16 位心率字节序、过期数据、零值和截断帧不能只按反编译文本照搬。当前标准心率解析与若干数值保护是项目已有的明确选择；本轮没有据此更改电机控制、工厂改型、OTA 或默认计算模型。

## REA 输出的解释与复现

27 个选定方法在每版 Evidence 中均标记为 `source.status=complete`、`representation=java`。这只说明输出未截断，不能证明 Java 控制流准确：`RowingAnalysis.checkIsSlowMode` 和 `reportPull` 仍带 `JADX WARN: Code duplicated`，本轮新提取的 Smali 再次确认了前者的双样本慢速判定和后者的条件分支。工具返回 `fell_back=false` 不能取代字节码复核。

在此次 LXC 中可直接执行 `rea --version`。用户命令位于 `~/.local/bin/rea`，安装与引擎位于 `$DEV_TEMP_BASE/build/projects/openmobi-rea-20261018/`；运行临时目录沿用 `TMPDIR`，状态位于开发卷。安装时对 npm 使用了进程内 DNS 解析修正和原 npm 缓存下的独立子目录，未修改系统 DNS。

本地完整结果位于 `$DEV_TEMP_BASE/work/openmobi-rea-20261018/`：`evidence-index.json` 记录原始 Evidence ID、文件哈希、操作参数和 APK 身份，`validation.json` 记录验证范围；`international/`、`chinese/` 分别保存结果。复查单个方法可使用：

```bash
rea inspect-android-package 'apk/莫比健身 2.1.14.apk' --format json
rea inspect-android-class 'apk/莫比健身 2.1.14.apk' \
  com.anytum.mobi.sportstatemachine.SportStateMachineToolKt --format json
rea inspect-android-method 'apk/莫比健身 2.1.14.apk' \
  com.anytum.mobi.sportstatemachine.SportStateMachineToolKt \
  calculatePower --overload-index 0 --format json
```

切换 APK 后重新查看该版本的成员清单再选择重载。`calculateCalories(double,int,int)` 在此次两版清单中的索引均为 1。

本轮实际执行 `:core:test` **74 项**、强制执行 `:app:testDebugUnitTest` **97 项**，均为 0 失败／0 跳过。10 份 oracle 输入集及 16 张期望表的哈希也与 manifest 一致。没有进行真实 BLE 连接、设备机械控制、Android 界面测试或人体能耗／功率计标定；本机无 `/dev/kvm`，本轮未启动 Android 模拟器。

## English

The latest follow-up now executes all three APKs: 2,772 identical synthetic sequences and 21,382 steps per APK, with selected original MotionData listeners and 80 reordered repeatability probes. Fifteen application audit tests and 97 standard application tests pass. The audit tests reproduce existing gaps; they are not fixes. Partial updates retain stale speed across four equipment families, extreme FTMS cadence reaches stored samples, four Chinese rowing subtype names are absent, and 8804 is SignalAcquisitionScale rather than a log-only field. The official app also retains stale values in some zero-input paths, so vendor equality is not a correctness criterion. See the new differential section and runner for coverage, environment errors and boundaries. The following paragraphs describe the earlier static phase.

REA 6.5.0 and its pinned JADX headless 0.7.1 engine were installed in the existing Debian LXC, with tools, temporary files and retained evidence on the development volume. Both supplied APK hashes match the repository's reference manifest. The initial pass produced 49 REA evidence records per APK, including 16 class inventories, 27 method inspections and 3 reference traces. Ambiguous rowing methods were checked against fresh Smali from the original APKs.

The initial method-level comparison was insufficient to establish protocol completeness. The follow-up includes public Issue #1: unused AT-R revolution counts and a reported hardware capability that conflicts with the subtype table. Thirteen opt-in characterization probes reproduce stale-field retention across partial packets, distance integration during silence, stale stride persistence, conditional counter-wrap loss, zero-magnet fallback, startup ordering gaps and the missing battery callback. Passing these probes confirms the observed gaps; it does not mean they are fixed. Three controls cover explicit V2 capabilities, independent heart-rate freshness and counter rebasing. Three additional REA methods per APK bring the retained evidence count to 52 per APK.

The earlier diagnostic and rowing-detail findings remain applicable. AT-R mechanical resistance support and counter overflow behavior still require device evidence. The referenced Codex thread was unavailable because this session had no read_thread tool; only the public GitHub issue and comment were used.

Do not infer active support from dormant code: both current Bluetooth services have an empty history-read entry point, while references to the older implementation and to the RowingAnalysis3209 constructor were absent in the selected static traces. This does not exclude reflection or dynamic loading.

All 74 core and 97 Debug application unit tests passed in this run. The original DEX oracle was not rerun; its committed corpus and golden-table hashes were verified. No physical BLE, motor, UI or measurement-calibration validation is claimed. Application behavior remains unchanged by this audit.
