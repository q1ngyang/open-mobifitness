# V0.2.0 验证记录 / Validation record

本报告记录 V0.2.0 发布前实际执行的结果与覆盖边界。[下载与更新说明](releases/v0.2.0.md) · [English](#english)

## 构建、资源与安装包

最终 R48 构建对当前源码执行 `:core:test`、两个变体的单元测试及 Lint 任务，并构建普通版、Debug 版和测试 APK。Gradle 对未变化输入使用增量结果，完整任务日志与 JUnit/Lint 报告均保留。

| 检查 | 本轮结果 |
| --- | --- |
| Core 单元测试 | 59 项通过 |
| Debug 单元测试 | 59 项通过 |
| Release 单元测试 | 60 项通过 |
| 两个变体的 Lint | 各 0 错误、59 警告、1 提示；完整报告保留，未宣称零警告 |
| 六语言资源 | 每套 422 个字符串，含 `strings.xml` 和 `library.xml`；键集合及格式参数一致 |
| 版本 | 普通版 `0.2.0`、Debug 版 `0.2.0-debug`；均为 `versionCode 9`，高于已发布的 8 |
| 签名 | 两种变体分别与原发行证书一致，包名保持独立 |
| 原生库 | `arm64-v8a`、`x86_64`；ZIP 页对齐及 ELF LOAD 段 16 KB 对齐通过 |

发行包与校验和见 [v0.2.0 发布页](https://github.com/q1ngyang/open-mobifitness/releases/tag/v0.2.0)。本地 `candidate/` 保留验证时的同一份二进制；发行文件沿用 v0.1.1 的命名方式，内容和校验值不变。

## 协议、状态与记录

- 国际版和中文版原始 DEX 分别重新运行 1,663 个 oracle 请求，耗时 323.3 秒及 367.2 秒；固定表输出与夹具一致。
- v0.1.1 公共基线 576 行、私有基线 394 行的表头及数据逐项一致；仅忽略生成时的来源注释差异。私人采样帧的两项回放也已重新执行。
- 本轮单元回归涵盖初始化与周期写入、消息解析、指标过滤、实际/请求阻力、过期命令、连接代次、累计量重基准，以及仅控制与记录切换。
- 仅控制不创建 Session、计时或数据库采样。开始记录不重连或跳档；保存自由记录回到仅控制。断连后主动继续，个人提示不调阻，跑步机电机、速度及坡度控制保持禁用。
- 界面复测发现并修复旧前台服务延迟退出误暂停新记录的竞态：主动空闲退出不操作后续使用状态，开始中或已在使用时忽略空闲退出请求；意外服务丢失只暂停对应会话，不会恢复已暂停记录或操作新会话。新增会话身份回归和连续三次退出后立即记录的实际操作检查。

`PROTOCOL_VERIFICATION.md` 中原有 64,039 个比较项保留其原始统计口径。本报告不把旧报告重新标记成本轮运行，也不将模拟结果称为硬件实测。用户已有的 V1 MB-EP 椭圆机反馈属于继承的实机基线。

## 非空升级与备份

API 37 上分别用原签名 v0.1.1 普通版和 Debug 版建立非空数据后覆盖安装：Room 5 升至 7，保留 1 条会话、240 条采样、自定义方案及 2 个阶段；所有旧列值与非默认偏好逐项一致。Room 1–6 的迁移另有单元回归，未把它们表述为逐个旧 APK 的真实升级。

完整备份 v7 采用每块 2,000 条采样的读写，仍读取旧 v1–v6 格式。360,001 条采样往返通过完整指标摘要比对，ZIP 为 51,279,708 字节（48.9 MiB），展开内容为 117,217,091 字节（111.8 MiB）。重复导入、冲突、取消、损坏、容量与空间不足均有对应检查。API 29 通过系统文件选择器实际导出、预览、选择恢复组并导入；只恢复外观时保留自定义指标设置。

Room v7 在既有 v6 字段上增加偏好恢复日志；采样 CSV v6、会话 CSV v5 不变。数据库与偏好的失败恢复行为见[数据格式](DATA_FORMAT.md)。备份不复制系统授权、蓝牙配对或旧屏幕的绝对坐标。

## 界面与实际操作

在读取 v0.1.1 及含协议修改的 Debug 实际界面后生成设计图，十张设计板已获确认。后续按用户要求恢复首页四项统计的单行布局、两种模式的右侧悬浮按钮，并压缩低矮横屏的标题与底栏。手机竖屏仅控制使用紧凑的两列数据及同行单位；平板控制内容限制最大宽度。

界面证据均为模拟器实际截图；设计图另外保存，不充当运行截图。每个场景记录窗口 dp、字体、语言、主题、API 及模拟数据说明。自动化断言和图片阅读分开记录；系统 ANR 或通知栏遮挡的画面不计入通过。

矩阵已完成 343 个流程与环境检查项，对应 336 组独立场景、3,221 张选用实图；操作结果及图片阅读均已登记，无待核对项。重复覆盖同一场景的检查项不重复计入独立场景数。

| 覆盖范围 | 检查项 |
| --- | --- |
| 常规页面、键盘输入、首页长数值/备份预览、悬浮操作、方案手动接管：各 9 种布局 × 3 档字体 × 浅深色 | 270 |
| 输入及固定操作栏：各 9 种布局 × 1.5 字体 × 浅深色 | 36 |
| 修改页面及悬浮操作：各手机/平板 × 六语言 | 24 |
| 范围提示、固定操作栏、悬浮操作的高风险组合：各 4 组 | 12 |
| 16:9 手机完整页面流程 | 1 |

9 种布局包含窄屏、常规和大手机、低矮横屏、小平板与平板竖屏、平板横屏、折叠展开及分屏。字体为 1.0、1.3、2.0，输入补充 1.5；高风险组合包含德语 200% 字体和中文平板竖屏。最终 R48 候选包另在 API 29 采集 12 张竖屏实图，并在 API 34 验证普通版离线启动；这些补充不混入上述矩阵数量。

已修复并复测的细节包括：键盘上方固定保存/取消、按剩余高度压缩输入弹窗留白、窄屏大字体的指标选择操作、方案阶段剩余时间裁切、准确的个人范围错误说明、悬浮窗独立展开按钮及辅助功能状态刷新。结束保存弹窗的标题和说明共用可滚动区域，按钮保持独立可达，避免低矮横屏配合德语 200% 字体时说明被压住。

原生通知的运行计时由系统计时器更新，避免每秒重建通知影响按钮操作。最终 R48 包在 API 29 中文、API 34 德语均通过连续三次快速退出后重新记录，以及通知打开、暂停、断连、重连后继续及退出链；重连后保持暂停，等待用户操作。

失败或不完整的历史尝试仍保留：模拟器失去焦点、Compose 等待空闲超时、旋转未稳定时的截图均另行记录并针对重跑，不以测试返回成功替代看图检查。截图索引保留每轮 APK 摘要、实际窗口尺寸和被后续结果替代的关系；不同修订的结果共同构成本轮回归证据，并非所有场景都在最终 APK 上重复运行。

## 后台检查与覆盖边界

API 29 的 60 分钟活跃使用检查耗时 3,601,866 毫秒，生成 3,158 条采样；软件模拟调度下最大采样间隔为 10,967 毫秒，单调时钟计时保持正确。覆盖前台服务、桌面、悬浮展开/收起、10 分钟处播放本地视频、20–30 分钟熄屏、约 40 分钟处合成断连及主动继续、保存并退出。退出后服务停止、唤醒锁为 0。

该检查没有蓝牙射频，GATT 传输指令数为 0；视频在熄屏期间结束，不代表连续一小时视频播放。它不验证真实无线稳定性或各厂商的省电策略。

最低 API 29 和 API 34 有运行时验证。API 37 的 oracle 与数据库升级能够运行，但其 SystemUI/Launcher 多次 ANR、黑屏，无法完成可信的界面矩阵，因此明确保留目标系统 GUI 缺口。模拟折叠尺寸不等于实体铰链验证，其他器材型号也不声称全部实测。

## 本地证据

所有构建、SDK、模拟器、原始 APK 和测试输出均留在开发盘。当前任务目录为 `$DEV_TEMP_BASE/work/open-mobifitness-v020/`：

- `baseline/`：版本身份、原版和协议 Debug 的真实界面与操作。
- `design/`：设计板、确认记录和本地生成材料。
- `qa/`：原始运行截图、环境元数据、迁移、备份及后台证据。
- `qa/review.html`：可搜索的实图索引，链接已确认设计、原图和逐组识图记录。
- `reports/`：构建日志、JUnit/Lint、oracle 摘要、矩阵操作结果和图片阅读记录。
- `candidate/`：两个候选 APK、签名检查及 `SHA256SUMS`。

设计提示词、私人协议日志、原始 APK 和反编译材料不提交到源码库。

## English

This report records checks actually performed before the V0.2.0 release and their limits. See [downloads and release notes](releases/v0.2.0.md#english). Release assets retain the tested binaries, with filenames following the v0.1.1 convention.

The final R48 build validates 59 core, 59 Debug and 60 Release unit-test results, with no failures, errors or skips. Gradle reuses incremental results for unchanged inputs; complete task logs and reports are retained. Both Lint variants report zero errors, 59 warnings and one hint. All six locales have matching sets of 422 strings and matching format arguments. Both APKs use version code 9, retain their original certificates and package identities, and pass 64-bit ABI and 16 KB ZIP/ELF alignment checks.

The original international and Chinese DEX oracles were rerun with 1,663 requests each. The 576-row public and 394-row private v0.1.1 baselines match exactly in headers and data, excluding generated provenance comments only. The inherited 64,039-item evidence retains its original definition. Existing V1 MB-EP hardware feedback is baseline evidence; other models rely on DEX, synthetic inputs, simulated GATT and local regressions.

UI regression exposed a delayed service-destruction race that could pause the next record. Intentional idle shutdown now leaves subsequent use alone, and idle-stop requests are ignored while starting or in use. Unexpected service loss is pause-only and bound to the matching session ID. Regression checks cover stale IDs, repeated loss signals and three consecutive immediate exit/start interactions.

Both signed v0.1.1 variants were upgraded in place on API 37 with nonempty data: Room 5 to 7, one session, 240 samples, a custom workout and two stages. Every old column value and nondefault preference was preserved. Separate unit tests cover older schema migrations; this is not a claim that every historical APK was installed. A 360,001-sample backup roundtrip passes full-metric digest comparison: 51,279,708 bytes compressed (48.9 MiB), 117,217,091 bytes expanded (111.8 MiB). Native API 29 document-picker tests verify export, preview, selective restore and retained unselected settings. Session CSV v5 and sample CSV v6 remain unchanged.

Actual baseline screenshots preceded the approved image-generated designs. The implementation preserves a single row of four home statistics, compact portrait controls with inline units, rightmost floating actions in both modes, and shorter landscape chrome. All 343 workflow/environment checks are complete, comprising 336 unique cases and 3,221 selected screenshots, with both operation results and image-reading reviews recorded. Coverage includes nine layouts at 1.0/1.3/2.0 font scale in both themes, 1.5 input/action-bar supplements, six locales on phone/tablet, and risk combinations including 16:9. Twelve additional portrait screenshots use the final R48 Debug APK on API 29, and the regular APK starts offline on API 34. Obscured or ANR captures are excluded and rerun.

Follow-up fixes include keyboard-safe form actions, readable stage remaining time, accurate range-validation messages, accessible overlay expansion, and scrollable finish-confirmation explanations with fixed actions. Native notifications use the system chronometer while recording. The final R48 APK passes three consecutive immediate exit/start interactions and actual notification open/pause/disconnect/reconnect/resume/exit flows on API 29 in Chinese and API 34 in German; reconnecting still requires explicit resume. Earlier idle timeouts and premature rotation captures remain in the evidence alongside targeted reruns. The index records actual APK hashes and environments; regression evidence composes several revisions rather than claiming every scene was rerun on the final binary.

The API 29 background run lasted 3,601,866 ms and produced 3,158 samples, with a maximum 10,967 ms gap under software emulation. It covered desktop/video transitions, overlays, screen off/on, synthetic disconnect and explicit resume, then save/exit with no remaining service or wake lock. It had no BLE radio and zero GATT transport commands. The video ended while the screen was off; continuous one-hour video playback and vendor battery policies were not validated.

API 29 and 34 provide runtime evidence. API 37 supports the oracle and upgrade checks but repeated SystemUI/Launcher ANRs and black screens prevent trustworthy GUI coverage. This target-system GUI gap is explicit. Simulated foldable windows do not validate a physical hinge.

The local evidence root is `$DEV_TEMP_BASE/work/open-mobifitness-v020/`, with `baseline`, `design`, `qa`, `reports` and `candidate` directories. Raw APKs, private protocol logs, decompiled materials and image-generation prompts remain local.
