#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [ -n "${JAVA_HOME:-}" ]; then export PATH="$JAVA_HOME/bin:$PATH"; fi
SDK="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$PWD/tools/sdk}}"
BT="$SDK/build-tools/35.0.0"
API="$SDK/platforms/android-30/android.jar"
mkdir -p build/move-tests/classes build/move-tests/dex
javac --release 8 -classpath "$API:build/classes" -d build/move-tests/classes tests/*.java
"$BT/aapt" package -f -M tests/AndroidManifest.xml -I "$API" -F build/move-tests/unsigned.apk
"$BT/d8" --lib "$API" --min-api 26 --output build/move-tests/dex build/move-tests/classes/uk/michael/calmhome/*.class
(cd build/move-tests/dex && zip -q ../unsigned.apk classes.dex)
"$BT/zipalign" -f 4 build/move-tests/unsigned.apk build/move-tests/aligned.apk
"$BT/apksigner" sign --ks private/calmhome.jks --ks-key-alias calmhome --ks-pass file:private/signing.pass --out build/calm-move-tests.apk build/move-tests/aligned.apk
