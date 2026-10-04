# v0.3.0 使用变化 / Feature guide

本页介绍 v0.3.0 的用户、方案与数据变化。[下载与版本说明](releases/v0.3.0.md) · [验证范围](V030_VALIDATION.md)

## 用户与记录

点击顶栏称呼选择或添加本地用户。第一步填写称呼，可浏览本地图片并确认圆形头像预览；取消预览保留原头像，没有图片时使用称呼字符。第二步可填写体重，也可以留空。高级 MET 参数仍可编辑。未知体重在需要估算时使用默认参数，报告会标明来源。

每位用户分别保存训练方案、收藏、主题、单位、指标、悬浮窗和个人档位。语言、已配对器材及诊断设置属于本机共用设置。两位同名的恢复档案仍是不同用户；选择列表以短身份编号辅助区分。

默认每次开始新记录前确认训练用户。设置中可改为每次 App 使用过程确认，或记住上次用户。正在开始、记录中、暂停和保存失败时保持用户锁定；继续训练不重复选人。切换用户会清空上一人的测量与独立心率上下文，需要重新连接的器材会给出提示。

记录页默认显示周概览，顶部用户范围、器材、来源和搜索共同筛选记录及导出。查看其他人的记录不会切换当前训练用户。“最近六个月”右侧选择的是器材。已结束记录可在报告中更正归属；开始时的姓名、参数、方案和测量保持原样。

旧版记录升级后保持“历史未分配”，可在用户管理中预览数量并认领。旧方案与个人设置归入“原有用户”。默认移除用户保留数据；勾选“移除用户并删除数据”后，还须勾选不可恢复确认。删除只处理确认时仍属于该用户的数据，不删除已经更正给其他用户的记录，也不删除另存的备份。

## 方案与报告

首页按内容宽度展示一、二或三列方案，放大字体时会减少列数。选择器材后浏览内置、个人或收藏方案，点击卡片查看详情；创建和编辑可离线完成。旧的通用方案可指定器材并保存副本，原方案保留。

椭圆机和单车使用时间／距离与踏频；划船机另外支持桨数和桨频；跑步机使用时间／距离、步频及手动速度指导。跑步机目标不发送电机、速度或坡度控制命令；划船调阻仍需已验证的器材能力。跳绳与哑铃本版使用自由记录。

方案默认频率和心率提示分别配置，每个阶段可分别继承、自定义或关闭。新恢复／放松阶段默认关闭提示。旧全局提示仅作为可主动导入的归档，不自动影响训练。训练中的静音保留视觉范围提示。

报告保留适用且实际有数据的均值、极值、曲线、有效覆盖和来源。有效零值继续显示，缺测不补零；历史能力与单位证据可保留兼容的附加指标。未记录的组数、阶段达标率或目标速度不会被当成实际测量。

## 备份与 Debug

完整备份一次包含所有保留的用户、头像、个人设置、方案、记录、采样与归属历史。恢复默认不覆盖已有档案和个人设置，不改变当前训练用户，本机移除状态优先。旧备份可读；v0.3.0 新备份需要兼容的新版本读取。导出在打开系统文件窗口前固定用户范围、语言及单位。

Debug 的“设备 → 演示 · Debug”提供显式加载和精确清理内置数据：四档案、六器材、36 个完整月份及边界案例，共 780 条记录。再次加载只补缺失，保留本地修改；清理前显示范围，并保留真实训练、其他演示和有关联的档案。普通版不包含这组数据或操作入口。两版数据各自独立，测试器材前先结束并断开另一版。

## English

This guide describes profiles, plans and data changes in v0.3.0. See the [downloads and release notes](releases/v0.3.0.md#english) and [validation record](V030_VALIDATION.md#english).

Use the header name to select or create a local profile. Choose a local image, review its circular preview, and confirm it; cancelling keeps the previous avatar. Without a photo, the name supplies the initial. Weight is optional; estimates identify default parameters when weight is unknown. MET remains editable in advanced profile options.

Profiles isolate plans, favorites, theme, units, metrics, floating-panel preferences and resistance presets. Language, paired equipment and diagnostics are shared. Restored profiles with the same name remain separate identities, with short IDs shown where selection could be ambiguous.

New recordings confirm the user by default. The policy can instead confirm once per app process or remember the previous user. Starting, recording, paused and failed-save states keep identity locked. Switching clears previous readings and the independent heart-sensor context; devices that require reconnecting show a prompt.

History opens to a weekly overview. Owner, equipment, source and search filters also apply to exports. Viewing another person's history does not switch the training user. Finished records can have their owner corrected without rewriting the original name, body parameters, plan or measurements. Legacy records remain unassigned until explicitly claimed. Legacy plans/settings belong to an original-user profile.

Removing a profile preserves data by default. Deleting its data requires choosing the destructive option and acknowledging the irreversible warning. Records already reassigned to someone else and separately saved backups survive.

Plan cards use one, two or three columns according to available width and font size. Editors support elliptical, bike, rower and treadmill semantics, including rowing strokes and manual treadmill guidance. No treadmill motor, speed or incline control is added. Jump rope and dumbbell use free recording in this version. Frequency and heart ranges independently inherit, override or disable plan defaults; new recovery/cooldown stages disable hints. Archived global hints require explicit import.

Reports retain applicable measured values, averages, extremes, curves, coverage and provenance. Valid zeros remain distinct from missing data. Compatible extra axes require historical unit evidence. Targets, unrecorded sets and unimplemented attainment scores are not presented as measurements.

Full backups include all retained profiles, avatars, personal preferences, plans, records, samples and ownership history. Existing-profile overwrite is off by default; restores preserve the active identity and prioritize local removal status. Older backups remain readable; new backups require a compatible new app. Export freezes user scope, units and language before opening the document picker.

Debug offers explicit loading and precise cleanup of 780 records covering four profiles, six equipment types and 36 complete months plus boundary cases. Reloading fills missing records without replacing edits. Cleanup protects real recordings, unrelated demos and referenced profiles. Release excludes this dataset and its controls. Disconnect the other variant before physical testing; their data is separate.

## 界面与训练库 / Interface and training library

悬浮窗、六指标布局、指标分组、设备与设置分栏的最终规则见 [精修说明](V030_REFINEMENTS.md)。内置训练库扩充为四类器材共 70 个方案，参考材料、适用边界及器材差异见 [训练库说明](BUILTIN_WORKOUTS.md)。

See [interface refinements](V030_REFINEMENTS.md#english) for floating panels, six-reading layouts, grouped reports and balanced device/settings columns. The library contains 70 plans across four equipment types; [training-library notes](BUILTIN_WORKOUTS.md#english) explain sources, equipment differences and limits.
