# v0.3.0 版本验证 / Release validation

2026-10-04。基线 `ef3496f`（v0.2.0），版本 `0.3.0`／versionCode `10`。本页记录本地身份、器材训练、Debug 数据和响应式界面的验证范围；最终界面细节见 [精修说明](V030_REFINEMENTS.md)。

[使用变化](V030_GUIDE.md) · [版本说明](releases/v0.3.0.md) · [数据格式](DATA_FORMAT.md)

## 已实现范围

| 工作包 | 实现与检查重点 |
| --- | --- |
| 本地身份 | UUID 档案、照片头像、文字回退、可选体重、个人偏好隔离；每次记录默认确认；暂停及保存失败继续锁定身份 |
| 数据与迁移 | Room 8、ZIP 8、sessions 6、samples 6、workouts 2、preferences 2、users 1；原有用户、未分配旧记录、提示归档；软移除、明确确认后删除、更正归属、认领旧记录 |
| 方案 | 四类大器材的编辑与兼容校验；两项提示独立继承／自定义／关闭；恢复与放松默认关闭；稳定阶段 ID、历史快照、进入阶段前缀 |
| R4 界面 | 紧凑用户范围与默认周概览；器材筛选；完整有效报告指标；1／2／3 列方案；大字体降列；圆环只从 2.5 dp 加宽到 4 dp，原几何与位置保留 |
| 悬浮与服务 | 原布局、品牌和调阻区域保留，状态与通知补充姓名；开始入口共用身份协调；方案提示共用会话限频 |
| 备份与导出 | 全部用户与头像、个人设置、记录、方案和归属历史；默认不覆盖本机档案；旧 owner 缺失不清空新归属；文件选择前固定范围、语言与单位 |
| Debug | 专用 source set，四档案／六器材／780 记录；自然月与固定锚点；幂等补缺、事务回滚、精确清理；普通版排除实现和专用资源 |

P1 的跳绳／哑铃分段编辑器与阶段达标率不在本版。没有增加跑步机电机、速度或坡度控制，也没有扩大未经验证的器材写入能力。

## 自动化结果

| 检查 | 结果 |
| --- | --- |
| Core JVM 测试 | 73 项通过 |
| Android Debug 单元／Robolectric | 90 项通过 |
| Android Release 单元／Robolectric | 84 项通过 |
| 六种语言 | 549 个正式资源键及 14 个仅 Debug 的资源键完整，格式占位符检查通过 |
| Lint | Debug、Release 均通过 |
| 构建 | Debug、签名 Release、Debug instrumentation APK 均成功 |
| APK 身份 | 普通版与 Debug 包名独立，版本号 10；两者均沿用 v0.2.0 对应证书 |
| 64 位与对齐 | 仅 arm64-v8a／x86_64；原生 ELF LOAD 与 ZIP 16 KiB 对齐通过 |
| 普通版排除样例 | 不含 DebugDataset／DebugSamples／DebugSampleStore 及 14 个专用资源键 |

共 247 项单元／数据测试，零失败、零跳过。包含全部受支持的 Room 1–7 升级、360,001 采样的大备份、780 条 Debug 数据完整备份往返、冲突与回滚、真实／其他演示记录保护、头像编码与篡改校验、头像删除中断后重试且不清理其他草稿、旧收藏迁移后的取消收藏，以及保存失败重试、暂停锁定、过期开始请求、恢复与开始互斥等边界。

报告测试覆盖时长加权、有效零、缺测断线、流式降采样保留极值、划船配速、器材语义和明确历史单位证据。数据层验证使用实际 Room／SQLite；传感器输入是合成数据，不属于 GATT 实测。

