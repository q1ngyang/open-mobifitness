# 验证与设备反馈

## 自动检查

```sh
python3 tools/check_resources.py
./tools/build.sh :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
```

协议测试覆盖 V1 状态模板和磁阻椭圆机双脉冲踏频、V2 数字位数字段、FTMS SINT16、能力范围、可选字段与截断数据包；数据测试覆盖 CSV 特殊字符、公式文本、备份往返、去重、冲突事务回滚、中断恢复。

`ExerciseFlowTest` 在 Android 上使用明确标注的模拟器材运行完整训练 → 悬浮面板 → 返回运动页 → 保存记录。它不验证真实 BLE 或电机行为。运行前仅对测试应用授予悬浮权限：

```sh
adb shell appops set org.openmobifitness.app.debug SYSTEM_ALERT_WINDOW allow
./tools/build.sh :app:connectedDebugAndroidTest
```

`LanguageLayoutTest` 在运动中切换全部六种语言，检查 Activity 重建后的界面文本与同一个会话 ID，并保存运动页截图。可通过 instrumentation 参数 `layout` 标记截图尺寸。

## 0.1.0-alpha.1 验证结果

- 10 项核心测试、3 项 Room / 备份测试通过；Android Lint 无错误，签名 release 构建通过。
- Android 14 / x86_64 模拟器上的悬浮控制面板往返与记录保存测试通过。
- 同一模拟器分别在 320 × 640 dp 手机窗口、1024 × 768 dp 平板窗口完成六语言切换测试；手机 130% 字体下也完成六语言检查。
- 检查了实际截图并修正窄屏控制按钮、德语长标签、数值字号与大字体指标换行。
- 发行包仅包含 arm64-v8a / x86_64 原生库，APK ZIP 与 ELF LOAD 段均通过 16 KiB 对齐检查；发行签名通过 `apksigner verify`。

这些结果来自模拟数据和 Android 14 模拟器。Android 10、Android 16、小米 / vivo 后台管理、实体器材 GATT 读写、真实铰链状态仍待实机验证。编译目标版本和静态检查不能替代这些测试。

## 首轮实体测试

器材：白色经典款 MB-EP 系列椭圆机。手机：小米 14 / Android 16；平板：iQOO Pad 2 Pro / Android 14。

1. 安装签名 alpha；可先启用飞行模式后单独开启蓝牙，确认无需账号和网络也能进入。
2. 断开官方 App，搜索并连接器材。记录设备页的协议、阻力范围，以及静止／运动时踏频和阻力显示。
3. 若控制可用，以相邻档位测试一次增加和一次降低，核对体感与显示反馈；不要先运行自动高低交替。
4. 在低强度手动训练中切换悬浮面板，打开视频 App，返回运动页，核对计时、连接和记录没有重置。
5. 暂停计时、继续、结束保存；导出 ZIP，再导入，确认不会重复记录。
6. 若无法识别或反馈不符，在设备页导出匿名诊断。需要的是实际 GATT／能力信息，不是反复尝试不明控制命令。

无实体折叠屏：只验证不同窗口尺寸、旋转和配置变化，不声称已通过实体折叠铰链或厂商系统验证。其他器材同样需要逐型号补充真实反馈。
