# 官方 DEX 对照 / Original DEX oracle

直接在独立 Android 模拟器的 `app_process` 中执行本地参考 APK 的方法。APK 不安装，官方 Application 不启动，不连接真实蓝牙，不调用官方服务器。仅替换运行环境依赖（内存偏好、Application、UTC 时区、主 Looper），计算与编码方法使用未经修改的 DEX。

`commands` 执行原处理器，将传输队列的消费者保持停止，读取原代码入队的写入字节；不订阅传输 Observable。初始化顺序与周期调度另由 Smali 核对和模拟 GATT 测试覆盖，不能把这两种证据称为真实连接测试。

参考 APK 的 SHA-256、输入集、表行数和校验值在 [manifest.json](manifest.json)。原 APK 和完整反编译源码不入库；JSONL 全部是合成输入，TSV 为实际 DEX 输出或单位／字段映射。两版生成的 16 张表完全相同，因此共用一份标注国际版来源的测试资源。

## 重现

先启动专用 Android 14 模拟器。需要项目开发卷上的 JDK 21、SDK 37、Build Tools 36.0.0；`run.sh` 使用 `DEV_TEMP_BASE` 下已有工具，也接受 `JAVA_HOME`、`ANDROID_HOME`。所有构建、原始结果放开发卷。不要同时运行两个 oracle 进程：模拟器内使用同一个隔离目录。

```bash
oracle_run="$DEV_TEMP_BASE/work/openmobi-oracle-$(date +%Y%m%d-%H%M%S)"
mkdir -p "$oracle_run/international" "$oracle_run/chinese"
for variant in international chinese; do
  if [ "$variant" = international ]; then
    oracle_apk='apk/莫比健身 2.1.14.apk'
  else
    oracle_apk='apk/莫比健身 4.5.16.1.apk'
  fi
  for corpus in cases command-cases resistance-cases equipment-cases alternate-cases status-cases fold-cases ftms-handler-cases interaction-cases ftms-status-cases; do
    bash tools/protocol-oracle/run.sh "$oracle_apk" emulator-5554 "$variant" \
      < "tools/protocol-oracle/$corpus.jsonl" \
      > "$oracle_run/$variant/$corpus.jsonl"
    python3 tools/protocol-oracle/extract.py "$oracle_run/$variant/$corpus.jsonl" "$oracle_run/$variant/tables"
  done
  python3 tools/protocol-oracle/verify.py "$oracle_run/$variant/tables"
done
./tools/build.sh :core:test
./tools/build.sh :app:testDebugUnitTest :app:lintDebug
```

每版 1,663 个请求。转换器遇到任何原 DEX 异常立即失败，不跳过错误。`verify.py` 对照已提交表的哈希；不要在核对差异前覆盖参考表。工具拒绝把真实手机作为执行目标。

## 输入与覆盖

- `cases.jsonl`：划船状态机、全部 256 个读写模式／坡度字节、33 个 V2 型号能力、V1/V2/HuanTong 解析、FTMS 可选字段与两种椭圆机头部、默认功率／热量模型。包括零磁铁数量这一原厂边界分支。
- `command-cases.jsonl`：V1/V2/FTMS 状态、速度、坡度、调阻，V2 解锁与小器材目标，HuanTong 周期帧。两版的命令 9 都没有入队写操作；测试保留这个事实。
- `resistance-cases.jsonl`：调用实际 `MotionData.updateCurveData` 和 `getAvgResistanceInQueue`，验证计算档位逐秒变化及相应功率、热量。
- `equipment-cases.jsonl`：跳绳 15 字节、哑铃 7 字节实时帧、8812 哑铃重量的 read/notify 两条回调。
- `status-cases.jsonl`／`fold-cases.jsonl`：能力、急停、折叠反馈及两代折叠控制。
- `ftms-handler-cases.jsonl`／`ftms-status-cases.jsonl`：实际 FTMS 通知处理器的二次转换、范围与机器状态；不是只比较 flags 枚举。
- `interaction-cases.jsonl`：将原 Bus 的传输 channel 替换为只记录事件的接收端，捕获未修改回调产生的小器材／按键／语音事件。其余计算与切片逻辑不替换。
- `alternate-cases.jsonl`：可选 type-1 模型、汇总划船功率与热量函数。默认训练仍使用 type-0；不能从设备名推断用户在官方 App 中选择了哪套计算配置。

可审阅的协议、型号表、Smali 位置及限制见 [验证说明](../../docs/PROTOCOL_VERIFICATION.md)。

## v0.1.1 基线与私有实机回放

`openMobiV1` 直接调用已发布 OpenMOBI APK 的 `Protocols`，不使用厂商环境依赖。公开 `v011-cases.jsonl` 的 576 组输入独立于上面的官方 APK 请求数；每组比较解析、稳定功率和 24 档调阻。

```bash
baseline_run="$DEV_TEMP_BASE/work/openmobi-v011-baseline"
mkdir -p "$baseline_run"
bash tools/protocol-oracle/run.sh /path/to/OpenMOBI-0.1.1-debug.apk emulator-5554 openmobi \
  < tools/protocol-oracle/v011-cases.jsonl > "$baseline_run/results.jsonl"
python3 tools/protocol-oracle/extract.py "$baseline_run/results.jsonl" "$baseline_run/tables"
cmp core/src/test/resources/oracle/v011-baseline.tsv "$baseline_run/tables/v011-baseline.tsv"
```

私有回放沿用相同请求格式：`{"op":"openMobiV1","packets":["ab04…"]}`。原始帧、请求、结果及 TSV 全部放开发卷工作目录；提取时添加 `--private`，然后以环境变量 `OPENMOBI_PRIVATE_V1_BASELINE_TSV` 指向 TSV 运行 `:core:test`。正常回归始终包含公开合成集；提供该变量时，`V011CompatibilityTest` 再对私有帧执行完全相同的严格断言。历史实机日志不是新增无线连接测试。

旧版 APK、提交、合成输入和表的哈希在 manifest 的 `v011_baseline` 项；[复核报告](../../docs/V011_COMPATIBILITY_REVIEW.md)记录 394 条私有帧的结果，不公开其内容。公开验证集和既有 V1 实机基线足以完成本阶段验收，不要求其他型号硬件。

## English

This harness executes unmodified local reference DEX methods in a dedicated Android emulator using `app_process`. It substitutes environment dependencies only; it neither installs/starts the vendor app nor performs physical BLE or vendor-network I/O. Command tests inspect queued writes while the transport consumer is stopped.

Run the commands above sequentially. Each APK receives 1,663 synthetic requests. Both versions generate the same 16 golden tables. The manifest records APK/corpus/table hashes. Extraction fails on any oracle exception; verification compares generated tables with the committed checksums. Keep APKs, decompilations and temporary results outside Git. Initialization/timer evidence comes from Smali and simulated GATT tests; physical hardware evidence is limited to the existing V1 user feedback/captures; additional hardware is not a completion dependency for this phase.
