# OpenMOBI

**让莫比健身器材继续用起来。**

简体中文 · [English](README.en.md)

OpenMOBI 是一款独立开发的 Android 应用，让莫比健身器材在官方服务停止后仍能连接蓝牙、调节阻力、完成训练。无需账号，运动记录保存在本机。

## 下载

**[下载 v0.1.1 普通版](https://github.com/q1ngyang/open-mobifitness/releases/download/v0.1.1/OpenMOBI-0.1.1.apk)** · [所有版本](https://github.com/q1ngyang/open-mobifitness/releases) · [更新说明](docs/releases/v0.1.1.md)

支持 Android 10 及以上的 64 位手机和平板，可与官方 App 共存。

**日常使用请选择文件名不带 `debug` 的 APK。** Debug 版用于测试，包含演示设备，和普通版的数据互相独立。

目前只有 **V1 协议的莫比 MB-EP 椭圆机**有用户实机反馈。其他单车、划船机和跑步机仍需逐型号验证；当前不控制跑步机的启动、速度或坡度。[查看设备支持情况](docs/COMPATIBILITY.md)

## 可以做什么

- **离线运动**：自由调阻，或跟随 21 套内置方案；支持搜索、收藏和自定义训练。
- **边看边练**：全屏运动页和两级悬浮窗，按需选择显示的运动指标。
- **看见积累**：今日运动、历史记录、趋势与单次训练详情，支持 CSV 报告和本地备份。
- **适应你的屏幕**：手机、平板与折叠窗口布局，浅色／深色模式，六种界面语言。

## 界面

<img src="docs/screenshots/home-phone.png" alt="手机上的自由运动与训练方案" width="270"> <img src="docs/screenshots/workout-phone.png" alt="运动进度、可选指标与阻力控制" width="270">

<img src="docs/screenshots/home-tablet.png" alt="平板深色模式下的自由运动与今日统计" width="800">

模拟器实际截图，连接状态和运动数据为测试示例。[查看更多界面](docs/SCREENSHOTS.md)

## 连接异常？请告诉我们

**搜不到器材、连接后没有数据、调阻无效，都欢迎反馈。** 请到「设置 → 设备诊断」确认已开启「记录蓝牙协议报文」，再连接并复现问题，随后导出日志。

[提交问题](https://github.com/q1ngyang/open-mobifitness/issues/new/choose)时附上器材型号、手机／平板型号、Android 版本和日志。不确定怎么做？[查看分步指引](docs/HELP.md)。

## 更多说明

[开始使用](docs/USER_GUIDE.md) · [训练与数据估算](docs/TRAINING.md) · [隐私与权限](docs/PRIVACY.md) · [文档目录](docs/README.md) · [参与开发](docs/DEVELOPMENT.md)

OpenMOBI 与原厂没有隶属关系。新增代码采用 [Apache-2.0](LICENSE)；原品牌字标权利归其所有者，见[素材说明](docs/BRANDING.md)。作者：[q1ngyang](https://x.com/q1ngyang)。
