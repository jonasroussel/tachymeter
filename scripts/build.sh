#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"
export PATH="$JAVA_HOME/bin:$PATH"
SDK="${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}"
BT="$SDK/build-tools/36.0.0"
PLATFORM="${ANDROID_JAR:-$SDK/platforms/android-37.0/android.jar}"
KOTLIN="${KOTLIN_HOME:-$PWD/.tools/kotlinc}"
mkdir -p build/classes build/generated build/dex dist signing
"$BT/aapt2" compile --dir app/src/main/res -o build/resources.zip
sed 's/<manifest /<manifest package="app.tachymeter" /' app/src/main/AndroidManifest.xml > build/AndroidManifest.xml
"$BT/aapt2" link -o build/resources.apk -I "$PLATFORM" --manifest build/AndroidManifest.xml --java build/generated build/resources.zip
"$KOTLIN/bin/kotlinc" app/src/main/kotlin -classpath "$PLATFORM" -jvm-target 1.8 -no-reflect -d build/classes.jar
"$BT/d8" --lib "$PLATFORM" --min-api 26 --output build/dex build/classes.jar "$KOTLIN/lib/kotlin-stdlib.jar"
cp build/resources.apk build/unsigned.apk
(cd build/dex && zip -q ../unsigned.apk classes*.dex)
"$BT/zipalign" -f -p 4 build/unsigned.apk build/aligned.apk
if [[ ! -f signing/tachymeter.p12 ]]; then
    umask 077
    openssl rand -hex 24 > signing/password.txt
    keytool -genkeypair -keystore signing/tachymeter.p12 -storetype PKCS12 -storepass:file signing/password.txt -alias tachymeter -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=Tachymeter Personal, O=Personal Apps' -noprompt
fi
"$BT/apksigner" sign --ks signing/tachymeter.p12 --ks-pass file:signing/password.txt --ks-key-alias tachymeter --out dist/tachymeter-1.3.apk build/aligned.apk
"$BT/apksigner" verify --verbose --print-certs dist/tachymeter-1.3.apk > dist/signature.txt
"$BT/zipalign" -c -v 4 dist/tachymeter-1.3.apk > build/alignment.txt
shasum -a 256 dist/tachymeter-1.3.apk > dist/SHA256SUMS
echo 'Built and verified dist/tachymeter-1.3.apk'
