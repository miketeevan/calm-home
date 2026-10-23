#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [ -n "${JAVA_HOME:-}" ]; then export PATH="$JAVA_HOME/bin:$PATH"; fi
SDK="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$PWD/tools/sdk}}"
BT="$SDK/build-tools/35.0.0"
mkdir -p build/classes private
chmod 700 private
if [ ! -f private/signing.pass ]; then
  openssl rand -hex 24 > private/signing.pass
  chmod 600 private/signing.pass
fi
if [ ! -f private/calmhome.jks ]; then
  keytool -genkeypair -keystore private/calmhome.jks -storepass:file private/signing.pass -keypass:file private/signing.pass -alias calmhome -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=Calm Home local build' >/dev/null
  chmod 600 private/calmhome.jks
fi
"$BT/aapt" package -f -M source/AndroidManifest.xml -A source/assets -S source/res -I "$SDK/platforms/android-30/android.jar" -F build/unsigned.apk
javac --release 8 -classpath "$SDK/platforms/android-30/android.jar" -d build/classes source/*.java
"$BT/d8" --lib "$SDK/platforms/android-30/android.jar" --min-api 26 --output build build/classes/uk/michael/calmhome/*.class
(cd build && zip -q unsigned.apk classes.dex)
"$BT/zipalign" -f 4 build/unsigned.apk build/aligned.apk
"$BT/apksigner" sign --ks private/calmhome.jks --ks-key-alias calmhome --ks-pass file:private/signing.pass --out build/calmhome.apk build/aligned.apk
"$BT/apksigner" verify build/calmhome.apk
ls -lh build/calmhome.apk
