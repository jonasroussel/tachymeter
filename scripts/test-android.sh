#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"
export PATH="$JAVA_HOME/bin:$PATH"
SDK="${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}"
BT="$SDK/build-tools/36.0.0"
PLATFORM="${ANDROID_JAR:-$SDK/platforms/android-37.0/android.jar}"
KOTLIN="${KOTLIN_HOME:-$PWD/.tools/kotlinc}"
ADB="$SDK/platform-tools/adb"
SERIAL="${ANDROID_SERIAL:-emulator-5556}"
mkdir -p build/android-tests/dex
"$KOTLIN/bin/kotlinc" tests/android/SmokeTests.kt -classpath "$PLATFORM:build/classes.jar" -jvm-target 1.8 -d build/android-tests/tests.jar
"$BT/d8" --lib "$PLATFORM" --classpath build/classes.jar --classpath "$KOTLIN/lib/kotlin-stdlib.jar" --min-api 26 --output build/android-tests/dex build/android-tests/tests.jar
"$BT/aapt2" link -o build/android-tests/tests.apk -I "$PLATFORM" --manifest tests/android/AndroidManifest.xml
(cd build/android-tests/dex && zip -q ../tests.apk classes.dex)
"$BT/zipalign" -f 4 build/android-tests/tests.apk build/android-tests/aligned.apk
"$BT/apksigner" sign --ks signing/tachymeter.p12 --ks-pass file:signing/password.txt --out build/android-tests/signed.apk build/android-tests/aligned.apk
"$ADB" -s "$SERIAL" install -r dist/tachymeter-1.3.apk
"$ADB" -s "$SERIAL" install -r build/android-tests/signed.apk
"$ADB" -s "$SERIAL" shell pm grant app.tachymeter android.permission.ACCESS_COARSE_LOCATION
"$ADB" -s "$SERIAL" shell pm grant app.tachymeter android.permission.ACCESS_FINE_LOCATION
"$ADB" -s "$SERIAL" shell am instrument -w app.tachymeter.tests/app.tachymeter.tests.SmokeTests | tee build/android-tests/results.txt
if ! rg -q 'PASS:' build/android-tests/results.txt; then exit 1; fi
"$ADB" -s "$SERIAL" pull /sdcard/Android/data/app.tachymeter/files/speed-test.png dist/speed-test.png
"$ADB" -s "$SERIAL" pull /sdcard/Android/data/app.tachymeter/files/icon.png dist/icon.png
