# Codecks

Turn an Android phone, tablet, or Samsung DeX window into a local-first command deck, trackpad, and automation surface for your Mac.

[![License: Apache-2.0](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Android-9%2B-3ddc84.svg)](app/build.gradle.kts)
[![Kotlin](https://img.shields.io/badge/Kotlin-Compose-f18e33.svg)](app/build.gradle.kts)
[![Local-first](https://img.shields.io/badge/local--first-no%20account-2fdf84.svg)](PRIVACY.md)

![Codecks screenshot gallery](docs/images/screenshot-gallery.png)

## Demo

![Codecks demo](docs/images/codecks-demo.gif)

## Why It Exists

Mac shortcuts are fast until you need the command you never remember. Codecks gives you a second-screen control surface: big command keys, a Bluetooth trackpad, and reviewable automations that stay local by default.

## Highlights

- **Command deck:** Finder, Terminal, Spaces, media, screenshots, browser tabs, and custom Mac commands.
- **Trackpad:** Bluetooth HID pointer controls with gestures, scrolling, haptics, rotation, and optional screen pinning.
- **Automations:** local When / If / Then recipes with safe templates, test-before-enable, and run history.
- **AI-assisted drafting:** optional provider calls can draft buttons and automations; generated actions stay disabled until reviewed.
- **DeX-ready layouts:** phone, tablet, landscape, freeform, and desktop windows.
- **No hosted account:** no Codecks backend, analytics SDK, advertising SDK, public database, or cloud sync.

## Safety Model

Codecks can run commands on a Mac you configure, so the app is built around review and restraint:

- built-in templates use an allowlist;
- dangerous shell patterns are blocked;
- generated automations are disabled until the user tests and enables them;
- SSH host keys are pinned;
- optional AI API keys are encrypted with Android Keystore;
- diagnostic text is redacted before display.

Use a non-admin Mac account and review every custom command before enabling it. See [Security](SECURITY.md) and [Privacy](PRIVACY.md).

## Install

Download the signed APK and `SHA256SUMS.txt` from the [latest GitHub release](https://github.com/vaddisrinivas/codecks/releases/latest). Android may ask you to allow installation from your browser or file manager.

Requirements:

- Android 9 or newer.
- macOS with Remote Login enabled for Deck and Automations.
- A compatible paired Bluetooth host for HID Trackpad controls.

## Build

```bash
git clone https://github.com/vaddisrinivas/codecks.git
cd codecks
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Debug APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Release signing instructions live in [docs/release/RELEASING.md](docs/release/RELEASING.md).

## Project Status

`v0.1.23` is the current public beta. Core deck, trackpad, keyboard, clipboard, rules, editing, settings, optional AI-assisted drafting, and default-off Smart/Reactive foundations are implemented in the single signed APK. Broader physical-device coverage, TalkBack validation, and longer crash-free field testing remain GA gates. See the [production launch plan](docs/release/PRODUCTION_LAUNCH_PLAN.md).

## FOSS Distribution

Codecks is Apache-2.0, source-available, account-free, and prepared for FOSS directory review. Distribution notes and anti-feature disclosures are tracked in [docs/distribution/FOSS_READINESS.md](docs/distribution/FOSS_READINESS.md). Fastlane/IzzyOnDroid metadata lives in [fastlane/metadata/android/en-US](fastlane/metadata/android/en-US).

## Use the screen already beside your computer

Phones are commonly already within reach. In a 2013 CHI phone-placement study,
68% of 650 respondents had their phone on a table or desk when asked, and 83%
of 693 respondents had placed it there during the previous 24 hours.[^phone-placement]
Current Pew data says 91% of U.S. adults own a smartphone.[^pew-ownership]

Codecks turns that existing screen into a Bluetooth trackpad and command
surface. That can avoid another pointer purchase and free mousepad space for
writing, devices, or simply a less crowded desk.

A July 27, 2026 manufacturer snapshot of 18 visible Logitech catalog entries
ranged from $20.00 to $119.99 and averaged $65.55.[^pointer-snapshot] Two
sampled Razer Gigantus V2 SKU pages were $15.00 and $20.00, putting that small
illustrative mouse-plus-pad snapshot around $83.05 before tax and shipping.[^pad-snapshot]
The current Gigantus V2 medium and large size table spans about 153 to 279
square inches.[^pad-snapshot]

For desk scale, three current IKEA desk examples averaged about 846 square
inches of surface and $129.99 in price, or about $0.15 per square inch.[^desk-snapshot]
A 153-to-279-square-inch pad represents roughly 18% to 33% of that illustrative
surface, with a $24 to $43 space allocation.[^desk-snapshot] That is an
opportunity-cost illustration, not cash Codecks promises to recover. Your
result depends on your desk, pointer, pad, phone, and whether the phone was
already sitting there.

## Tradeoffs

- Initial pairing and minor setup are required. Deck and automation commands
  also require a configured Mac connection.
- A touchscreen trackpad does not feel identical to a dedicated mouse. Pointer
  speed, tap thresholds, gestures, and muscle memory may need adjustment.
- Bluetooth, Android background rules, lockscreen behavior, and vendor power
  management can affect availability.
- The phone remains occupied while used as a full-screen trackpad.
- Codecks does not claim ergonomic superiority over a physical mouse. Use
  whichever input method is more comfortable for the task.

## Contributing

Bug reports, device compatibility notes, accessibility findings, and safe automation templates are welcome. Start with [CONTRIBUTING.md](CONTRIBUTING.md).

## License

Codecks is available under the [Apache License 2.0](LICENSE). Third-party Android libraries retain their own licenses; in-app notices are generated from `app/src/main/assets/open_source_notices.txt`.

Codecks is an independent project. It is not affiliated with OpenAI, Toggl, Work Louder, Samsung, Google, or Apple.

[^phone-placement]: Wiese, Saponas, and Brush, ["Phoneprioception," CHI 2013](https://citeseerx.ist.psu.edu/document?doi=16baf1a983217b965bd72b868086126e3e24634c&repid=rep1&type=pdf). This was not a current population-representative survey, and "table or desk" did not specifically mean "computer desk."
[^pew-ownership]: Pew Research Center, ["Mobile phone ownership"](https://www.pewresearch.org/chart/mobile-phone-ownership-2/), chart updated through June 18, 2025.
[^pointer-snapshot]: [Logitech US mice catalog](https://www.logitech.com/en-us/shop/c/mice), captured July 27, 2026. The sample used the first 18 visible catalog prices only. It is not sales-weighted and not a market average.
[^pad-snapshot]: [Razer Gigantus V2 overview](https://www.razer.com/gaming-mouse-mats/razer-gigantus-v2) and two [sampled SKU pages](https://www.razer.com/gaming-mouse-mats/Razer-Gigantus-V2/RZ02-03330200-R3U1), captured July 27, 2026. Prices and SKU mix can change quickly.
[^desk-snapshot]: [IKEA US desks listing](https://www.ikea.com/us/en/cat/desks-computer-desks-20649/), captured July 27, 2026 using MICKE, KALLAX desk, and LAGKAPTEN / ALEX as a small illustrative sample. It is not a market average.
