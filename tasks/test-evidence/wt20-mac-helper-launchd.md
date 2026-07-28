# WT20 Mac helper launchd installer

Status: installer scaffold added and syntax/lint checked. Not installed.

Changed:
- Added LaunchAgent plist template for `app.codecks.mac-helper`.
- Added `install-launchd.sh`.
  - Builds the Swift helper release binary.
  - Copies it into user Application Support.
  - Creates `helper.json` with `0600` permissions if absent.
  - Keeps shared secret in config file, not launchd environment.
  - Bootstraps user LaunchAgent when explicitly run.
- Added `uninstall-launchd.sh`.
  - Removes only the user LaunchAgent plist.
  - Preserves helper binary/config.

Safety:
- Installer was not executed.
- No launchctl command was run.
- No file was installed into `~/Library`.
- No phone/APK touched.

Verification:
- `bash -n macHelper/scripts/install-launchd.sh`
  - Passed.
- `bash -n macHelper/scripts/uninstall-launchd.sh`
  - Passed.
- `plutil -lint macHelper/launchd/app.codecks.mac-helper.plist.template`
  - Passed.