截图反馈后的 17 组增量原生复核、悬浮窗文字完整性回归，以及最终界面截图见 [本轮精修验证](V030_REFINEMENTS.md#本轮验证结果)。下表单独保留精修之前的 R4 阶段记录，避免混淆验证批次。

## R4 阶段原生界面

下表是本次截图精修前的 R4 验证。最新界面调整、训练库与补充验证见 [截图反馈精修](V030_REFINEMENTS.md)。

使用专用 API 34 软件模拟器，dp 尺寸按下表设置。测试未清理用户日用设备。页面、对话框和图片均来自 Android 原生渲染，不是设计稿代替实现。模拟器未启用硬件加速，本轮布局与流程结果不作为实机性能结论。

| 窗口 dp | 字体倍率 | 语言／主题 | 方案列数 | 页面矩阵 |
| --- | --- | --- | --- | --- |
| 320×640 | 1.0 | 英语／浅色 | 1 | 通过 |
| 360×780 | 1.0 | 简中／浅色 | 1 | 通过 |
| 390×844 | 1.3 | 日语／浅色 | 1 | 通过 |
| 390×844 | 2.0 | 德语／深色 | 1 | 通过，排版修正后复测 |
| 600×960 | 1.0 | 日语／浅色 | 1 | 通过 |
| 700×1000 | 1.0 | 韩语／浅色 | 2 | 通过 |
| 700×1000 | 1.3 | 韩语／深色 | 1 | 通过 |
| 840×900 | 1.0 | 繁中／深色 | 2 | 通过 |
| 1280×800 | 1.0 | 简中／浅色 | 3 | 通过，紧凑筛选栏复测 |
| 1280×800 | 2.0 | 德语／浅色 | 1 | 通过 |
| 780×360 | 1.0 | 英语／浅色 | 2 | 通过 |

另在 390×844、正常字体下实际创建方案和用户，检查默认开始确认、暂停身份锁定、不可恢复删除确认框、周概览、报告和软键盘。小屏正常字体的周概览完整进入首屏；短横屏和大字体使用自然滚动。首次检查发现软键盘遮挡表单操作区、大字体德语标题及标签断行，已修正并复测。平板圆环仍位于左侧自由运动介绍区上缘。

原生截图：[平板首页](screenshots/v030-home-tablet.png) · [手机周概览](screenshots/v030-history-phone.png) · [头像预览](screenshots/v030-avatar-preview.png) · [删除确认](screenshots/v030-delete-warning.png) · [Debug 清理预览](screenshots/v030-debug-cleanup.png)。截图使用明确的合成演示资料；报告与悬浮窗最终截图见 [界面精修](V030_REFINEMENTS.md)。

专项验证使用真实系统图片选择器完成本地选图、圆形预览、建档保存，核验私有 512×512 JPEG 资源；称呼及未填写体重状态保留。原悬浮窗完成四角拖动、旋转后位置恢复、收起／展开、调阻、记录／暂停／保存和返回控制模式，品牌布局及训练用户名正常。

原生阶段编辑另验证划船机以 60 桨结束、跑步机以 6 km/h 手动指导，保存后的类型与标准单位值正确，并且未出现不兼容的桨数／阻力项。Debug 按钮实际完成 780 条加载、跳转历史、预览确认和精确清理；清理后内置记录为零，清单外记录和当前训练用户保留。首次原生验证发现加载进度回调绑定错误，已修复并增加回归测试。旧版 instrumentation 并未整套重跑；本轮不能据此声称全部历史 UI 测试通过。

## 升级与边界

专用模拟器已分别完成同证书 Debug 和普通版 v0.2.0 → v0.3.0 覆盖安装，未用卸载重装代替升级。Debug 预置旧记录、方案、主题、收藏、73 kg 与旧目标桨频后，确认 Room 8 迁移保留记录；旧记录不自动分配；原有用户取得旧方案与个人设置；旧提示被归档。普通版升级保留原有记录和另行预置的旧记录／方案，确认旧记录未分配、旧方案取得原有用户归属，且 Debug 数据与设置未受影响。

R4 阶段签名产物另外通过覆盖安装与实际渲染启动检查，前后用户／记录／采样／方案数量一致；最终 Debug APK 的 2.0 倍德语深色页面矩阵复测通过。

真实 BLE 器材、本轮 Android 10 原生运行、厂商后台策略、长时间实机训练及人工 TalkBack／折叠屏验收尚未验证。模拟器和合成数据不能替代这些结果，本版不据此宣称新增器材已实测。

## English

Version 0.3.0 (version code 10) builds on v0.2.0 commit `ef3496f`. This page records validation of local identity, equipment-aware plans, Debug data and responsive layouts. See [interface refinements](V030_REFINEMENTS.md#english) for the final layout details.

The implemented scope includes local UUID profiles and avatars, isolated preferences, identity locking, typed plans and stage hints, historical snapshots, compact weekly history, full applicable reports, transactional migration/backup and the Debug-only 780-record dataset. The original floating-panel layout and branding remain. Ring geometry is unchanged; only stroke width increases to 4 dp. Small-equipment segmented editors, attainment scores and new treadmill control remain out of scope.

All 247 unit/data tests pass: Core 73, Debug 90 and Release 84, without failures or skipped cases. Debug/Release lint, builds and instrumentation compilation pass. Six locales contain all 549 main keys and 14 Debug-only keys. Tests cover old Room versions, large backups, dataset round trips and exact cleanup, avatar validation and restartable deletion, favorite migration, identity races, failed-save retry, query freezing and metric semantics.

Both APKs preserve their respective v0.2.0 signing certificates and distinct package IDs. Only arm64-v8a and x86_64 libraries are included; ELF and ZIP 16 KiB alignment pass. Release excludes the Debug dataset implementation and dedicated resources.

The 11-case native API 34 matrix above covers phone/tablet widths, short landscape, font scales 1.0/1.3/2.0, six languages and both themes. Native screenshots informed corrections to IME handling, German large-font text and wide-screen filter density. A separate normal-font phone flow checks plan/profile creation, confirmation, locked paused identity, deletion acknowledgment and reports. The system photo picker, circular preview and profile save produce a verified private 512×512 JPEG. The original overlay passes corner dragging, rotation/position restoration, collapse/expand, resistance adjustment, recording, pause/save and return to control mode. This does not claim the entire legacy instrumentation suite was rerun.

Both Debug and Release pass in-place v0.2.0 upgrades. The Debug upgrade preserves seeded legacy records and preferences, leaves old records unassigned, assigns legacy plans/settings to a stable original profile and archives old hints. Release preserves existing and seeded records/plans without changing Debug data or preferences. Physical BLE equipment, Android 10 native execution in this round, vendor background policies, long physical sessions and manual TalkBack/foldable checks remain unverified. Simulation cannot establish hardware behavior.

Native typed-editor checks save a 60-stroke rowing stage and 6 km/h manual treadmill guidance with correct standard-unit values. The Debug buttons load 780 records, open filtered history, preview and confirm cleanup, then leave zero built-in records while preserving unrelated records and the current training user. Native verification exposed an incorrectly bound load-progress callback; it was fixed and covered by a regression test.
