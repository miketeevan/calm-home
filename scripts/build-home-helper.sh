#!/bin/bash
# Build only. This script deliberately has no ADB or install step.
set -euo pipefail
cd "$(dirname "$0")/.."
if [ -n "${JAVA_HOME:-}" ]; then export PATH="$JAVA_HOME/bin:$PATH"; fi
SDK="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$PWD/tools/sdk}}"
BT="$SDK/build-tools/35.0.0"
SRC="${CALM_HELPER_SOURCE:-$PWD/home-helper-tuned}"
mkdir -p build/home-helper/gen build/home-helper/classes build/home-helper/dex
"$BT/aapt2" compile --dir "$SRC/res" -o build/home-helper/resources.zip
"$BT/aapt2" link -o build/home-helper/unsigned.apk -I "$SDK/platforms/android-30/android.jar" --manifest "$SRC/AndroidManifest.xml" -R build/home-helper/resources.zip --java build/home-helper/gen --auto-add-overlay --min-sdk-version 21 --target-sdk-version 34
javac --release 8 -encoding UTF-8 -classpath "$SDK/platforms/android-30/android.jar" -d build/home-helper/classes "$SRC"/src/io/github/toolicious/homeonfire/*.java build/home-helper/gen/io/github/toolicious/homeonfire/R.java
"$BT/d8" --lib "$SDK/platforms/android-30/android.jar" --min-api 21 --output build/home-helper/dex build/home-helper/classes/io/github/toolicious/homeonfire/*.class
(cd build/home-helper/dex && zip -q ../unsigned.apk classes.dex)
"$BT/zipalign" -f 4 build/home-helper/unsigned.apk build/home-helper/aligned.apk
"$BT/apksigner" sign --ks private/calmhome.jks --ks-key-alias calmhome --ks-pass file:private/signing.pass --out build/home-helper/home-on-fire-reviewed.apk build/home-helper/aligned.apk
"$BT/apksigner" verify build/home-helper/home-on-fire-reviewed.apk
"$BT/aapt" dump permissions build/home-helper/home-on-fire-reviewed.apk
ls -lh build/home-helper/home-on-fire-reviewed.apk
