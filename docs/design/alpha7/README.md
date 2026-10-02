# Alpha 7：记录、设置与连接支持

先通过内置 imagegen 生成三张设计稿，再实现原生 Compose 界面。完整提示词见 [prompts.md](prompts.md)。生成稿仅为布局参考；实际页面纠正了占位设备名称、链接、日志保留时间和导航项目，保留已批准的启动图标与运动页面。

- [记录与训练结束详情设计](records.png)：趋势常展开；最近六个月列表；独立年月查询；结束后展示已保存记录。
- [设置与诊断设计](settings.png)：偏好、训练、数据与支持、关于分组；低频配置进入独立页面或选择框。
- [设备与排障设计](devices.png)：连接、数据接收与控制能力分别呈现；排障点击后展开；统一前往设备诊断。

## 操作与数据约定

记录首页下半部分固定显示当前月及之前五个日历月，截至今天结束。上半部分的周、月、年、全部控制统计周期，不再把旧记录隐藏。搜索、设备与来源过滤共同作用于统计和列表。「全部记录」进入独立列表，可选择全部年份、某年全年或某个月；每页 20 条，旋转后保留筛选和页码。

旧备份的 `archived` 字段继续兼容、保留，但当前界面不再用它隐藏记录。所有旧记录均可通过年月查找；没有删除、迁移到另外的库或自动压缩数据。

结束训练在数据库保存成功后弹出真实详情，复用记录详情的统计、曲线及导出能力。弹窗支持滚动；平板为居中宽窗口，手机为近全屏窗口。保存失败保留训练，不显示成功；旋转和进程恢复后可继续查看，关闭后不重复弹出。

设置首页只显示入口和当前选项摘要。语言、外观、单位用单选窗口；显示内容、悬浮窗、数据管理、设备诊断和关于使用独立页面。设备诊断是唯一日志导出入口。设备页的帮助入口只引导到诊断页和 GitHub 问题表单，不自动上传或发帖。

诊断文案按真实实现核对：报文默认开启，用户显式关闭的选择保留；事件保留 48 小时 / 64 KiB，报文 30 分钟 / 32 KiB，导出仅取最近 24 小时事件且总量低于 128 KiB。报文可能含即时运动和心率数据，应在分享前检查。

普通版没有演示入口且控制器拒绝启用；模拟信号生成实现只在 Debug 源集中。Debug 包名 `.debug`、名称 `OpenMOBI Debug`，与普通版并存，数据库与偏好各自独立。历史演示数据仍有明确标签。更新检查打开同一个 Release 页面，页面和关于说明分别指明两种 APK。

通知与系统单色图标使用批准品牌的 Open 胶囊和 MOBI 字形的简化矢量；通知展开时显示批准的完整品牌图。已删除初版 `ic_mark`，启动图的浅深色位图保持不变。文字轮廓来自 DejaVu Sans Bold，许可见 [DejaVu-license.txt](DejaVu-license.txt)。

## 验证

自动化用合成训练和连接状态检查页面，不以这些截图声称真实硬件兼容。窗口矩阵、升级和导出结果见 [测试说明](../../TESTING.md)。


## 实装截图

以下均为 Android 模拟器实际截屏，训练和连接状态为测试夹具，不代表真实器材验证。

| 页面 | 手机 | 大屏 |
| --- | --- | --- |
| 设置 | [手机设置](phone-settings-actual.png) | [平板横屏深色](tablet-settings-actual.png)、[近方形繁体中文](fold-settings-actual.png)、[德语大字体](narrow-dark-settings-actual.png) |
| 设备 | [设备状态](phone-devices-actual.png)、[普通版未连接](release-devices-actual.png)、[普通版无演示入口](release-devices-bottom-actual.png)、[按需排障](phone-help-actual.png)、[手机横屏](phone-landscape-devices-actual.png)、[横屏完整排障页](landscape-help-actual.png) | [平板设备页](tablet-devices-actual.png)、[日语低矮横屏](low-landscape-devices-actual.png) |
| 支持 | [设备诊断](phone-diagnostics-actual.png)、[关于与项目链接](phone-about-actual.png) | |
| 记录 | [手机趋势与首条记录](phone-history-actual.png)、[年月查询](phone-all-records-actual.png)、[德语大字体日期轴](narrow-history-actual.png) | [平板趋势与最近记录](tablet-history-actual.png)、[平板竖屏](tablet-portrait-history-actual.png)、[平板年月查询](tablet-all-records-actual.png) |
| 通知 | [系统通知展开](notification-actual.png) | |
| 结束结果 | [保存后自动打开](phone-result-actual.png)、[德语大字体结果](narrow-dark-result-actual.png) | [近方形结果窗口](fold-result-actual.png) |

窄屏的设置值使用单行摘要，较长标签允许换行；结果窗口固定关闭入口，内部详情可滚动。低矮横屏设备页使用两列独立滚动区域，附近扫描不再被连接卡片挤到下方。手机趋势图保持展开，通过更紧凑的图表和说明减少占高；「全部记录」仅显示筛选合计与列表，大屏居中限制阅读宽度。
