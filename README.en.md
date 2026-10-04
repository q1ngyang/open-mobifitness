# OpenMOBI

**Keep your Mobi fitness equipment moving.**

[简体中文](README.md) · English

OpenMOBI is an independent Android app for using Mobi fitness equipment after the original services shut down. Connect over Bluetooth, adjust resistance and follow workouts without an account. Your workout history stays on your device.

## Download

**[Download v0.3.0 — regular app](https://github.com/q1ngyang/open-mobifitness/releases/download/v0.3.0/OpenMOBI-0.3.0.apk)** · [All releases](https://github.com/q1ngyang/open-mobifitness/releases) · [Release notes](docs/releases/v0.3.0.md#english)

**v0.3.0** adds local profiles, 70 plans across four equipment types, grouped training reports and improved tablet, phone and floating-panel layouts. [Upgrade and feature guide](docs/V030_GUIDE.md#english)

Requires Android 10 or later on a 64-bit phone or tablet. It can be installed alongside the original app.

**For everyday use, choose the APK without `debug` in its filename.** The Debug app includes simulated equipment for testing and keeps its data separate from the regular app.

So far, user hardware feedback covers **Mobi MB-EP ellipticals using the V1 protocol**; other models have no hardware feedback yet. This version does not start treadmill motors or control their speed or incline. [Check device support](docs/COMPATIBILITY.md#english)

## What you can do

- **Record when you want:** view readings and adjust resistance in control-only mode, then start recording when ready. Follow one of 70 built-in plans across four equipment types or create your own.
- **Share equipment, keep personal history:** local profiles have photo avatars, separate records, plans and preferences, with identity confirmation before recording.
- **Watch while you train:** use the full workout screen or two floating-panel sizes, with selected metrics, saved positions and resistance presets.
- **Set your own pace:** enter frequency or heart-rate ranges for optional hints. They never adjust resistance automatically.
- **Keep your history:** today's totals, past workouts and details, plus readable CSV reports, full backups and settings transfer.
- **Use your screen comfortably:** phone, tablet and foldable-window layouts, light/dark themes and six interface languages.

## Screenshots

<img src="docs/screenshots/v030-metric-units-tablet.png" alt="v0.3.0 tablet workout: progress, six readings and one row of resistance presets" width="1000">

Actual v0.3.0 capture with Debug demo data. [Phones, floating panels and more screenshots](docs/SCREENSHOTS.md)

## Bluetooth trouble? Please report it

**Can't find your equipment, receive readings or change resistance? We'd like to hear from you.** In Settings → Device diagnostics, make sure Record Bluetooth packets is on. Connect and reproduce the problem, then export the log promptly.

[Open an issue](https://github.com/q1ngyang/open-mobifitness/issues/new/choose) with your equipment model, phone/tablet model, Android version and log. [Step-by-step help](docs/HELP.md#english) is available if you need it.

## Learn more

[Getting started](docs/USER_GUIDE.md#english) · [Workouts and estimates](docs/TRAINING.md#english) · [Privacy and permissions](docs/PRIVACY.md#english) · [Documentation](docs/README.md) · [Development](docs/DEVELOPMENT.md#english)

OpenMOBI is not affiliated with the original manufacturer. New code uses [Apache-2.0](LICENSE); rights to the original wordmark remain with its owners. See [asset credits](docs/BRANDING.md#english). Author: [q1ngyang](https://x.com/q1ngyang).
