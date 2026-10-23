#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
: "${ANDROID_SDK_ROOT:?Android SDK erforderlich}"
: "${JAVA_HOME:?JDK 17 erforderlich}"
bt="$ANDROID_SDK_ROOT/build-tools/35.0.0"
platform="$ANDROID_SDK_ROOT/platforms/android-35/android.jar"
mkdir -p build/generated build/classes build/dex
"$bt/aapt" package -f -m --custom-package de.taxil150.app -J build/generated -M app/src/main/AndroidManifest.xml -S app/src/main/res -A app/src/main/assets -I "$platform" -F build/unsigned.apk
"$JAVA_HOME/bin/javac" -source 8 -target 8 -cp "$platform" -d build/classes build/generated/de/taxil150/app/R.java app/src/main/java/de/taxil150/app/*.java
"$JAVA_HOME/bin/jar" cf build/classes.jar -C build/classes .
"$bt/d8" --lib "$platform" --min-api 26 --output build/dex build/classes.jar
(cd build/dex && zip -q ../unsigned.apk classes.dex)
"$bt/zipalign" -f 4 build/unsigned.apk build/aligned.apk
if [ -n "${TAXI_SIGNING_KEYSTORE:-}" ]; then
 : "${TAXI_SIGNING_PASSWORD:?Passwort des Signierschlüssels erforderlich}"
 "$bt/apksigner" sign --ks "$TAXI_SIGNING_KEYSTORE" --ks-pass env:TAXI_SIGNING_PASSWORD --out build/TaxiL150_1.6.8_BER_Zertifikat.apk build/aligned.apk
 "$bt/apksigner" verify --verbose build/TaxiL150_1.6.8_BER_Zertifikat.apk
else
 echo "Unsignierte APK: build/aligned.apk. Für eine installierbare Update-APK den vorhandenen Signierschlüssel angeben."
fi
"$JAVA_HOME/bin/javac" -d build/test-classes app/src/main/java/de/taxil150/app/Parser.java app/src/main/java/de/taxil150/app/Config.java tests/ParserTest.java app/src/main/java/de/taxil150/app/FlightRefreshGate.java tests/FlightRefreshGateTest.java app/src/main/java/de/taxil150/app/CountdownState.java tests/CountdownStateTest.java
"$JAVA_HOME/bin/java" -cp build/test-classes ParserTest
"$JAVA_HOME/bin/java" -cp build/test-classes FlightRefreshGateTest
"$JAVA_HOME/bin/java" -cp build/test-classes CountdownStateTest

if [ -n "${JSON_TEST_JAR:-}" ]; then
 "$JAVA_HOME/bin/javac" -cp "$JSON_TEST_JAR" -d build/flight-tests app/src/main/java/de/taxil150/app/FlightParser.java tests/FlightParserTest.java app/src/main/java/de/taxil150/app/WeatherParser.java tests/WeatherParserTest.java app/src/main/java/de/taxil150/app/PrayerParser.java tests/PrayerParserTest.java
 "$JAVA_HOME/bin/java" -cp "$JSON_TEST_JAR:build/flight-tests" FlightParserTest
 "$JAVA_HOME/bin/java" -cp "$JSON_TEST_JAR:build/flight-tests" WeatherParserTest
 "$JAVA_HOME/bin/java" -cp "$JSON_TEST_JAR:build/flight-tests" PrayerParserTest
fi
