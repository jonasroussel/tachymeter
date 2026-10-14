#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p .tools
curl -fL https://github.com/JetBrains/kotlin/releases/download/v2.2.20/kotlin-compiler-2.2.20.zip -o .tools/kotlin-compiler.zip
echo '81f0264c9073b5cbbdb3ff8418cf2c5dac076879fc156fa1a6462f5a5acc4420  .tools/kotlin-compiler.zip' | shasum -a 256 -c -
unzip -qo .tools/kotlin-compiler.zip -d .tools
