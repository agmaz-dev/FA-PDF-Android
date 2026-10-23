# 2. Eine APK erneut erstellen

## Voraussetzungen

Der mitgelieferte und zuvor verwendete Weg ist ein manueller Bash-Build unter Linux. Auf einem Mac oder unter Windows/WSL braucht man eine entsprechend passende lokale Werkzeuginstallation; diese Betriebssystemvarianten wurden mit diesem Übergabepaket nicht erneut getestet.

Benötigt werden:

- JDK 17 **mit javac und jar**, eine reine Java-Laufzeit reicht nicht.
- Android SDK Platform 35: `platforms/android-35/android.jar`.
- Android SDK Build Tools **35.0.0** mit aapt, d8, zipalign, apksigner.
- Bash und das Kommando `zip`.
- Für ein installierbares Update: bisheriger PKCS12-Signierschlüssel mit Passwort.
- Optional Node.js und Python 3 für zusätzliche Tests.

SDK-Werkzeuge über die offiziellen Android-Entwicklungswerkzeuge installieren. Die Betriebssystem-Variante muss zum Rechner passen. Pfade unten sind Beispiele und durch eigene absolute Pfade zu ersetzen.

Dieses Archiv hat absichtlich keine Gradle-Konfiguration. „Projekt öffnen und Gradle synchronisieren“ ist daher keine vollständige Bauanleitung. Der dokumentierte Einstieg ist das Shell-Skript.

## Vorbereitung

ZIP entpacken und im Terminal in den Ordner `TaxiL150` wechseln. Dann:

```bash
export JAVA_HOME='/absoluter/pfad/zum/jdk-17'
export ANDROID_SDK_ROOT='/absoluter/pfad/zum/android-sdk'
"$JAVA_HOME/bin/java" -version
"$JAVA_HOME/bin/javac" -version
ls "$ANDROID_SDK_ROOT/platforms/android-35/android.jar"
ls "$ANDROID_SDK_ROOT/build-tools/35.0.0/apksigner"
```

Die Umgebung muss die Android-Werkzeuge tatsächlich ausführen dürfen. Bei einem SDK, das man aus einem ZIP ausgepackt hat, können unter Linux die Ausführungsrechte fehlen.

## Private Signierung – erforderlich für Updates

Das separat gespeicherte Backup `TaxiMonitor_Test_Signierung_Privat.zip` enthält `taxi-test-164.p12`, `password.txt` und einen Hinweis. Nur privat auf dem eigenen Rechner entpacken. Das Passwort nicht in Quellcode, Chatnachrichten, öffentliche Projekte oder Screenshots kopieren.

```bash
export TAXI_SIGNING_KEYSTORE='/privater/absoluter/pfad/taxi-test-164.p12'
read -r -s -p 'Signierpasswort: ' TAXI_SIGNING_PASSWORD
printf '\n'
export TAXI_SIGNING_PASSWORD
bash build-manual.sh
unset TAXI_SIGNING_PASSWORD
```

`read` fragt das Passwort ohne Anzeige ab. Das Skript übergibt es über eine Umgebungsvariable an apksigner. Der Schlüsselalias im vorhandenen Backup ist `taxi-test`; der Store enthält die zu dieser Update-Linie gehörende Signieridentität.

## Ergebnis

Bei erfolgreichem signiertem Build:

`build/TaxiL150_1.6.8_BER_Zertifikat.apk`

Ohne Signierangaben entsteht `build/aligned.apk`, eine **unsignierte** APK. Diese ist noch nicht als normale installierbare Auslieferungsdatei zu behandeln. Eine beliebige neue Signatur ersetzt die bestehende Update-Signatur nicht.

Das Skript baut die App und führt anschließend einige Java-Tests aus. Wird ein Testfehler gemeldet, den Build nicht als vollständig erfolgreich freigeben, selbst wenn schon eine APK im Buildordner liegt.

## Sauberer Neubau

Vor einem wiederholten Build kann der generierte Ordner `build` entfernt werden; er enthält keine Quelltexte. Zuerst sicherstellen, dass man im richtigen Projektordner ist:

```bash
pwd
rm -rf ./build
bash build-manual.sh
```

## Signatur und Metadaten prüfen

```bash
"$ANDROID_SDK_ROOT/build-tools/35.0.0/apksigner" verify --verbose --print-certs build/TaxiL150_1.6.8_BER_Zertifikat.apk
"$ANDROID_SDK_ROOT/build-tools/35.0.0/aapt" dump badging build/TaxiL150_1.6.8_BER_Zertifikat.apk
```

Erwarteter Ausgangsstand:

- Paket: `de.taxil150.app.test164`.
- Name: `Taxi L150`.
- versionName: `1.6.8`, versionCode: `27`.
- minSdkVersion: `26` (Android 8), targetSdkVersion: `35`.

Den Zertifikatsfingerabdruck mit einer bereits installierten/ausgelieferten APK dieser Update-Linie vergleichen. Paket und Signieridentität müssen für ein normales Update übereinstimmen.

## Spätere Version, z. B. 1.6.8

1. Änderungen an einer Kopie vornehmen.
2. Im Manifest versionName auf `1.6.8` und versionCode auf mindestens `27` erhöhen.
3. Den Ausgabedateinamen in `build-manual.sh` anpassen.
4. Paketname und Java-Namensraum unverändert lassen.
5. Mit demselben privaten Schlüssel bauen, Tests ausführen und auf einem Gerät installieren.
6. Quellcode, APK, Änderungsnotiz und Prüfergebnisse gemeinsam versioniert sichern.

Wichtig: Java liegt im Namensraum `de.taxil150.app`, während das installierte Paket `de.taxil150.app.test164` heißt. Das ist absichtlich so. Das Buildskript verwendet deshalb `--custom-package de.taxil150.app`; Manifest-Komponentennamen sind vollständig qualifiziert.

## Installation auf dem Telefon

APK privat auf das Telefon übertragen und über die vorhandene App aus der Linie 1.6.4–1.6.7 installieren. Nicht vorher deinstallieren: Einstellungen und Verlauf sollen erhalten bleiben. Die ursprüngliche App bis 1.6.3 mit Paket `de.taxil150.app` gehört zu einer anderen Signier-/Paketlinie.

Ein erfolgreicher Build ersetzt keinen Gerätetest. Mindestens Datenabruf, Ankunftsliste, Aufruftest, Ton/Vibration, Hintergrundbetrieb und Display-an-Verhalten prüfen.
