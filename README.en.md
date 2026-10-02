# OpenMOBI

**Keep your Mobi fitness equipment moving.**

[简体中文](README.md) · English

OpenMOBI is an independent Android app for using Mobi fitness equipment after the original services shut down. Connect over Bluetooth, adjust resistance and follow workouts without an account. Your workout history stays on your device.

## Download

**[Download v0.1.1 — regular app](https://github.com/q1ngyang/open-mobifitness/releases/download/v0.1.1/OpenMOBI-0.1.1.apk)** · [All releases](https://github.com/q1ngyang/open-mobifitness/releases) · [Release notes](docs/releases/v0.1.1.md#english)

Requires Android 10 or later on a 64-bit phone or tablet. It can be installed alongside the original app.

**For everyday use, choose the APK without `debug` in its filename.** The Debug app includes simulated equipment for testing and keeps its data separate from the regular app.

So far, user testing covers **Mobi MB-EP ellipticals using the V1 protocol**. Other bikes, rowers and treadmills still need model-by-model testing. This version does not start treadmill motors or control their speed or incline. [Check device support](docs/COMPATIBILITY.md#english)

## What you can do

- **Work out offline:** adjust resistance yourself or follow one of 21 built-in plans. Search, save favorites and create your own.
- **Watch while you train:** use a full workout screen or a two-size floating panel, with your choice of metrics.
- **See your progress:** today's activity, history, trends and workout details, plus readable CSV reports and local backups.
- **Use your screen comfortably:** phone, tablet and foldable-window layouts, light/dark themes and six interface languages.

## Screenshots

<img src="docs/screenshots/home-phone.png" alt="Free workout and workout library on a phone" width="270"> <img src="docs/screenshots/workout-phone.png" alt="Workout progress, selectable metrics and resistance control" width="270">

<img src="docs/screenshots/home-tablet.png" alt="Free workout and today's activity on a tablet in dark mode" width="800">

Actual emulator captures with test connection states and sample workout data. [More screenshots](docs/SCREENSHOTS.md)

## Bluetooth trouble? Please report it

**Can't find your equipment, receive readings or change resistance? We'd like to hear from you.** In Settings → Device diagnostics, make sure Record Bluetooth packets is on. Connect and reproduce the problem, then export the log promptly.

[Open an issue](https://github.com/q1ngyang/open-mobifitness/issues/new/choose) with your equipment model, phone/tablet model, Android version and log. [Step-by-step help](docs/HELP.md#english) is available if you need it.

## Learn more

[Getting started](docs/USER_GUIDE.md#english) · [Workouts and estimates](docs/TRAINING.md#english) · [Privacy and permissions](docs/PRIVACY.md#english) · [Documentation](docs/README.md) · [Development](docs/DEVELOPMENT.md#english)

OpenMOBI is not affiliated with the original manufacturer. New code uses [Apache-2.0](LICENSE); rights to the original wordmark remain with its owners. See [asset credits](docs/BRANDING.md#english). Author: [q1ngyang](https://x.com/q1ngyang).
