# v0.3.0 界面精修 / Interface refinements

本页记录 v0.3.0 的响应式界面规则，保留品牌、首页圆环位置与多用户数据规则。安装入口见 [版本说明](releases/v0.3.0.md)。

后续修正：[v0.3.1](releases/v0.3.1.md) 已将报告功率改为折线图，并补齐底部退出入口、整理方案顺序；下文保留 v0.3.0 的验证范围。Later changes: [v0.3.1](releases/v0.3.1.md#english) restores power lines, adds the missing exit action and groups related plans. The validation scope below describes v0.3.0.

## 最终设计规则

1. **小悬浮窗**：姓名放在标题栏、展开按钮左侧；删去重复整行状态。底部以蓝色胶囊展示控制／阶段状态，以琥珀色展示尚未记录提醒。保留 OpenMOBI 品牌和拖动区域。
2. **大悬浮窗**：统一标题姓名与底部状态；阻力名称、模式徽标、减号、档位、百分比、加号在一行。最多六项指标，随实际选择数量、记录状态、字号增减高度；高度受屏幕可用区约束，短屏仍能滚动读数并保留固定操作栏；大字号单列时，奇数项不会保留空白读数行。靠近边缘时调整尺寸保持该侧锚点。
3. **运动界面**：足够宽时每页三列两行；中等宽度平板可用两列三行；窄屏或大字体采用较少列与分页；近方形窗口的窄栏使用两行分页，防止第三行被操作栏挡住。平板竖屏缩短进度卡片，使六项指标和常用档位尽量同时进入首屏。自由记录默认六项，既有用户选择保持。页码按“〈 1 / 1 〉”排列，数字位于左右按钮之间；底部圆点使用 28 dp 区域，上下对称留白；只有一页时显示 1 / 1，翻页按钮置灰。每格为名称与读数两层；数值和单位同一基线，单位紧邻数值右侧，间距 6 dp。常规平板采用 14 sp 名称、36 sp 数值、15 sp 单位。依据当前字体的数字宽度和读数格式预留空间，数值在预留区内右对齐；同位数更新时单位稳定，跨位数仅扩展一次，回落或缺测不收窄。更换窗口尺寸、字体或训练记录后重新适配。自由记录与训练方案共用读数排版，移除不必要的第三行。
4. **阻力卡片**：自动调阻文字与开关作为一组，信息按钮相邻；大字号时整组换行，自动模式标签按实际可用宽度排版。大字号阶段目标允许两行，保留档位百分比。缩短档位控制区，常用档位固定单行，按按钮数量平分可用宽度。无阻力能力的划船机／跑步机显示本阶段目标与操作说明。
5. **训练报告**：同一指标的平均、最大、最低放在独立浅底分组内；空间足够三列，大字号整组纵排。继续保留全部适用且有数据的指标，统计 CSV 内容不变。默认选择有数据的功率；无功率时回退至其他有效指标。功率以时间段采样峰值柱状图展示，空数据段不补值，完整均值／最大／最小仍取原统计。点击柱体选择该时间段峰值并将指示点对齐柱中心，点击缺测空档不选中相邻样本。
6. **方案详情**：未连接与器材不匹配显示不同的明确原因，点击关闭详情并进入设备页。器材说明按类型展示，不再在单车详情泛化显示跑步机提示。默认范围与旧提示导入预览分别注明频率、心率，避免两条无标签的“范围提示关闭”。
7. **设备页**：宽屏左侧为主器材与已保存器材；右侧为附近设备、心率配件与连接帮助。Debug 版右侧另有演示区，因此将附近设备放到左侧以保持两栏均衡。窄屏顺序阅读，按字体尺度决定是否分栏。
8. **设置页**：左侧组织个人设置、训练设置、本机用户；右侧组织共享语言、数据支持、关于。保留顶部当前用户卡。
9. **训练库**：70 个器材专属内置方案，详见 [来源与编排说明](BUILTIN_WORKOUTS.md)。旧记录始终读取开始训练时的方案快照。

## 原生界面截图

原生界面：[平板运动](screenshots/v030-metric-units-tablet.png) · [自由记录](screenshots/v030-metric-units-free.png) · [平板竖屏](screenshots/v030-metric-units-portrait.png) · [近方形窗口](screenshots/v030-metric-units-fold.png) · [两倍字体](screenshots/v030-metric-units-large-font.png) · [指标分组报告](screenshots/v030-refined-report-tablet.png) · [设备](screenshots/v030-refined-devices-tablet.png) · [设置](screenshots/v030-refined-settings-tablet.png)。

截图来自 Android 14 模拟器，使用明确标注的合成演示资料，不代表真实器材测试。

## 界面复核要求

复核同时检查正常、禁用和翻页后的状态，不能只看初始截图。运动卡片检查文字真实排版边界、数字与单位基线、邻近间距、比例字体下连续更新及跨位数防抖、页码顺序、圆点留白；常用档位检查四个按钮同排及其完整边界，不能仅凭“节点可见”判定按钮没有被底部操作栏遮挡。字体放大时允许自然滚动和减少每页指标，保留可读字号与操作触达。

本轮自检还调整了平板竖屏进度区的高度、近方形窗口的两行分页和进度区占比、大字号德语自动调阻标签与阶段目标换行，以及大字号悬浮窗奇数项后的空白行。悬浮窗品牌按完整字标预留宽度，大字号状态胶囊允许自然换行，阻力名称与模式获得相应宽度；新增原生字体排版回归检查，防止品牌、状态和模式出现省略号。报告柱状图点击按对应时间段选取真实峰值，缺测空档不选中相邻采样。

## 单位邻近与防抖补充复核

数值和单位使用同一基线、6 dp 间距；单位随读数格式预留的数字空间排列。真实 Android 比例字形测试先验证数字字宽确有差异，再检查连续变更、99.9／100.0 往返、短暂缺测、窗口变窄及较长本地化配速。普通读数使用更大字号，较长读数按实际宽度适配。系统大字体采用实际文字高度计算行高，避免把整行留白也按字体倍率放大。

六组窗口／字号组合及一组比例字体回归通过。最终安装包补跑比例字体、平板横屏和两倍字体；其余四组 1.0 字号窗口保留同轮行高迭代前的结果（这些窗口的行高下限未变）。Debug／Release 构建、Lint、资源与签名校验通过。

补充截图：[手机竖屏](screenshots/v030-metric-units-phone.png) · [手机横屏](screenshots/v030-metric-units-landscape.png)。

## 本轮验证结果

2026-10-04 完成 247 项单元／数据测试（Core 73、Debug 90、Release 84），零失败、零跳过；Debug／Release Lint、签名打包、六语言资源检查通过。两版安装包分别沿用 v0.2.0 的签名，64 位原生库及 ZIP 16 KiB 对齐通过，普通版不包含 Debug 样例实现与专用资源。

原生界面采用增量复核：六组页面检查、六组运动布局检查、四组悬浮窗检查及一组器材目标／不匹配入口检查，共 17 组通过。修改过的区域补跑，未受影响的检查保留原始构建结果；这不表示 17 组都在同一次构建上重新执行，也不表示全部旧版 UI 测试重跑。

| 窗口 dp | 字号／语言 | 实际检查 |
| --- | --- | --- |
| 1280×853 | 1.0／简中 | 页面、自由记录、六项运动指标、分页操作、常用档位、悬浮窗 |
| 800×1280 | 1.0／简中 | 页面、竖屏进度区、六项指标和常用档位首屏显示 |
| 390×844 | 1.0／简中 | 页面、运动分页与滚动、单排常用档位、悬浮窗 |
| 780×390 | 1.0／英语 | 短横屏页面、运动布局、悬浮窗内容滚动及固定操作栏 |
| 700×840 | 1.0／日语 | 近方形页面、两行指标分页、紧凑阶段预览、按钮完整露出 |
| 390×844 | 2.0／德语深色 | 页面、运动文字排版、完整模式标签、阶段百分比、悬浮窗品牌与状态、滚动后调阻和开始记录 |

悬浮窗分别检查两项、三项、四项、六项指标及开始记录后的实际窗口；同一行指标数量不变时窗口不会随读数变化而抖动。截图：[小窗](screenshots/v030-refined-overlay-compact.png) · [两项大窗](screenshots/v030-refined-overlay-expanded-2.png) · [六项大窗](screenshots/v030-refined-overlay-expanded-6.png)。

方案详情明确显示未连接或器材不匹配原因，并区分划船目标、跑步机手动指导和兼容器材的阻力控制。

模拟器截图验证不扩大已有硬件兼容性结论；物理折叠屏铰链、厂商悬浮窗策略及其他器材的蓝牙写入仍以真机证据为准。

## English

The refinement preserves OpenMOBI branding and the approved home-ring placement. Live metrics use a previous/page/next header, balanced dot spacing and larger units beside the value on its baseline. A measured digit-width reservation keeps units close and stable even with proportional fonts; it expands at digit boundaries without oscillating back on smaller or missing readings. Quick resistance levels remain in one row. Portrait and near-square layouts reserve space for usable controls; large fonts use fewer readings per page and natural scrolling.

Floating panels remove repeated identity/status text, retain the complete wordmark, distinguish unrecorded state and adapt their height to the selected metrics. Manual/automatic mode words remain intact. Reports group averages and extrema by metric and initially select available power, with gap-aware peak bars. Plan details explain equipment mismatches and label frequency and heart-rate hints separately. The built-in library contains 70 equipment-specific plans with documented sources and limits.

All 247 unit/data tests, both variants' lint and packaging checks pass. Seventeen incremental native checks cover the six window/font configurations above; changed areas were rerun, while unaffected earlier checks retain their original build results. A further six-window/font pass and proportional-font regression check cover adjacent unit placement; the final build reruns the font regression, landscape tablet and two-times font cases. Four unaffected normal-font windows retain the results from before the final row-height adjustment. Screenshots show native Android rendering with synthetic demo data. Simulator results do not establish physical hinge behavior, vendor overlay policies or new Bluetooth compatibility.
