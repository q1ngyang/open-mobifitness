# 开始使用 / Getting started

[首页](../README.md) · [English](#english)

## 安装与连接

1. 从[下载页](https://github.com/q1ngyang/open-mobifitness/releases/latest)选择不带 `debug` 的 APK。需要 Android 10 或以上的 64 位设备。
2. 关闭官方 App 或其他正在连接器材的应用，唤醒器材，并打开手机／平板的蓝牙。
3. 在 OpenMOBI「设备」页搜索并连接，允许系统提示的蓝牙权限。Android 10–11 还需要位置权限及系统定位开关；App 不记录位置。
4. 连接后轻踩几秒，确认频率、当前阻力有反馈，再试着升降一档。若没有反馈，先看[连接帮助](HELP.md)。

普通版、Debug 版与官方 App 可以共存，但请让器材一次只连接一个应用。Debug 版包含演示设备，不能用它的演示结果判断真实器材是否兼容。

## 仅控制或开始记录

连接后，在首页自由运动卡片选择「仅控制」，即可查看实时指标、调阻和使用悬浮窗；这个模式不保存记录，今日统计也不会增加。需要保存时点「开始记录」，保留当前连接和档位，从新的计数基准开始。自由记录结束保存后回到仅控制，已保存的记录可在「记录」页查看。

一台主运动器材可以另接一台心率配件。「设备」页保留连接成功的常用设备，可加备注或移除；点设备后才会连接，不自动套用上次档位。断连会暂停记录，重连后确认读数，再主动继续。

阻力区可按当前设备保存最多四个常用档位，编辑不会立即调阻。没有档位回报的设备会明确标为设定值；待确认请求与实际阻力分开显示。

## 开始一次训练

- 在「训练」页选择自由运动，或搜索内置方案。收藏的方案会优先显示；也可以添加自己的训练。
- 运动中用加减按钮或滑块调阻。跟随方案时，手动调阻会退出自动模式，之后可以重新打开「自动调阻」。
- 档位以器材反馈为准。百分比是当前档位除以最高档位，例如 24 档器材的 12 档是 50%，不是个人运动强度的百分比。
- 点击「显示设置」挑选运动指标；部分指标需要器材提供数据，没有数据时显示 `—`。
- 方案训练点击「结束并保存」后可查看本次详情；自由记录保存后回到仅控制。暂停和结束只管理 App 内的训练，**不等于让器材停机**。

## 一边看视频，一边运动

到「设置 → 悬浮窗」允许显示在其他应用上层。悬浮功能与自动显示默认开启，可以在这里关闭。

仅控制或记录时切到桌面或其他应用会显示小窗；点小窗展开控制面板，可调阻、暂停或返回运动页。拖动面板可移动位置，横屏和竖屏分别记住相对位置；可在悬浮设置恢复默认位置。运动页、小窗和大窗的指标在「设置 → 显示内容」分别选择。

## 可选的个人提示

到「设置 → 个人提示」手动填写频率或心率上下限；上下限可以只填一侧，默认关闭。频率按器材分别设置，单位随器材变化。提示音、震动可以分别开启，使用中可以本次静音。暂停记录、断连、缺测或退出时停止提示。

提示只比较你填写的范围，不推算个人区间、不评价训练效果，也不自动调阻。原来的目标桨频仍是独立的单一目标值。

## 查看记录与备份

- 首页「今日已记录」统计今天已保存的非演示训练，点它可进入记录。
- 「记录」可搜索、按设备筛选，并查看周／月／年趋势。列表默认显示本月及之前五个月，较早的记录在「全部记录」按年／月查找。
- 点击一条记录查看时长、热量、距离、平均／峰值和曲线。内容取决于器材提供的数据。
- 「记录 → ⋮ → 导出统计 CSV」适合用表格软件查看；它不是备份文件，不能重新导入。
- 换设备或重装前，到「设置 → 数据管理 → 导出完整备份」。备份可包含偏好。在新设备同一页面选择「导入文件」，预览后选择需要覆盖的偏好组；重复记录跳过，内容冲突则取消导入。卸载会删除 App 内数据。

热量、功率、虚拟距离上的 `≈` 表示估算。可在「设置 → 热量估算」调整体重；具体来源见[训练说明](TRAINING.md)。

## 更新

到「设置 → 关于 → OpenMOBI」打开关于页并检查更新，或直接访问[下载页](https://github.com/q1ngyang/open-mobifitness/releases/latest)。安装同一类型的新版本可保留原数据，无需先卸载。普通版与 Debug 版的数据不会自动互相复制。

## English

### Install and connect

1. Choose the APK **without `debug`** from the [download page](https://github.com/q1ngyang/open-mobifitness/releases/latest). You need Android 10 or later on a 64-bit device.
2. Close the original app or any other app connected to the equipment. Wake the equipment and enable Bluetooth on your phone/tablet.
3. Search and connect on OpenMOBI's Devices tab. Allow Bluetooth permissions when prompted. Android 10–11 also requires location permission and the system location switch; OpenMOBI does not record your location.
4. Move gently for a few seconds and look for cadence and resistance readings, then try changing resistance by one level. If readings are missing, see [connection help](HELP.md#english).

The regular, Debug and original apps can coexist, but only one app should connect to the equipment at a time. Simulated equipment in the Debug app cannot confirm compatibility with real hardware.

### Control only or record

After connecting, choose Control only on the home card to view readings, adjust resistance and use floating panels without creating history. Start recording retains the connection and level, using a new counter baseline. Finishing a free recording returns to Control only; the saved workout is available in History.

Use one primary machine and an optional heart-rate accessory. Successfully connected devices appear in Devices, with optional notes and removal. Tap to connect; saved levels are never applied automatically. A disconnect pauses recording; reconnect, check the readings and resume explicitly.

Save up to four resistance presets for the current equipment. Editing presets does not change resistance. Devices without resistance feedback show the commanded value explicitly; pending requests stay distinct from actual feedback.

### Start a workout

- Choose Free workout or search the built-in plans. Favorites appear first. You can also create your own plans.
- Use the buttons or slider to change resistance. A manual change turns off automatic resistance; you can turn Automatic mode back on.
- The current level comes from equipment feedback. The percentage is level divided by maximum level: 12 of 24 is 50%. It is not a percentage of your personal exercise intensity.
- Use Display settings to choose metrics. A metric shows `—` when the required data is unavailable.
- Finishing a plan offers its details; finishing a free recording returns to Control only. Pause and finish manage the workout in the app; **they do not stop the equipment's motor**.

### Watch while you exercise

In Settings → Floating panel, allow display over other apps. The panel and automatic display are on by default and can be switched off here.

Going home or switching apps during control-only use or recording shows the compact panel. Tap it for resistance controls, pause and return-to-workout actions; drag to move it. Relative positions are remembered separately for portrait and landscape; reset them in Floating panel settings. Choose metrics separately for the workout screen and both panel sizes in Settings → Display settings.

### Optional personal hints

Enter your own frequency or heart-rate bounds in Settings → Personal hints. Either bound may be omitted; hints are off by default. Frequency settings and units follow the machine type. Sound and vibration are optional, with a mute action for the current use. Pausing a recording, losing readings/connection or exiting stops hints.

Hints only compare readings with your entered range. They do not infer personal zones, score a workout or adjust resistance. The existing target rowing cadence remains a separate single target.

### History and backups

- Recorded today counts saved, non-demo workouts from the current day. Tap it to open History.
- Search history, filter by equipment and view weekly/monthly/yearly trends. The list shows this month and the previous five months. Use All records to find older workouts by year/month.
- Open a workout for duration, energy, distance, averages, peaks and charts. Available details depend on the equipment's readings.
- History → ⋮ → Export summary CSV creates a readable spreadsheet report. This report cannot be imported as a backup.
- Before reinstalling or moving devices, use Settings → Data management → Export full backup. Preferences can be included. On the new device, choose Import file, review the preview and select the preference groups to replace. Identical records are skipped; conflicting content cancels the import. Uninstalling removes the app's local data.

`≈` marks estimated energy, power or virtual distance. Adjust your weight in Settings → Energy estimates; see [workout notes](TRAINING.md#english) for data sources.

### Updates

Open the OpenMOBI row under Settings → About to check for updates, or visit the [download page](https://github.com/q1ngyang/open-mobifitness/releases/latest). Install a newer APK of the same app type over the existing one to keep your data. The regular and Debug apps do not copy data between each other automatically.
