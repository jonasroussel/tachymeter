#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/.."
JDK="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"
export JAVA_HOME="$JDK"
KOTLIN="${KOTLIN_HOME:-$PWD/.tools/kotlinc}"
mkdir -p build/tests
"$KOTLIN/bin/kotlinc" app/src/main/kotlin/app/tachymeter/SpeedReading.kt tests/SpeedReadingTest.kt -include-runtime -d build/tests/tests.jar
"$JDK/bin/java" -jar build/tests/tests.jar
