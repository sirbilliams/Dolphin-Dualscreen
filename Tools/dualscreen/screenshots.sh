#!/usr/bin/env bash
# Screenshots of Dolphin Dualscreen on a running emulator, for checking the look without a Thor.
#
#   screenshots.sh <apk> <out dir>
#
# For each theme (Pluto's eight and Dolphin's default) it photographs:
#   preview-<theme>.png   the bottom-screen preview (the Luigi's Mansion companion with sample values)
#   main-<theme>.png      the game list
#   settings-<theme>.png  Settings -> Interface (where the Theme picker is)
# then turns on a simulated second display and photographs the preview again (second-display.png), with the
# companion shown on that display as on the Thor (on the emulator it appears as a window over the main screen).
#
# The emulator has "Remove animations" on, so skies and the companion stand still: the shots are steady, and a
# software-rendered emulator isn't swamped. Every step has a time limit so one hang can't eat the run.
set -u

APK="$1"
OUT="$2"
PKG=io.github.sirbilliams.dolphindualscreen
PREVIEW=org.dolphinemu.dolphinemu.features.dualscreen.CompanionPreviewActivity
mkdir -p "$OUT"

t() { timeout "$@"; }
shot() {
  for try in 1 2 3; do
    t 30 adb exec-out screencap -p > "$OUT/$1.png" 2>/dev/null
    if [ -s "$OUT/$1.png" ]; then echo "shot $1"; return 0; fi
    echo "screencap for $1 failed (try $try)"; sleep 5
  done
  rm -f "$OUT/$1.png"
}
# Starts an activity fresh (-S stops the app first, so Android doesn't just bring back the last screen).
start() { t 60 adb shell am start -S -W -n "$PKG/$1" "${@:2}" > /dev/null; }

t 300 adb install -r -g "$APK" 2>&1 | tee "$OUT/install.txt"
if ! t 30 adb shell pm path $PKG > /dev/null 2>&1; then
  echo "::error::The APK didn't install on the emulator (see install.txt)."
  exit 1
fi
t 30 adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS 2>/dev/null || true

# First start: Dolphin sets up its folders, and may ask about analytics once.
start org.dolphinemu.dolphinemu.ui.main.MainActivity
sleep 15
shot first-start
t 10 adb shell input keyevent KEYCODE_BACK
sleep 2

for theme in 10 11 12 13 14 15 16 17 0; do
  start $PREVIEW --ei theme $theme
  sleep 8                                   # (switching the theme recreates the screen)
  shot "preview-$theme"
  start $PREVIEW --ei theme $theme --es open main
  sleep 7
  shot "main-$theme"
  start $PREVIEW --ei theme $theme --es open settings
  sleep 7
  shot "settings-$theme"
done

# A simulated second display, as on the Thor (its lower screen is 1080 x 1240).
t 30 adb shell settings put global overlay_display_devices "1080x1240/320"
sleep 6
t 30 adb shell dumpsys display | grep -E "mDisplayId=|DisplayDeviceInfo\{" | head -20 > "$OUT/displays.txt"
start $PREVIEW --ei theme 10
sleep 8
shot second-display
t 30 adb shell settings delete global overlay_display_devices

# The app's own log of the companion screen (display detection etc.), and the system log.
t 30 adb shell cat /sdcard/Android/data/$PKG/files/Dualscreen/log.txt > "$OUT/companion-log.txt" 2>/dev/null || true
t 60 adb logcat -d > "$OUT/logcat.txt" 2>/dev/null || true
ls -la "$OUT"
