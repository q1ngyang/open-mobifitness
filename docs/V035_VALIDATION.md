# v0.3.5 连接审阅与验证 / Connection review and validation

[版本说明](releases/v0.3.5.md) · [设备支持](COMPATIBILITY.md) · [v0.3.4 协议验证](V034_VALIDATION.md)

## 依据与范围

本轮以 v0.3.4 及独立连接测试版的代码、已有用户实机日志为依据，按用户要求以代码审阅和自动化回归完成后续验收，不新增实机测试。

已有实机反馈：小米 Android 16、V1 椭圆机的首次连接、三轮重新连接及重新搜索后连接均成功；第三轮随后在器材仍开机、应用切后台／锁屏期间出现报文停顿及断开。原 vivo 的恢复后日志没有记录完整失败过程，且未在原机复测。不能由这些日志证明所有手机、型号或固件的后台行为已实测解决。私人日志和设备地址不提交仓库。

## 审阅发现与修复

| 范围 | 缺口 | 修复与回归入口 |
| --- | --- | --- |
| 所有器材、心率带 | 断开后仍保存地址，附近列表无条件排除该地址 | 仅排除仍连接／连接中的地址；`ConnectionRecoveryTest` |
| 所有连接 | 保存地址直接连接，缺少重新发现、阶段期限及失败恢复 | 扫描后连接、连接调用放到 IO、阶段及总期限、一次恢复；迟到回调关闭旧 GATT；`ConnectionRecoveryTest` |
| 后台重连 | 无过滤扫描在熄屏时可被 Android 暂停 | 目标地址过滤，保留有界直接连接后备路径；测试检查实际传给扫描器的过滤条件 |
| 器材与心率带并用 | 两个客户端分别计算扫描额度 | 控制器共享额度与系统限流后的冷却期；跨客户端回归 |
| 仅连接、训练、暂停及心率带 | 唤醒保护取决于训练状态及主器材 ready，未覆盖其他连接阶段／用途 | 前台服务按两个真实连接的生命周期持有、续期并释放唤醒；`BackgroundConnectionTest` |
| 服务失败与退出 | 服务失败仅提示通用权限错误，可留下无后台保护的 GATT | 记录异常、关闭两条连接、暂停未结束记录、显示明确提示；测试覆盖启动拒绝及服务销毁 |
| V2、FTMS 等多通知设备 | 任意通知成功即认为订阅成功，电量／状态可能掩盖运动通知失败 | 要求实际运动通知成功；V2 档位／负重反馈不替代运动通知；失败关闭，静止但订阅成功则继续等待 |
| 排队读写与连接替换 | 等到取得队列锁后才获取 GATT，旧任务可能使用新连接 | 入队前绑定连接，执行前再次校验；排队折叠状态读取回归 |
| 各协议运行期写入 | 非权限类 GATT 异常可能仅终止协程 | 统一记录、结束连接并清理控制等待状态；故障注入回归 |
| 后台问题诊断 | 无法关联应用、服务、锁屏及省电状态 | 增加有界生命周期日志与导出状态；不包含设备地址或完整运动历史 |

Android 的扫描行为参见[官方 BluetoothLeScanner 文档](https://developer.android.com/reference/android/bluetooth/le/BluetoothLeScanner#startScan(java.util.List,android.bluetooth.le.ScanSettings,android.bluetooth.le.ScanCallback))，前台服务依据[后台蓝牙连接说明](https://developer.android.com/develop/connectivity/bluetooth/ble/background)。这些平台规则不能单独证明用户那次断开的唯一原因。

## 自动化设备矩阵

`EquipmentConnectionMatrixTest` 对生产扫描、初始化、数据解析和断开流程运行 **17 种组合 × 3 个场景**：

- V1：椭圆机、AT-R 自行车、含延迟订阅的 MB-HW 划船机、跑步机。
- V2：椭圆机、自行车、划船机、跑步机、跳绳、哑铃。
- 通用 FTMS：自行车、椭圆机、划船机、跑步机。
- Huantong：椭圆机、自行车；另有标准心率带。

每种组合检查首次连接及三轮断电重连、旧回调隔离、运动订阅失败、静止等待及不擅自启动器材。现有 `BleClientTest` 继续检查官方 MOBI FTMS 的特殊控制行为、通用 FTMS 应答、Huantong 周期命令、V2 聚合上报与展开／收起。

`BackgroundConnectionTest` 覆盖六类器材的六个连接阶段、心率带单独连接、结束记录后保留连接、熄屏／省电事件、服务启动拒绝、服务销毁、无真实连接的演示模式。

这些是 Android 回调与服务的合成测试，不模拟无线传播、手机厂商固件、省电策略或机械动作。未改变运动算法；原始官方 DEX 生成的期望表继续由核心回归测试使用，本轮未重新执行完整官方 APK 对照。

## 发布检查

核心 85 项、Debug 208 项、Release 202 项，共 **495 项单元测试通过，无失败、错误或跳过**。Debug／Release lint 均为 0 错误，保留各 85 条警告及 1 条提示；六语言资源检查通过。普通版、Debug 版及 instrumentation 测试包均构建成功。核验普通版无演示入口、旧版签名及包名一致、版本码递增、APK 和原生库 16 KiB 对齐。版本没有数据库或导入导出格式迁移；沿用存储及迁移回归。

按本轮约定，未追加手机或模拟器安装升级、视觉和长时间后台实测。发布说明区分已有实机证据、代码修复和合成验证，不把代码审阅写成原 vivo 设备复测成功。

## English

This release uses existing user logs, source review and automated regressions, without another hardware round. The supplied Xiaomi/Android 16 V1 elliptical logs show five successful connection establishments, followed by a background/screen-off interruption in the third reconnect session. The original vivo handset was not retested; its earlier log was exported after recovery. These observations do not certify every handset, model or firmware.

Shared fixes cover disconnected-device visibility, rediscovery and bounded recovery, screen-off-compatible target filtering, a shared equipment/heart scan budget, connection-owned foreground/wake protection, cleanup when the service is unavailable, required measurement subscriptions, generation-bound queued operations and runtime GATT exceptions. Diagnostics now include app/service/power lifecycle state.

The 17-combination matrix exercises V1 (four equipment families), V2 (six), generic FTMS (four), Huantong (two) and a heart-rate strap. Three scenarios per combination cover connection plus three power cycles and stale callbacks, rejected measurement subscriptions, and quiet subscribed devices without unsolicited motion commands. Existing tests retain MOBI FTMS special commands, generic FTMS responses, Huantong periodic writes, V2 aggregate reporting and folding checks. Service tests cover six equipment families across six setup/live phases, heart-only use, idle stop guards, screen/power events, foreground startup rejection and destruction.

All **495 unit tests passed** (core 85, Debug 208, Release 202), with no failures, errors or skips. Both lint runs have zero errors, 85 warnings and one hint each. Six-language resources and release/debug/instrumentation APK builds pass, together with signing/package continuity, increasing version code and 16 KiB ZIP/ELF alignment. Database and exchange formats are unchanged. No additional handset/emulator installation, visual or long-running background test is claimed. Protocol calculations are unchanged; core tests retain original-DEX oracle fixtures, without a fresh full APK differential run.
