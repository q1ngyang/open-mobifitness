# Issue #2：调阻迟到反馈 / Late resistance feedback

基线：v0.3.5，`0c3031d`。本次为可审阅修复与 Debug 复测包，不发布 Release、不关闭 [Issue #2](https://github.com/q1ngyang/open-mobifitness/issues/2)。没有新增实机测试，**硬件验收尚未完成**。

## 行为与边界

Issue 中 MB-EP（V1，kind 11 / subtype 17，1–24 档）目标 18 在约 3.942 秒收到匹配读数；目标 7 在约 10.701 秒才收到匹配读数。旧实现 5 秒后丢弃目标，导致后者无法消除提示。零值不是该设备的有效档位；本次没有放宽其范围。

`ResistanceControl` 将发送结果、新收到的阻力字段与反馈等待窗口分别保存，界面状态由它们共同决定：

| 状态 | 条件与显示 |
| --- | --- |
| 发送中 | 写入／协议操作未完成；先到达的有效读数可以暂存，但不掩盖发送结果。 |
| 等待反馈 | 发送成功，可读设备尚未收到匹配的有效阻力字段。 |
| 尚未确认 | 发送成功后 5 秒等待窗口结束；`requested` 为空，但最新目标保留。允许提交新目标。 |
| 读数一致 | 新收到的有效阻力字段与最新目标一致，消除未确认提示。迟到和重复读数走相同路径。 |
| 已设定（只写） | AT-R、HuanTong 保留设定值，不生成实测阻力，不等待不存在的阻力反馈。 |
| 发送／协议失败 | 实际写失败或协议拒绝，优先于偶然匹配的读数；FTMS 控制点响应超时和 GATT 操作超时仍按协议／连接错误处理。 |

设备页和训练页共用状态组件。未确认属于提示，发送失败使用错误样式。脚踏器材的文案仅建议在安全时轻踩观察，没有把“运动时才反馈”宣称为通用协议规则。

- 确认只看当前包的新有效阻力字段；合并后的缓存读数、缺失字段、越界字段以及请求目标都不能确认。合法的零起点设备仍可确认 0 档。
- 新的有效读数若又偏离目标，状态重新反映当前读数；不会长期显示“读数一致”。无效包不改写最后的有效阻力。
- 每次请求有本地递增 ID；计时器同时核对请求、连接和控制代次。新请求替换旧目标，连续相同目标也拥有独立等待窗口。断连、控制失效、退出、换人等现有失效流程清理目标，不恢复旧意图。
- V1 回包没有请求序号。本地 ID 只能隔离任务生命周期，无法精确区分所有同值命令的回包。**读数一致既不是回包因果归属证明，也不是机械阻力执行证明。**
- 不因档位尚未确认而重连；不改变既有 FTMS 操作错误处理，也不取消 HuanTong 协议要求的周期写入。

## 自动训练

保留每次一档、至少相隔 2 秒的调节行为，记录“阶段、下一档目标、有效阻力字段版本”作为最近一次自动尝试的依据。

- 同一阶段没有新有效阻力字段时，等待窗口结束不会触发同一档位重发。零值无效包、缺失字段和静默均不推进反馈版本。
- 收到新有效读数后，可以依据该读数继续逐档调节。新读数仍是旧档位时，允许一次新的尝试；之后若再无新有效反馈，就不会持续重发。
- 自动模式切换阶段会使旧阶段意图失效，并依据新目标重新逐档调节。
- 手动改档退出自动模式、替换旧目标；加减按钮可从最新未确认目标继续调整，而主读数仍显示设备值。手动覆盖不会被下一阶段自动撤销；主动恢复自动模式后重新计算。
- 只写设备继续依据设定值逐档调节，不把设定值写入实测指标。

## 代码与回归覆盖

- `app/ble/ResistanceControl.kt`、`BleClient.kt`：请求状态、迟到反馈、连接与代次隔离、统一可注入的单调时钟。
- `OpenMobiApp.kt`：自动尝试去重、阶段变化、手动替换与失败提示。
- `ui/ResistanceStatus.kt`、`DevicesScreen.kt`、`TrainingScreen.kt`、六种语言资源：共用状态文案与显示。
- `data/AppLog.kt`：导出当前请求 ID、目标、发送状态、有效读数与反馈版本，便于复测判断。
- `ResistanceFeedbackTest`：16 个虚拟时间测试，通过真实解析器及模拟 GATT 覆盖 3.942 / 10.701 / 15.352 秒时间点、217 个零值包、缺失字段、静默、新旧目标、同值计时器、断连旧回调、写失败、读数早于写完成、在途失效、V2、标准与官方 FTMS、AT-R 和 HuanTong 周期写入。
- `AutomaticResistanceTest`：7 个控制器／Room 集成测试；BLE 时钟由测试显式推进，数据库 I/O 不会自动跳过反馈计时器。
- `ZeroResistanceRegressionTest` 保留合法零起点设备的验证，更新意图构造方式。
- `V020ExperienceTest.resistanceFeedbackStatesAgreeOnTrainingAndDevicesPages`：真实 Compose 页面检查发送中、等待、未确认、读数一致、失败的文案一致性，并保存截图。

正式回归只保留合成身份与最小数据帧。原始诊断日志及带设备身份的调查材料留在本地，没有加入仓库；没有迁入“旧错误仍然存在即通过”的断言。

## 验证命令

```sh
python3 tools/check_resources.py
./tools/build.sh :core:test :app:testDebugUnitTest :app:testReleaseUnitTest :app:lintDebug :app:lintRelease :app:assembleDebug
```

本次结果：

| 检查 | 结果 |
| --- | --- |
| 资源检查 | 593 个主资源键、14 个 Debug 专用键，六种语言齐全。 |
| 单元／集成测试 | core 85、app Debug 231、app Release 225，共 541 项；0 失败、0 错误、0 跳过。 |
| lintDebug / lintRelease | 均通过；每种构建 0 错误、91 警告、1 提示，未把“通过”表述为零警告。 |
| assembleDebug | 成功；包名 `org.openmobifitness.app.debug`，版本 `0.3.5-debug`（15），arm64-v8a / x86_64。 |
| 签名 | 与原 v0.3.5 Debug 交付包 SHA-256 证书指纹一致。 |
| 界面 | API 34 模拟器，390×844，中文 1.0 倍字体、德文 1.3 倍字体；两个测试执行通过，20 张有效截图逐张复核，未见本次状态文案裁切、遮挡或控件重叠。 |

首次模拟器冷启动出现系统服务 ANR 弹窗，受遮挡的截图没有计入视觉通过结果；排除系统遮挡后重新执行。模拟器没有真实器材连接。

构建与验证产物按现有开发盘配置写入 `$DEV_TEMP_BASE`；本次证据目录为 `$DEV_TEMP_BASE/work/openmobi-issue2-fix-20261024/`。交付包为该目录中的 `OpenMOBI-0.3.5-issue2-debug.apk`，校验值在 `SHA256SUMS`，完整结果在 `delivery.json`，有效截图在 `ui-verified/`。

## MB-EP 复测与日志

Debug 包名为 `org.openmobifitness.app.debug`，与普通版共存。先结束并断开另一版的器材连接，再安装本次 Debug 包；保留既有数据，不用卸载重装掩盖签名不一致。此包仍基于 0.3.5（15），通过文件名和 SHA-256 区分本次构建。

1. 打开诊断中的协议跟踪，连接 MB-EP，核对识别范围为 1–24。进入“仅控制”，记录手机型号、Android 版本、器材型号和开始时间。
2. **静止**：先用相邻档位操作，再在适合自己的情况下复现 18 → 7。每次保持约 20 秒，记录目标、App 设备读数及状态。若没有有效反馈，应显示尚未确认、保留最新目标、允许再次操作；不能显示成功、反复自行发指令或自动重连。
3. **踩踏**：在安全情况下轻踩，观察设备是否回报目标档位。若此前已显示尚未确认，收到匹配有效读数后应消除提示。停止踩踏再重复一次，记录实际观察，不预设设备只在运动时反馈。
4. **连续改档**：依次提交不同目标，再重复同一目标；检查提示始终对应最新请求，旧等待窗口不会使新请求提前变为未确认。滑块／加减按钮的目标与设备读数应保持可区分。
5. **自动计划与失效**：使用短阶段计划，观察未确认期间不会在无新有效反馈时持续重发；新读数到达后逐档继续。切换阶段、手动覆盖、暂停／结束、断电重连后，均不应恢复已经失效的旧目标。HuanTong 的必要周期写入不适用“停止重复写入”的判断。
6. 每组结束后立即导出诊断，分别命名“静止”“踩踏”“连续改档”“自动计划”。附上每次操作的大致时间、目标、App／器材显示值和身体感受到的变化；原始日志先自行检查隐私，再决定是否分享。

软件回放和模拟器测试验证状态机、数据来源、生命周期和界面；没有验证华为 P20 的真实蓝牙链路、设备电机或实际机械阻力。机械效果需要独立的实机观察／测量，不能由匹配读数替代。

## English

Baseline: v0.3.5 (`0c3031d`). This delivery is reviewable source and a Debug retest build, with no Release publication or issue closure. **Hardware acceptance is pending.**

The fix separates transmission, the short feedback window, and the latest target. Five seconds after successful transmission, pending clears but the target survives for late comparison. A fresh valid resistance field can clear the unconfirmed notice; cached/merged values, absent fields, invalid zero readings and commanded values cannot. New requests replace prior intent. Local IDs, connection identity and control generations protect timers and lifecycle transitions, including repeated equal targets. V1 has no response sequence number: matching telemetry cannot establish exact response attribution or mechanical execution.

Actual GATT failures, FTMS rejections and control-point response timeouts remain errors. Early matching telemetry cannot hide a later failed write. Write-only AT-R/HuanTong retain commanded levels without manufacturing measurements; required HuanTong periodic writes remain intact. No feedback-timeout reconnection was added.

Automatic plans still adjust one level at a time with at least two seconds between attempts. An attempt is keyed by stage, next level and valid-feedback revision. Silence does not trigger repeated identical steps. New valid feedback permits reevaluation, including a fresh attempt when it still reports the old level. Automatic stage changes replace old intent. Manual changes disable automatic mode and survive later stage transitions; explicitly resuming automatic mode recalculates from the current reading.

Both pages share status wording: sending, awaiting feedback, unconfirmed, matching device reading, write-only setpoint, or send/protocol failure. Pedaling is suggested only as a safe observation step, not asserted as a universal protocol requirement. Six language sets are updated.

The regression suites use synthetic identities, minimal frames and virtual BLE time. They cover the issue's 3.942/10.701/15.352-second timing, invalid/missing feedback, overlapping requests, lifecycle invalidation, write/protocol failures, automatic-plan behavior and all five protocol families. All 541 tests passed, with no failures or skips. Both lint tasks passed with zero errors and 91 warnings plus one hint each. Resource checks and Debug assembly passed. Two API 34 UI runs (Chinese, German at 1.3 font scale) passed; 20 unobscured screenshots were individually inspected. An initial emulator system-service ANR obscured earlier images, which were excluded and rerun. Private original logs remain local. Run the commands above; detailed build evidence, `OpenMOBI-0.3.5-issue2-debug.apk` and `SHA256SUMS` are in `$DEV_TEMP_BASE/work/openmobi-issue2-fix-20261024/`.

For MB-EP retesting, disconnect the regular app first, install the Debug build alongside it, enable protocol tracing, and use control-only mode. Test stationary changes, gentle pedaling, rapid different/equal targets, then short automatic stages and manual overrides. Wait roughly 20 seconds per stationary request; only a new matching valid reading should clear the notice. Export each group promptly with action times, requested levels, displayed readings and physical observations. Simulation does not validate Huawei P20 radio behavior, motors or mechanical resistance.
