<p align="center">
  <a href=""><img width="200" height="200" src="https://github.com/meshtastic/Meshtastic-Android/blob/master/app/src/main/res/mipmap-xxxhdpi/ic_launcher2.png"></a>
</p>
<h1 align="center">Meshtastic-Android</h1>

![GitHub all releases](https://img.shields.io/github/downloads/meshtastic/meshtastic-android/total)
[![Android CI](https://github.com/meshtastic/Meshtastic-Android/actions/workflows/android.yml/badge.svg)](https://github.com/meshtastic/Meshtastic-Android/actions/workflows/android.yml)
[![Crowdin](https://badges.crowdin.net/e/f440f1a5e094a5858dd86deb1adfe83d/localized.svg)](https://crowdin.meshtastic.org/android)
[![CLA assistant](https://cla-assistant.io/readme/badge/meshtastic/Meshtastic-Android)](https://cla-assistant.io/meshtastic/Meshtastic-Android)
[![Fiscal Contributors](https://opencollective.com/meshtastic/tiers/badge.svg?label=Fiscal%20Contributors&color=deeppink)](https://opencollective.com/meshtastic/)
[![Vercel](https://img.shields.io/static/v1?label=Powered%20by&message=Vercel&style=flat&logo=vercel&color=000000)](https://vercel.com?utm_source=meshtastic&utm_campaign=oss)

This is a tool for using Android with open-source mesh radios. For more information see our webpage: [meshtastic.org](https://www.meshtastic.org). If you are looking for the the device side code, see [here](https://github.com/meshtastic/Meshtastic-device).

This project is currently beta testing, if you have questions or feedback
please [Join our discussion forum](https://meshtastic.discourse.group/). We would love to hear from
you!

[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png"
alt="Get it on F-Droid"
width="32%">](https://f-droid.org/packages/com.geeksville.mesh/)
[<img src="https://gitlab.com/IzzyOnDroid/repo/-/raw/master/assets/IzzyOnDroid.png"
alt="Get it on IzzyOnDroid"
width="32%">](https://apt.izzysoft.de/fdroid/index/apk/com.geeksville.mesh)
[<img src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png"
alt="Download at https://play.google.com/store/apps/details?id=com.geeksville.mesh]"
width="32%">](https://play.google.com/store/apps/details?id=com.geeksville.mesh&referrer=utm_source%3Dgithub-android-readme)

If you want to join the Play Store testing program go to [this URL](https://play.google.com/apps/testing/com.geeksville.mesh) and opt-in to become a tester.
If you encounter any problems or have questions, [post in the forum](https://meshtastic.discourse.group/) and we'll help.

However, if you must use 'raw' APKs you can get them from our [github releases](https://github.com/meshtastic/Meshtastic-Android/releases). This is not recommended because if you manually install an APK it will not automatically update.

## Fork Features
* Request User-Info for node - if your node is defined as "Unknown user", you can ask it to send its Long Name and Short Name
* Last contact on map instead of last position
* Web Browser for Node! (the server will be available soon)
* Packet Counter for node!

## Translations

You can help translate the app into your native language using [Crowdin](https://crowdin.meshtastic.org/android).

## Build instructions

If you would like to develop this application we'd love your help! These build instructions are brief and should be improved, please send a PR if you can.

- Use Android Studio to build/debug
- Use "git submodule update --init --recursive" to pull in the various submodules we depend on
- There are a few config files which you'll need to copy from templates included in the project. Run
  the following commands to do so:

```bash
rm ./app/google-services.json
cp ./app/google-services-example.json ./app/google-services.json
```

- Now you should be able to select "Run / Run" in the IDE and it will happily start running on your
  phone or the emulator. Note: The emulators don't support bluetooth, so some features can not be
  used in that environment.

## Analytics setup

- analytics are included but can be disabled by the user on the settings screen

- on dev devices

```bash
adb shell setprop debug.firebase.analytics.app com.geeksville.mesh
adb shell setprop log.tag.FirebaseCrashlytics DEBUG
```

for verbose logging:

```bash
adb shell setprop log.tag.FA VERBOSE
```

Copyright 2024, Meshtastic LLC. GPL-3.0 license
