# 隐私与权限 / Privacy and permissions

[首页](../README.md) · [English](#english)

## 数据在哪里？

训练记录、方案和设置保存在本机，不需要账号，没有广告或使用统计服务。应用不会把器材注册到原厂服务器。系统云备份已关闭；请主动导出完整备份。卸载会清除应用数据。

常用设备的蓝牙地址、备注、快捷档位、个人范围和悬浮位置也保存在本机。导出时勾选「包含个人偏好」才会把这些设置加入备份；恢复时可按组选择。备份不包含系统权限或蓝牙配对凭据，恢复设备后仍须手动连接。

## 什么时候联网？

只有你检查更新、打开项目主页或提交问题时才访问 GitHub；作者链接会打开 X。网站会收到正常的访问请求信息，但 App 不会自动上传运动记录、设备标识或日志。网络不可用不会影响本地训练。

## 为什么需要权限？

- **附近设备／蓝牙**：搜索、连接健身器材与心率设备。
- **Android 10–11 的位置权限和定位开关**：这些系统版本的蓝牙搜索要求；应用不采集地理位置。
- **悬浮窗**：在其他应用上显示运动信息和控制按钮，不读取其他应用画面。可在设置中关闭。
- **通知与前台服务**：保持设备连接，并显示训练状态。
- **训练期间的唤醒锁**：帮助后台计时与阶段调度。运动页可见时保持屏幕常亮。

文件导出和导入均使用系统文件选择器，由你决定文件位置。

## 诊断日志记录什么？

记录连接状态、设备服务与能力、型号／固件、错误、App 和 Android 版本、手机厂商及型号。不会包含完整训练历史，也不直接记录蓝牙地址、序列号或设备名称；蓝牙地址会做文本脱敏。

「记录蓝牙协议报文」默认开启，保留你已明确关闭的选择。报文可能含实时运动、控制及心率数值。**分享前请检查文件内容**，日志不会自动上传，也不采集系统 logcat。

| 内容 | 本机保留 | 导出范围 |
| --- | --- | --- |
| 应用事件 | 最多 48 小时、64 KiB | 最近 24 小时 |
| 蓝牙报文 | 最多 30 分钟、32 KiB | 最近 30 分钟 |
| 完整诊断文件 | 按需导出 | 小于 128 KiB |

写入、启动或导出时清理过期内容。关闭报文开关会停止新增报文；已有内容到期删除，也可在「设置 → 设备诊断」手动清除。清日志不会删除运动记录。体重和估算参数只存本机，导出时可选择随个人偏好保存；每条运动记录仍保留其当时使用的估算参数。

## English

### Where is my data?

Workouts, plans and preferences stay on your device. There is no account, advertising or usage analytics service, and no equipment registration with the original servers. System cloud backup is disabled; export a full backup yourself. Uninstalling removes local app data.

Saved-device Bluetooth addresses, notes, resistance presets, personal bounds and floating positions also stay local. They enter a backup only when Include personal preferences is selected, and can be restored by group. Backups exclude system permissions and Bluetooth pairing credentials. Restored devices require a manual connection.

### When does the app go online?

GitHub is used when you check for updates, open the project page or submit an issue. The author link opens X. These sites receive normal request information, but the app does not automatically upload workouts, equipment identifiers or logs. Offline training continues when the network is unavailable.

### Why these permissions?

- **Nearby devices/Bluetooth:** discover and connect fitness equipment and heart-rate accessories.
- **Location permission and location switch on Android 10–11:** required by those Android versions for Bluetooth scanning; the app does not collect geographic location.
- **Display over other apps:** show workout readings and controls without reading other apps' screens. You can disable it in settings.
- **Notifications and foreground service:** maintain connections and show workout status.
- **Wake lock during a workout:** support background timing and stage scheduling. The visible workout screen stays awake.

Imports and exports use the system file picker; you choose where files go.

### What do diagnostic logs contain?

Connection state, services/capabilities, model/firmware, errors, app/Android versions, and phone manufacturer/model. They do not include complete workout history or directly record Bluetooth addresses, serial numbers or device names. Bluetooth addresses are redacted from text.

Record Bluetooth packets defaults to on and preserves an explicit choice to turn it off. Packets may contain live movement, control and heart-rate values. **Review files before sharing.** Logs are never uploaded automatically; system logcat is not collected.

| Content | Local retention | Export range |
| --- | --- | --- |
| App events | Up to 48 hours and 64 KiB | Last 24 hours |
| Bluetooth packets | Up to 30 minutes and 32 KiB | Last 30 minutes |
| Complete diagnostic export | Created on request | Under 128 KiB |

Expired entries are cleared during startup, writes or export. Turning packet recording off stops new entries; existing entries expire or can be cleared in Settings → Device diagnostics. Clearing logs does not delete workouts. Weight and estimate settings stay local and can be exported with personal preferences. Each workout record retains the estimate parameters used for that session.
