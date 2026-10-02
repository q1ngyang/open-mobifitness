# 连接帮助与问题反馈 / Help and feedback

[首页](../README.md) · [English](#english)

## 先试这几步

1. 唤醒器材、打开蓝牙，并关闭其他连接它的应用。如果刚切换 App，等几秒再搜索。
2. 确认已允许附近设备权限。Android 10–11 还需允许位置权限并打开系统定位。
3. 在「设备」页连接后，轻踩或拉动几秒，查看运动数据是否更新。连接成功不一定代表已经收到运动数据。
4. 仍有问题时，按下面的方法记录。**不需要知道协议名称，也不需要会看日志。**

## 蓝牙问题：附一份诊断日志

1. 打开「设置 → 设备诊断」，确认「记录蓝牙协议报文」已开启。新安装默认开启；如果你曾手动关闭，需要重新打开。
2. 回到「设备」连接并复现问题。例如轻踩约 15 秒，或尝试相邻档位的调节，记下当时的现象。
3. 尽快回到「设置 → 设备诊断 → 导出诊断日志」，保存 `OpenMOBI-diagnostics.txt`。日志会自动清理，长时间等待会丢失较早的报文。
4. 打开[问题反馈表单](https://github.com/q1ngyang/open-mobifitness/issues/new?template=device-problem.yml)，按提示填写，并把刚保存的 TXT 文件拖入附件区域。手机浏览器也可点附件按钮选择文件。

搜不到器材时也可以导出日志。若无法导出，请写明卡在哪一步，不用等日志齐全才反馈。

## 其他问题与建议

界面排版问题请附截图，写明横／竖屏、是否分屏、字体是否放大。记录或导出出错时，请保留屏幕上的错误提示。不要公开完整运动备份；确实需要时再提供只含测试数据的小样本。

[报告问题](https://github.com/q1ngyang/open-mobifitness/issues/new?template=device-problem.yml) · [提出建议](https://github.com/q1ngyang/open-mobifitness/issues/new?template=feature-request.yml)

提交前快速检查附件：诊断日志不含完整运动历史，但蓝牙报文可能带有运动及心率读数。请遮住截图中的个人信息。GitHub 需要登录后才能提交；也可以先保存文字和附件。

目前只有 V1 椭圆机有用户实机反馈。其他器材能否连接、哪些指标可用，需要你的型号和日志来确认；详见[设备支持](COMPATIBILITY.md)。

## English

### Try these steps first

1. Wake the equipment, enable Bluetooth and close other apps connected to it. Wait a few seconds after switching apps before scanning again.
2. Allow Nearby devices permission. Android 10–11 also needs location permission and the system location switch.
3. Connect from Devices and move gently for a few seconds. Check whether readings change: a Bluetooth connection alone does not guarantee workout data is arriving.
4. If the problem continues, follow the steps below. **You do not need to know the protocol or understand the log.**

### For Bluetooth issues, attach a diagnostic log

1. Open Settings → Device diagnostics and check that Record Bluetooth packets is on. It defaults to on for new installs, but preserves a previous choice to turn it off.
2. Connect and reproduce the problem. For example, move gently for about 15 seconds or change resistance by one level, and note what happened.
3. Promptly return to Settings → Device diagnostics → Export diagnostic log and save `OpenMOBI-diagnostics.txt`. Logs clear automatically, so older packets may disappear if you wait too long.
4. Open the [problem report form](https://github.com/q1ngyang/open-mobifitness/issues/new?template=device-problem.yml) and drag the TXT into its attachment field. On a phone, use the attachment button to pick the file.

You can export a log even if scanning finds nothing. If export itself fails, tell us which step failed; a missing log should not stop you from reporting the issue.

### Other problems and suggestions

For layout issues, attach a screenshot and mention orientation, split screen and enlarged fonts. For history or export failures, include the exact error message. Please do not post a complete workout backup; a small sample containing only test data can be provided later if needed.

[Report a problem](https://github.com/q1ngyang/open-mobifitness/issues/new?template=device-problem.yml) · [Suggest an improvement](https://github.com/q1ngyang/open-mobifitness/issues/new?template=feature-request.yml)

Review attachments before posting. Diagnostic logs do not contain your full history, but Bluetooth packets may include live movement and heart-rate readings. Hide personal details in screenshots. GitHub requires an account to submit; you can save your description and attachments first.

User hardware feedback currently covers V1 ellipticals only. Logs and model information help confirm support for other equipment; see [compatibility](COMPATIBILITY.md#english).
