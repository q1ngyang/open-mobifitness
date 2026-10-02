# OpenMOBI 品牌素材

alpha.3 按用户要求使用提供的 MOBI FITNESS 官方字标，在上方添加较小的 Open 胶囊，并用粉、红、黄、蓝四段细线作为装饰。它表示独立 OpenMOBI 项目，不表示原公司的官方发行或背书。Apache-2.0 适用于本项目新增代码，不授予原字标的商标权。

## 已落地素材

- `app/src/main/res/drawable-nodpi/brand_light.png`：白底黑字，用于浅色应用内品牌区和普通启动器图标。
- `app/src/main/res/drawable-nodpi/brand_dark.png`：深灰底白字，用于深色应用内品牌区及系统夜间图标资源。
- `drawable/ic_launcher_foreground.xml` / `drawable-night/ic_launcher_foreground.xml`：适应 Android 图标遮罩的安全留白。
- `drawable/ic_notification.xml`：状态栏使用的 Open＋MOBI 单色矢量；展开通知同时显示完整品牌位图。
- `drawable/ic_brand_monochrome.xml`：Android 单色主题图标，替代已删除的初版 `ic_mark.xml`。

应用内品牌随 OpenMOBI 的浅色／深色设置变化；桌面图标由启动器和系统夜间资源选择决定，不保证所有厂商启动器会立即刷新，也不更换组件或创建第二个桌面入口。

## 生成与选择记录

使用内置 image_gen 编辑工具，以用户提供的 480 × 480 官方字标为编辑目标。两次透明底试稿出现纹理或透明度问题，未采用。最终采用完全不透明、无纹理的浅色版本，再以该版本生成对应深色版本；检查文字、居中、装饰色和应用实际显示后复制入上述资源路径。未调用外部图像 API 或另写图像编辑脚本。

浅色最终提示词：

> Edit the supplied MOBI FITNESS wordmark. Preserve the clean black MOBI and FITNESS letter shapes, hierarchy and spacing. Solid opaque pure white background. Add a small rounded black Open capsule with white text above MOBI, with a fine underline of four equal pink, red, yellow and blue segments. Center the stack, with the mark within x 15–85% and y 25–75% of a square canvas, ample margin. Open must be smaller than MOBI. Flat crisp artwork. No distress, texture, grain, gradient, shadow or mockup.

深色最终提示词：

> Make the exact matching dark version of this light artwork. Change only colors: background to flat #111318, MOBI FITNESS letters to #F6F7F9, Open capsule to #2A2E36 with white text. Retain the four accent colors and the same positions and canvas. Fully opaque, clean flat edges. No textures, grain, gradients or artifacts.

提示词记录描述本次约束与迭代意图；生成式输出并非确定性构建步骤。发行包直接使用版本控制中的最终 PNG，构建过程无需重新生成图片。


Alpha 7：删除初版 `ic_mark.xml`。通知使用 `ic_notification.xml`，系统主题图标使用 `ic_brand_monochrome.xml`，均为新版 Open 胶囊＋MOBI 字形的单色简化；展开通知仍使用已批准的 `brand_light` 完整图。启动图标的浅／深色位图未改动。轮廓字体许可位于 `design/alpha7/DejaVu-license.txt`。
