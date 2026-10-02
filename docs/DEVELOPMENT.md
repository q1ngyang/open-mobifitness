# 参与开发 / Development

[文档目录](README.md) · [English](#english)

## 构建

需要 JDK 21、Android SDK（platforms;android-37.0、build-tools;36.0.0）。项目使用 Gradle 9.3.1、AGP 9.1.1、Kotlin 2.2.10，最低 Android 10，目标 API 37。

```sh
export JAVA_HOME=/path/to/jdk-21
export ANDROID_HOME=/path/to/android-sdk
python3 tools/check_resources.py
./tools/build.sh :core:test :app:testDebugUnitTest :app:testReleaseUnitTest :app:lintDebug :app:lintRelease :app:assembleDebug
```

设置 `DEV_TEMP_BASE` 后，构建输出和项目缓存放到该盘的 `build/projects/open-mobifitness`，尊重已有缓存与临时目录设置。未设置时使用 Gradle 默认输出位置。

Debug 包名为 `org.openmobifitness.app.debug`，含演示设备，和普通版 `org.openmobifitness.app` 共存。只发布 arm64-v8a 与 x86_64。请使用模拟器或专用测试设备执行 instrumentation；测试会创建、修改和清理测试应用的数据。

```sh
adb shell appops set org.openmobifitness.app.debug SYSTEM_ALERT_WINDOW allow
./tools/build.sh :app:connectedDebugAndroidTest
```

测试覆盖协议、调阻反馈、训练阶段、导入事务、数据迁移、统计、悬浮窗及响应式界面。模拟测试不证明真实蓝牙链路、器材电机、人体热量估算或厂商后台策略正确。

## 发行

`app/build.gradle.kts` 定义版本。发行签名从环境读取 `OPENMOBI_KEYSTORE`、`OPENMOBI_STORE_PASSWORD`、`OPENMOBI_KEY_PASSWORD`，alias 为 `openmobi`。不提供时只生成未签名 release；CI 不生成替代发行密钥。

```sh
./tools/build.sh :app:assembleRelease :app:assembleDebug
```

发行前检查旧版覆盖升级、包名和签名、64 位库的 16 KiB 对齐，并确认普通版无演示入口。GitHub Release 同时提供普通 APK、Debug APK 和 SHA-256 校验文件；普通版列在前面。发布正文中英文合计不超过 16 行，详细说明放在 `docs/releases/` 并链接到对应标签下的文件。

更新检查按版本号选择：正式版本（含同版本 Debug 包）只检查较新的正式发行，alpha 版本也检查较新的预发布。更新检查只打开 GitHub 发布页，不自动下载或安装。

## 提交内容

- 保留源码、测试、用户说明、格式／协议文档、必要许可和少量真实应用截图。
- 原 APK、完整反编译文件、密钥、私人日志、生成提示词、设计试稿、逐轮测试记录和构建产物留在本地，不提交。
- 旧开发过程目录通过 `.gitignore` 排除；新截图放 `docs/screenshots/`，需要说明测试数据。
- 面向用户的改动请同时写简体中文与英语，中文优先。应用资源还需同步其余四种语言。
- 设备适配请附型号、脱敏数据结构和测试边界，不把演示数据当作实机证明。

目录：`core` 为协议／训练／数据模型，`app/ble` 为蓝牙连接，`app/data` 为存储与更新，`app/service` 为前台服务和悬浮窗，`app/ui` 为 Compose 界面。

## English

### Build

Use JDK 21 and Android SDK packages `platforms;android-37.0` and `build-tools;36.0.0`. The project uses Gradle 9.3.1, AGP 9.1.1 and Kotlin 2.2.10, with minimum Android 10 and target API 37. The build/check commands above apply to both languages.

When `DEV_TEMP_BASE` is set, build output and project caches go under its `build/projects/open-mobifitness` directory, respecting existing cache/temp settings. Otherwise Gradle's default output paths apply.

Debug uses `org.openmobifitness.app.debug`, includes simulated equipment and coexists with regular `org.openmobifitness.app`. APKs include arm64-v8a and x86_64 only. Run instrumentation on an emulator or dedicated test device; tests create, change and clean test-app data. Grant the overlay app-op and run the instrumentation command above.

Tests cover protocols, resistance feedback, stages, import transactions, migrations, statistics, floating panels and responsive layouts. Simulation cannot validate real Bluetooth links, motors, personal energy estimates or vendor background policies.

### Releases

Versions are defined in `app/build.gradle.kts`. Release signing reads `OPENMOBI_KEYSTORE`, `OPENMOBI_STORE_PASSWORD` and `OPENMOBI_KEY_PASSWORD`, using alias `openmobi`. Without them, release output is unsigned; CI never creates a replacement release key.

Before publishing, verify in-place upgrades, package identity/signature, 16 KiB alignment of 64-bit libraries, and absence of demo entry points in the regular app. A release provides regular/Debug APKs and SHA-256 checksums, with the regular app first. Keep the bilingual release body within 16 lines total; link detailed notes from `docs/releases/` at the release tag.

Update checks follow the version number: stable versions (including matching Debug builds) see newer stable releases only; alpha versions also check newer prereleases. The checker opens GitHub's release page and does not automatically download or install.

### What belongs in Git

- Source, tests, user guides, format/protocol documentation, required licenses and a few actual app screenshots.
- Keep original APKs, complete decompilations, keys, private logs, generation prompts, design drafts, per-run test journals and build artifacts local.
- Older process directories are excluded by `.gitignore`. Put selected screenshots in `docs/screenshots/` and disclose test data.
- User-facing information needs Simplified Chinese first and English support. App resource changes also need the other four languages.
- Equipment support changes should identify the model, use sanitized data structures and state test limits. Simulated data is not hardware evidence.

Code layout: `core` holds protocol/workout/data models; `app/ble` Bluetooth; `app/data` storage/updates; `app/service` foreground service/overlays; `app/ui` Compose screens.
