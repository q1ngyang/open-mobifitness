# 早期测试版本 / Earlier test releases

[当前正式版 / Current stable release](v0.3.1.md) · [v0.3.0](v0.3.0.md) · [v0.2.0](v0.2.0.md) · [v0.1.1](v0.1.1.md)

这些是早期测试版本的主要变化，**新安装请选正式版**。当时的功能和限制可能已改变，日常使用以当前[使用说明](../USER_GUIDE.md)和[设备支持](../COMPATIBILITY.md)为准。以下版本均支持 Android 10+、64 位设备；可使用同一发行签名覆盖升级。

These notes summarize earlier test builds. **Choose the stable release for new installs.** Features and limits have since changed; follow the current [user guide](../USER_GUIDE.md#english) and [device support](../COMPATIBILITY.md#english). All builds below support Android 10+ on 64-bit devices and use the same release key for in-place upgrades.

## v0.1.0-alpha.7

中文：记录趋势直接展开，首页列表改为最近六个月，全部记录按年／月查找。保存后显示本次详情；整理设置和设备排障，把日志导出统一到设置。已知 V1 热量改用官方功率＋体重模型，历史值不重算。首次分开发行普通版和可共存的 Debug 演示版。通过数据迁移、签名、模拟器界面与导出检查；当轮未连接实体器材。

English: Always-visible trends, a recent-six-month list, year/month browsing and details after saving. Settings and troubleshooting were reorganized around a single diagnostics export entry. Known V1 energy switched to the original power-and-weight model without recalculating history. Regular and separate Debug/demo APKs were introduced. Migration, signing, emulator UI and export checks passed; no physical equipment was tested that round.

## v0.1.0-alpha.6

中文：按官方方式统一档位百分比，24 档的 12 档为 50%。新增训练返回条、方案搜索／筛选／收藏、记录搜索和分页、趋势及单次详情，统计 CSV 改为可读报告。蓝牙报文默认开启。此版曾使用“归档”和可折叠趋势，后续已改为更直接的年月查找与常展开趋势。旧备份兼容，模拟器升级保留数据；当轮未做实机测试。

English: Corrected resistance percentages: level 12 of 24 is 50%. Added a return-to-workout banner, plan search/filters/favorites, history search/pagination, trends/details and readable CSV reports. Packet logging became on by default. Its archive action and collapsible trends were later replaced by year/month browsing and always-visible trends. Older backups stayed readable and emulator upgrades preserved data; no hardware tests were added that round.

## v0.1.0-alpha.5

中文：重新设计两级悬浮窗。小窗两项读数和阶段进度，大窗四项指标与固定调阻／暂停／返回按钮；数字等宽、长数值自动缩小，浅深色一致。改善拖动、边缘展开和低矮横屏滚动。保留既有指标选择和设置。经过模拟器窗口、六语言、自动悬浮、升级与签名检查，未验证真实蓝牙或厂商后台策略。

English: Redesigned both floating panels: two compact readings and stage progress, four expanded readings with fixed resistance/pause/return controls. Monospaced numbers shrink when long; light/dark styling, dragging, edge expansion and short-landscape scrolling improved. Existing selections/settings were retained. Emulator, language, automatic-panel, upgrade and signature checks passed; real Bluetooth and vendor background behavior remained unverified.

## v0.1.0-alpha.4

中文：名称统一为 OpenMOBI，通用指标改为“频率”。重做运动页计时、相邻阶段、手机分页与平板分栏，固定底部保存等操作。恢复已知 V1 的功率估算，增加常亮、自动悬浮设置、固定窗口排版及限时限量日志清理。394 条 MB-EP 历史报文用于回放，不能视为机械功率标定。采样和备份 v3 需 alpha.4 或更新版读取；仍支持旧格式导入。

English: Standardized OpenMOBI branding and the Cadence label. Reworked timing, nearby stages, phone metric pages, tablet columns and fixed bottom actions. Restored known V1 power estimates, keep-awake behavior, automatic-panel settings, fixed panel sizing and bounded logs. Replayed 394 historical MB-EP packets; this was not mechanical power calibration. Sample/backup v3 needs alpha.4 or newer to read, while older imports remain supported.

## v0.1.0-alpha.3

中文：修复 MB-EP 已连接但无数据、不能调阻：V1 状态头实际为 AB 04，之前误按 AC 04 识别。新增阻力滑块、更明显的指标配置，方案增至 21 套，并采用 MOBI FITNESS＋Open 浅深色图标。真实报文回放与模拟器流程通过，发布当时仍需验证器材实际调阻。报文记录在此版仍需手动开启。

English: Fixed the MB-EP connection-without-data/control bug: V1 status starts with AB 04, previously mistaken for AC 04. Added a resistance slider, clearer metric configuration, 21 plans and light/dark MOBI FITNESS + Open artwork. Real-packet replay and emulator flows passed; actual resistance execution still needed confirmation at release time. Packet recording was still opt-in in this build.

## v0.1.0-alpha.2

中文：独立全屏运动页、17 项可选指标、两级悬浮窗、阶段分秒倒计时与 18 套离线方案。增加热量等数据路径及本地诊断。CSV／备份升级 v2，仍可导入旧格式，旧 App 无法读取新格式。更新检查开始支持后续预发布；alpha.1 用户需手动下载升级。当时未完成实机验证，V1 解析故障由 alpha.3 修复。

English: Added a full-screen workout page, 17 selectable metrics, two panel sizes, stage countdowns, 18 plans, energy-related fields and local diagnostics. CSV/backups moved to v2 with old-import support; older apps cannot read the new exports. Prerelease discovery was fixed, so alpha.1 users needed a manual download. Hardware remained unverified; the V1 parsing failure was fixed in alpha.3.

## v0.1.0-alpha.1

中文：首个公开测试版，提供离线蓝牙连接、受支持的调阻、12 套原创方案、悬浮控制、本地记录和 CSV／ZIP 备份，支持六种界面语言。包名为 org.openmobifitness.app，可与官方 App 共存。仅完成协议／数据测试和模拟器检查，尚未验证实体器材。更新检查只查正式发行；若要升级早期 alpha 需手动下载。

English: First public test build with offline Bluetooth, supported resistance control, 12 original plans, a floating panel, local history, CSV/ZIP backups and six interface languages. Package org.openmobifitness.app coexists with the original app. Validation covered protocol/data tests and an emulator, not real equipment. Its updater checked stable releases only; early alpha upgrades required a manual download.

所有早期版本均不提供跑步机电机、速度、坡度或固件更新控制；部分旧划船机和 HuanTong 能力未恢复。内置方案为原创替代方案，不是找回的官方在线课程。

None of these builds provides treadmill motor, speed, incline or firmware-update control. Some older rower and HuanTong functions remain unavailable. Built-in plans are original replacements, not recovered official online courses.
