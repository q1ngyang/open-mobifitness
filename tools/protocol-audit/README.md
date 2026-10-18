# v0.3.3 协议遗漏复现

> 历史 v0.3.3 缺口复现探针：应在对应旧基线上运行，通过仅代表缺口得到复现，不属于 v0.3.4 验收。新版正确行为由正常测试目录中的回归覆盖。
>
> Historical v0.3.3 characterization tests: a passing assertion here reproduces a defect. These probes are not the v0.3.4 release gate. Corrected behavior is covered by `ProtocolRecordingRegressionTest`, `ZeroResistanceRegressionTest`, `PlanCompletionTest`, `BleClientTest` and `ProtocolBoundaryTest` in the normal test suites. See [v0.3.4 validation](../../docs/V034_VALIDATION.md).

配合 [REA 审计补查](../../docs/REA_AUDIT.md) 使用。此目录保存 **v0.3.3 行为观察**，不属于常规回归套件；断言通过表示观察仍成立，其中包含已知缺陷，不能解释为修复通过。修复时应将对应场景转换为断言正确行为的正式回归测试。

```bash
./tools/build.sh -I tools/protocol-audit/audit.init.gradle \
  :app:testDebugUnitTest --tests org.openmobifitness.app.V033GapAuditTest \
  --offline --no-daemon
```

沿用项目配置的开发卷、Gradle 缓存和 Android/JDK 工具链。报告位于 `$DEV_TEMP_BASE/build/projects/open-mobifitness/outputs/app/test-results/testDebugUnitTest/`；执行其他测试前应另存本次 XML，因为 Gradle 会替换该报告目录。移除 `-I` 后，本目录不会加入测试源码集。

探针使用合成帧、Robolectric 时钟和真实 `MobiSession → BleClient.receive → Controller.tick → Repository` 路径；通过反射调用接收入口，不模拟无线链路或机械动作。部分探针仅检查 core 层。AT-R 帧布局来自公开 [Issue #1](https://github.com/q1ngyang/open-mobifitness/issues/1)，具体测试计数和时间为合成值。

| ID | 观察点 |
| --- | --- |
| A01 | AT-R 第 11–12 字节变化不改变 Metrics，未提供累计距离 |
| A02 | 子型 1 的固定模式阻止构造调阻命令 |
| A03 | 完全静默时仍积分 4 个周期；第 5 秒过期后停止 |
| A04 | FTMS 局部踏频包使旧速度保持有效并进入累计距离 |
| A05 | V2 mode 3 的 8811 使旧 8813 速度／功率保持有效 |
| A06 | 跑步机停止、数据过期后，旧步幅仍写入样本 |
| A07 | 合成的 16／24 位回绕被当作复位，丢掉跨界增量 |
| A08 | 磁铁数 0：辅助函数拒绝，实际解析路径产生 60 rpm |
| A09 | 等待开始上报的 V2 器材，距离／桨数方案在启动上报前被拒绝 |
| A10 | V2／FTMS 的 2A19 电量未进入应用状态 |
| A11 | 单车、椭圆机、划船机及 MOBI 跑步机局部更新保留旧速度，6 秒多计 30／60 米且写入历史 |
| A12 | FTMS 踏频 FFFF 被记录为 32,767.5 rpm，没有设备合理性过滤 |
| CONTROL-1 | V2 显式只写能力能够覆盖型号默认值 |
| CONTROL-2 | 心率通知不会刷新运动报文时间 |
| CONTROL-3 | 计数复位与重连重新建立基线的现有行为 |

回绕探针只证明现有累计器无法区分回绕和复位，不证明每台设备实际采用回绕。调阻探针只证明软件限制，不证明任何具体设备的机械调阻范围。

扩展后共 15 个测试（12 个观察编号、3 个对照），并配合 [三方原始 APK 差分工具](../protocol-differential/README.md) 使用。A11 同一个测试分别运行四类器材；A12 只证明极端合成值缺少过滤，不为真实器材规定统一阈值。

## 设备特殊规则补查

[SpecialRulesAuditTest.kt](src/org/openmobifitness/app/SpecialRulesAuditTest.kt) 另有 3 个测试，与上面的 15 个分开保留结果：

```bash
./tools/build.sh -I tools/protocol-audit/audit.init.gradle \
  :app:testDebugUnitTest --tests org.openmobifitness.app.SpecialRulesAuditTest \
  --offline --no-daemon
```

| ID | 观察点 |
| --- | --- |
| A13 | V2 单车／椭圆机／划船机明确声明 0–24 后，0 档命令可以编码，但反馈 12 → 0 被过滤，旧档位和待确认的 0 档意图保留；正值反馈对照能确认 |
| A14 | MOBI FTMS 单车／椭圆机声明 0–24 后，Metrics 能报告 0，计算输入却保留旧的 12；12 个活动 tick 后估算功率仍不变，正值 1 能使其下降 |
| CONTROL-4 | 通用 FTMS 的 0 档反馈会更新读数并确认待处理的 0 档意图 |

A13 通过反射注入通知，并直接设置待确认意图；它验证反馈消费，不声称真实 GATT 写入成功，也不把未执行的 5 秒超时算作测试结果。A14 通过真实 MobiFtmsSession 观察公开的 Metrics 和 energyPower，未使用真实功率计。0–24 为合成能力声明，不能据此认定 AT-R 或其他具体型号从 0 起档。
