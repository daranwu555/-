#!/usr/bin/env bash
# 不需 Gradle 的建置腳本。需求：JDK 17+、Android build-tools 34、platform android-34。
# 用法：BUILD_TOOLS=/path/to/build-tools/34 ANDROID_JAR=/path/to/android-34/android.jar ./build.sh
set -euo pipefail
cd "$(dirname "$0")"
BT="${BUILD_TOOLS:-/opt/android-sdk/bt36/android-16}"
JAR="${ANDROID_JAR:-/opt/android-sdk/android-34/android.jar}"
rm -rf build && mkdir -p build/gen build/classes build/dex
"$BT/aapt2" compile --dir res -o build/res.zip
"$BT/aapt2" link -o build/base.apk -I "$JAR" --manifest AndroidManifest.xml --java build/gen build/res.zip
javac -source 11 -target 11 -Xlint:-options -cp "$JAR" -d build/classes build/gen/com/soxl/calc/R.java src/com/soxl/calc/*.java
"$BT/d8" --release --min-api 24 --lib "$JAR" --output build/dex $(find build/classes -name '*.class')
(cd build/dex && zip -q ../base.apk classes.dex)
"$BT/zipalign" -f 4 build/base.apk build/aligned.apk
[ -f keystore.jks ] || keytool -genkeypair -keystore keystore.jks -storepass android -keypass android -alias key -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=SOXL Calc"
"$BT/apksigner" sign --ks keystore.jks --ks-pass pass:android --key-pass pass:android --out soxl-calc.apk build/aligned.apk
"$BT/apksigner" verify --verbose soxl-calc.apk | head -4
ls -l soxl-calc.apk
