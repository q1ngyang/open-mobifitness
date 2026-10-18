# 相同报文的三方差分执行

此工具把同一个 JSONL 输入文件分别送入国际版 2.1.14、中文版 4.5.16.1 和当前 OpenMOBI Debug APK。官方计算由 APK 中的原始 DEX 执行，不使用翻译后的公式生成期望值。2026-10-18 的结果见 [审计报告](../../docs/REA_AUDIT.md#三方动态差分补查2026-10-18) 和 [执行摘要](results-20261018.json)。

**差异是审查线索，不能当作缺陷数量。** 官方的默认零、负值哨兵、异常、元数据、未映射字段及 OpenMOBI 额外计算的字段均保留。通过完整性检查只代表执行完整、结果可重复，不代表产品正确或已修复问题。

## v0.3.4 复核

在原有主集／补集／新增划船子型之上，保留 136 条特殊规则序列并新增 184 条复归、稀疏字段、计数回绕与极端脉冲序列。合计每份 APK 3,092 条独立序列、30,164 步，另有 80 条逆序重复。新版验证器覆盖全部五批，单独记录每步异常，拒绝初始化／下游或 OpenMOBI 回调异常。分别以国际版和中文版为参考生成比较文件。新版结果及限制见 [验证记录](../../docs/V034_VALIDATION.md)。

下方早期结果数字保留为历史记录，不能代表修复后的差异数量。

## 注入边界

| 层 | 实际执行 | 限制 |
| --- | --- | --- |
| 官方接收 | 新建 V1/V2/HuanTong/FTMS Handler；同一个 Handler 按序接收不同特征的原始 byte[]；记录回调、设备元数据、事件和异常 | 在 GATT 回调处注入，没有扫描、连接、无线丢包或机械反馈；读取原始 DEX，不启动官方 Application/UI |
| OpenMOBI 接收 | APK 中的 MobiSession/MobiFtmsSession/Protocols；记录每包 Metrics、merge 后状态和元数据 | 配件及 FTMS 元数据调用采用与 BleClient 相同的分发边界；未在此进程启动 Controller、Room、UI 或协程计时器 |
| 官方算法链 | 标记 `motion=true` 的序列接入原始 MotionData listener；tick 调用官方 updateCurveData；OpenMOBI 调用 activeSecond | 用原始 setBleType 初始化器材，并将初始状态设为 START（新增停止状态的跑步机序列显式设置 initialSportState=STOP），隔离自动结算与真实时间。不能据此判断官方完整暂停、超时或上传流程 |
| OpenMOBI 记录链 | [15 个审计探针](../protocol-audit/README.md)，调用真实 BleClient → Controller → Repository，用虚拟时钟模拟静默和部分更新 | 这是选定场景的深入验证，并非全部 2,772 条序列都经过数据库或 UI |

官方偏好存储和事件接收器采用内存替身。实际解析、型号分支、划船计算和 MotionData 方法保持原样。每条序列新建 handler/session，重置官方全局能力常量；一条序列内部保留历史状态。`captureRx=true` 还捕获心率使用的 RxBus。

## 复现

需要现有 JDK、Android SDK 和独立模拟器。`run.sh` 拒绝物理设备。构建、DEX、缓存、输出都放在开发卷；没有 KVM 时本轮使用 Android Emulator 的 `-accel off` 软件运行方式。不要把实际设备连接到这个回放工具。

在仓库根目录执行，使用一个新的结果目录。模拟器序列号可按环境修改：

```bash
audit_dir=$(mktemp -d "$DEV_TEMP_BASE/work/openmobi-diff.XXXXXX")
python3 -B tools/protocol-differential/generate.py "$audit_dir"
python3 -B tools/protocol-differential/targeted.py "$audit_dir"
python3 -B tools/protocol-differential/special_rules.py "$audit_dir/special-rules"
python3 -B tools/protocol-differential/v034_cases.py "$audit_dir/regression"
./tools/build.sh :app:assembleDebug --offline --no-daemon

for batch in . supplement extra-rowers special-rules regression order-check; do
  bash tools/protocol-differential/run.sh 'apk/莫比健身 2.1.14.apk' emulator-5560 international \
    < "$audit_dir/$batch/cases.jsonl" > "$audit_dir/$batch/international.jsonl" 2> "$audit_dir/$batch/international.log"
  bash tools/protocol-differential/run.sh 'apk/莫比健身 4.5.16.1.apk' emulator-5560 chinese \
    < "$audit_dir/$batch/cases.jsonl" > "$audit_dir/$batch/chinese.jsonl" 2> "$audit_dir/$batch/chinese.log"
  bash tools/protocol-differential/run.sh "$DEV_TEMP_BASE/build/projects/open-mobifitness/outputs/app/outputs/apk/debug/app-debug.apk" emulator-5560 openmobi \
    < "$audit_dir/$batch/cases.jsonl" > "$audit_dir/$batch/openmobi.jsonl" 2> "$audit_dir/$batch/openmobi.log"
done
for batch in . supplement extra-rowers special-rules regression; do
  python3 -B tools/protocol-differential/compare.py "$audit_dir/$batch"
  python3 -B tools/protocol-differential/compare.py "$audit_dir/$batch" --reference chinese
done
python3 -B tools/protocol-differential/validate.py "$audit_dir"
```

顺序执行 runner，因为编译中间目录共享。上例只依赖已有工具链和本地 APK，不下载或上传 APK。

`differences.jsonl` 给出场景 ID、步骤、字段及两边值；`comparison.json` 汇总差异、异常和未映射字段；`execution-validation.json` 检查输入/输出数量、初始化/下游错误及顺序重复性。原始 trace 始终保留，比较投影不替代原始证据。单条复现可以按 ID 从 cases.jsonl 取一行，再传给 runner。

主集包含 2,736 条序列、20,770 步；补集 20 条、352 步；新增划船子型 16 条、260 步。合计每份 APK **2,772 条、21,382 步**。覆盖六类器材、39 个重点子型组合，并探索四类大器材全部 256 个子型字节值；后者不是 1,024 个真实型号的兼容性证明。80 条逆序重放用于检查状态污染，不重复计入覆盖数。

## 结果解读

- `callback` 比较原始回调与每包 Metrics；`accumulated-callback` 比较累计投影；`MotionData` 比较实际官方运动数据层。回调的零值与最终应用状态经常不同，不能混用。
- 原始 callback 的小数功率与 OpenMOBI 的整数显示功率可能不同；原始 0 W 与下游估算值也可能不同。这些会保留在差异列表中，须按层解释。
- `extra-metric` 和 `unmapped_fields` 显式列出额外/未对齐字段，不把缺少映射当作匹配成功。
- 两版 APK 的电量初值、名称和图片目录不同；数值算法比较应与这些元数据差异分开。
- 官方 48 次截断范围输入触发 Firebase 未初始化错误，属于运行环境限制，不能说完整官方 App 会在这些输入下崩溃。其余报文边界异常同样只证明注入层的行为。
- 逆序重放会改变官方 `LastUpload` 构造时缓存的 Box_name、Device_subtype。验证器明确记录并排除这两项和时间戳，仅确认当前计算/活动状态可重复；未验证最终官方上传记录。

首轮发现 MotionData 切换器材会触发真实结算依赖，已改用官方 setBleType 初始化，并重跑全部 18 条计算序列。开发卷保留 initial/ 原始失败结果、motion-repeat/ 重跑结果和两版 harness 源码；主结果用有来源记录的重跑替换这 18 条。其他序列未改写。后续直接按上述命令运行即可使用修正后的隔离方式。

## 展开／收起和零起始档位补查

[special_rules.py](special_rules.py) 是独立的 **136 条／4,430 步**输入集，不改写前批 2,772 条的文件或覆盖数字。三份 APK 均已运行，初始化、回调和 MotionData 转发异常为 0；对应的 3 个应用探针见 [SpecialRulesAuditTest](../protocol-audit/README.md#设备特殊规则补查)。完整结果见 [审计报告](../../docs/REA_AUDIT.md#展开收起与设备特殊规则补查2026-10-18) 和 [摘要](special-rules-results-20261018.json)。

```bash
special_dir=$(mktemp -d "$DEV_TEMP_BASE/work/openmobi-special-rules.XXXXXX")
python3 -B tools/protocol-differential/special_rules.py "$special_dir"
# 按上文三个 runner 命令，依次传入本目录的 cases.jsonl，输出到本目录。
python3 -B tools/protocol-differential/compare.py "$special_dir"
```

`summarize_special.py "$special_dir"` 还会验证执行完整性和提取关键观察，要求目录中已保存 `app-audit-results.xml` 及 `standard-test-results/TEST-*.xml`。应用探针通过代表复现当前缺口；`compare.py` 的差异数不等于缺陷数。原始三方输出和本地摘要中保留各次声明、回调、Metrics 和计算值。

本批检查 V2 读写模式、0／1 最小值、能力声明顺序、FTMS 步长、折叠能力及状态、字节有符号边界。它没有模拟 GATT 控制发送、官方界面或真实器材动作。`fold` 组验证接收状态；折叠命令编码的官方写入对照来自已有 oracle 的 8 个样本。给 V2 划船机输入折叠能力位不证明该型号真实支持电动折叠；声明 0–24 不证明 AT-R 从 0 起档。


v0.3.4 新增跑步机稀疏帧首轮触发了 15 个下游结算环境错误：统一的 START 初始状态遇到处理器默认 STOP，引发官方上传上下文依赖。修正 harness 的跑步机初始状态为 STOP 后重跑整个 184 条新回归集，两份原始失败结果保留在本地。没有改写报文状态、原始 DEX 或绕过数值回调；完整官方结算仍不属于这个隔离工具的验证范围。
