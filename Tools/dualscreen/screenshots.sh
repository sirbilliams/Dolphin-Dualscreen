#!/usr/bin/env bash
# Screenshots of Dolphin Dualscreen on a running emulator, for checking the look without a Thor.
#
#   screenshots.sh <apk> <out dir>
#
# For each theme (Dolphin's default and Pluto's eight) it photographs:
#   preview-<theme>.png   the bottom-screen preview (the Luigi's Mansion companion with sample values, framed)
#   main-<theme>.png      the game list
#   settings-<theme>.png  Settings -> Interface (where the Theme picker is)
# then turns on a simulated second display and photographs the preview again (second-display.png), with the
# companion shown on that display as on the Thor.
set -u

APK="$1"
OUT="$2"
PKG=io.github.sirbilliams.dolphindualscreen
PREVIEW=org.dolphinemu.dolphinemu.features.dualscreen.CompanionPreviewActivity
mkdir -p "$OUT"

shot() { adb exec-out screencap -p > "$OUT/$1.png" && echo "shot $1"; }
wait_idle() { sleep "${1:-4}"; }

adb install -r -g "$APK" | tee "$OUT/install.txt"
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS 2>/dev/null || true

# First start: Dolphin sets up its folders, and may ask about analytics once.
adb shell am start -W -n $PKG/org.dolphinemu.dolphinemu.ui.main.MainActivity
wait_idle 15
shot first-start
adb shell input keyevent KEYCODE_BACK
wait_idle 2

for theme in 10 11 12 13 14 15 16 17 0; do
  adb shell am force-stop $PKG
  adb shell am start -W -n $PKG/$PREVIEW --ei theme $theme
  wait_idle 8                                   # (switching the theme recreates the screen)
  shot "preview-$theme"
  adb shell am start -W -n $PKG/$PREVIEW --ei theme $theme --es open main
  wait_idle 6
  shot "main-$theme"
  adb shell am start -W -n $PKG/$PREVIEW --ei theme $theme --es open settings
  wait_idle 6
  shot "settings-$theme"
done

# A simulated second display, as on the Thor (its lower screen is 1080 x 1240). It shows as a window over the
# main screen on the emulator.
adb shell am force-stop $PKG
adb shell settings put global overlay_display_devices "1080x1240/320"
wait_idle 5
adb shell dumpsys display | grep -E "mDisplayId=|DisplayDeviceInfo\{" | head -20 > "$OUT/displays.txt"
adb shell am start -W -n $PKG/$PREVIEW --ei theme 10
wait_idle 8
shot second-display
adb shell settings delete global overlay_display_devices

# The app's own log of the companion screen (display detection etc.)
adb shell run-as $PKG cat files/Dualscreen/log.txt > "$OUT/companion-log.txt" 2>/dev/null ||
  adb shell cat /sdcard/Android/data/$PKG/files/Dualscreen/log.txt > "$OUT/companion-log.txt" 2>/dev/null || true
adb logcat -d > "$OUT/logcat.txt" || true
ls -la "$OUT"
