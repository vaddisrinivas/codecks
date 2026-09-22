# Launcher icon policy

## Identity and provenance

| Identity | Meaning | Source and generation input | License |
| --- | --- | --- | --- |
| Robot face (default) | Friendly automation controller in a button grid | Existing `ic_launcher.png`/`ic_launcher_round.png`, introduced by repository commit `bb83f39531445ae60281c7c1a9217cdd70e2ba27`; SHA-256 `a6676149b5d818147ab4df63d3506480027eef6a6dc8995bf24488aa832b28b2` | Existing Codecks project asset under repository Apache-2.0 license |
| Robot grid | Automation controller and four controls | Original vector XML authored for M09B from the text input “generic robot controller in a four-button grid”; geometric primitives only | Apache-2.0 |
| Pointer grid | Pointer control across a button matrix | Original vector XML authored for M09B from the text input “generic mouse pointer over an eight-button grid”; geometric primitives only | Apache-2.0 |
| Minimal green | One control and pointer | Original vector XML authored for M09B from the text input “bright-green circular control with a generic white pointer”; geometric primitives only | Apache-2.0 |

No alternate asset was copied, traced, downloaded, or generated from a third-party
logo. The icons contain no Apple, OpenAI, Android robot, product, or other
third-party marks. “Robot”, “pointer”, and “grid” describe generic shapes. The
trademark review is limited to those shipped shapes and names; future asset
replacement requires a new review and updated digest/provenance record.

## Surface policy

- The exact existing robot-face image remains the default and backs its legacy,
  adaptive color, round, shared splash, live-widget, widget-picker, Settings,
  and About identity. Its adaptive layer uses a 16dp inset around that exact
  unchanged PNG. Android 13 monochrome necessarily uses a one-color derivative.
- Android 12 splash theming belongs to `MainActivity`; `activity-alias` does not
  provide an alias-specific theme. All choices therefore use the shared default robot-face splash. Settings says this explicitly.
- Android widget-picker preview metadata is static. It shows the default robot
  face; an installed widget refreshes to the selected identity.
- Notifications use the selected monochrome identity when the next HID session
  notification is built.
- About reports the selected identity by name.
- Selection is Android-local. The separate Mac helper bundle/Dock icon is not
  changed or synchronized: it can operate when the phone is offline and has no
  authenticated icon-preference transport. Adding that transport is separate
  Mac-helper work and must not make HID startup depend on the helper.

## Visual and recovery contracts

- Alternate adaptive and monochrome vectors use a 108×108 viewport and a named
  0.68-scale group around the 54×54 pivot. This keeps essential geometry inside
  the adaptive safe zone under circle, squircle, rounded-square, and teardrop
  masks.
- Foreground/background palette pairs must meet at least 3:1 non-text contrast.
  Monochrome vectors contain one opaque white source color so the launcher owns
  final light/dark tinting. The pixel proof reads all backgrounds plus light and
  dark monochrome tints from `launcher_icon_colors.xml`; it contains no duplicate
  hard-coded palette.
- The exact default PNG digest, central identity contrast, vector geometry,
  palette contrast, and source/resource mapping are fail-closed unit tests.
- A switch enables and verifies the replacement before disabling any current
  alias. Recovery follows the same rule. If enabling the default fails, recovery
  preserves every surviving launcher and disables none.
- The Gradle matrix contains `ossDebug`, `ossRelease`, `playRelease`, and
  `playInternalRelease`; Play variants intentionally have no debug build.
- `CommercialLabLauncher` is a separate, internal-only diagnostic entry. It is
  never managed as a user-selectable identity. The merged-manifest matrix proof
  requires exactly one enabled primary identity in every variant and classifies
  the additional lab launcher only in `playInternal`.
