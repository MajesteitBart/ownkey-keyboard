#!/usr/bin/env bash
# Builds the benchmark agent APK with plain SDK tools (no Gradle).
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
SDK="${ANDROID_HOME:-$HOME/Android/Sdk}"
BT="$SDK/build-tools/36.0.0"
JAR="$SDK/platforms/android-36/android.jar"
OUT="$HERE/build"
rm -rf "$OUT"; mkdir -p "$OUT/classes" "$OUT/dex"
"$BT/aapt2" link -o "$OUT/base.apk" --manifest "$HERE/AndroidManifest.xml" -I "$JAR" \
  --min-sdk-version 30 --target-sdk-version 36
javac -source 11 -target 11 -nowarn -d "$OUT/classes" -classpath "$JAR" $(find "$HERE/src" -name '*.java') 2>&1 | grep -v "bootstrap classpath" || true
"$BT/d8" --min-api 30 --lib "$JAR" --output "$OUT/dex" $(find "$OUT/classes" -name '*.class')
cp "$OUT/base.apk" "$OUT/unsigned.apk"
(cd "$OUT/dex" && zip -q -j "$OUT/unsigned.apk" classes.dex)
"$BT/zipalign" -f 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"
"$BT/apksigner" sign --ks "$HOME/.android/debug.keystore" --ks-pass pass:android --key-pass pass:android \
  --out "$OUT/kbagent.apk" "$OUT/aligned.apk"
echo "$OUT/kbagent.apk"
