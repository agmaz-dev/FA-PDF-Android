# 4. Wartung, Grenzen und Prüfung

## Daten und Aktualisierung

Die Taxi-Daten kommen direkt von der APCOA-Anzeige, die in Config.java festgelegt ist. Der Dienst ruft sie im Normalbetrieb etwa alle fünf Sekunden ab. Fehler führen zu längeren Pausen; parallele aggressive Wiederholungen sollen vermieden werden.

BER-Daten kommen aus der vom offiziellen Webauftritt verwendeten JSON-Abfrage. Die Ankunftsseite aktualisiert bei geöffneter Ansicht ungefähr minütlich. Manuelle Abrufe und Fehler unterliegen zusätzlichen Mindestabständen. Eine erfolgreiche Abfrage ohne Daten ist von einem fehlgeschlagenen Abruf zu unterscheiden.

Wetter stammt aus Bright Sky. Gebetszeiten verwenden die Diyanet-Berlin-Seite: PrayerRepository lädt einen lokal gespeicherten oder mitgelieferten Stand und versucht während aktiver App neue Daten nachzuladen, wenn der gespeicherte Abruf mindestens sechs Stunden zurückliegt. Fehler werden angezeigt; die gebündelten Tage sind zeitlich begrenzt. Deshalb darf man die Beispieltabelle nicht als unbegrenzt gültigen Kalender behandeln.

Öffentliche Webseiten können ihre Struktur ändern. Wenn die Quelle geändert wird, Repository und Parser gemeinsam anpassen und mit echten gespeicherten Antworten prüfen. Daten nicht bloß anhand des Bildes erraten.

## Zertifikatskorrektur

Für tms-info.apcoa.de wird zusätzlich die öffentliche Sectigo Public Server Authentication Root R46 vertraut; für ber.berlin-airport.de die öffentliche GlobalSign Root R46. Das normale Systemvertrauen bleibt aktiv. Die Ergänzungen gelten nur für die genau bezeichneten Hosts.

Bei erneutem Zertifikatsfehler: Gerätezeit, konkrete Zieladresse, Server-Zertifikatskette und Netzwerksituation prüfen. Nicht mit Trust-all, deaktiviertem HostnameVerifier oder HTTP umgehen. Keine Zertifikate eines lokalen Entwicklungs-Proxys übernehmen. Die öffentlichen Serverzertifikate unter tests/tls-fixtures dienen historischen Tests und sind nicht die zusätzlich vertrauten Produktionsanker.

## Speicherung und Hintergrundbetrieb

Einstellungen und Zustände liegen in SharedPreferences, echte Aufrufe im lokalen SQLite-Verlauf. Ein Update mit gleichem Paket und Schlüssel bewahrt diese Daten normalerweise. Die App erlaubt kein allgemeines Android-Backup laut Manifest. Eine Deinstallation kann die lokalen Daten löschen.

Der Taxi-Monitor ist ein Android-Vordergrunddienst, kein zentraler Push-Server. Android-Energiesparen, Dienstzeitlimits oder Prozessbeendigung können ihn beeinflussen. Keine pauschale Zusicherung für unbegrenzten Hintergrundbetrieb geben. Der 45-Minuten-Timer ist im vorhandenen Stand still. Die Display-an-Funktion gilt nur, solange die Activity sichtbar ist; ein manuelles Sperren per Taste bleibt möglich.

## Tests im Archiv

Das Buildskript enthält Java-Tests für Taxi-Parser, Flug-Aktualisierungssperre und Countdown. Mit optionaler JSON_TEST_JAR können weitere JSON-Parserprüfungen laufen. Die APK selbst nutzt Androids eingebautes org.json.

Zusätzliche Prüfungen im Projektordner:

```bash
node tests/FirstTaxiPointsRenderTest.cjs
node tests/FareCalculatorTest.cjs
python3 tests/CallHistorySqlTest.py
python3 tests/CertificateCompatibilityTest.py
```

Vor Ausführung die Voraussetzungen der jeweiligen Testdatei prüfen. Die TLS-Prüfung validiert gespeicherte öffentliche Ketten mit definierten Testzeitpunkten; sie ist keine aktuelle Live-Verfügbarkeitsprüfung der Anbieter.

Die Dateien mit UiTest bzw. UiScriptTest im Namen benötigen Playwright und einen nutzbaren Chromium-Browser. Falls sie wegen fehlender Browserinstallation oder Systembeschränkungen nicht starten, ist das kein bestandener UI-Test. Es wurde bei der letzten Herstellung keine neue grafische Browserprüfung ausgeführt, weil die Umgebung den Browserstart verhinderte.

## Praktische Freigabeprüfung auf einem Android-Handy

1. Update über bestehende Installation installieren; Nummer und Einstellungen prüfen.
2. Taxi-Daten laden: eigener Rang/Punkte und L-/M-Zahlen plausibel, leere Liste korrekt behandelt.
3. Erste-Taxi-Punkte unabhängig von den eigenen Punkten kontrollieren.
4. BER öffnen: mehrere Flüge aus allen Terminals, Suche/Zeitauswahl, Status und manuelle Aktualisierung prüfen.
5. Verbindung kurz unterbrechen: Warnung statt erfundener frischer Daten, spätere Erholung prüfen.
6. Aufruftest starten und beenden: Simulation erkennbar, Ton/Vibration nach Berechtigung prüfen, echter Verlauf unverändert.
7. Echte Aufrufe im normalen Betrieb und Speicherung im Verlauf prüfen.
8. Timer starten/pausieren/zurücksetzen, Wetter und Gebetscountdown ansehen.
9. Display über normale Abschaltfrist offen lassen; danach App verlassen und normale Abschaltung prüfen.
10. Telefon sperren und Hintergrundbetrieb unter den konkreten Systemeinstellungen prüfen.

## Bekannte Wartungspunkte

Tarifregeln sind ein eingebauter Datenstand, keine automatisch amtlich aktualisierte Preisliste. Quellen und bisherigen Prüfstand nennt README.txt. Vor Nutzung als aktuelle Auskunft erneut prüfen. Benachrichtigungskanal-Einstellungen können von Android gespeichert werden; ein Codewechsel setzt persönliche Systemeinstellungen nicht automatisch zurück.

Die Entwicklungsumgebung, SDK und private Signierung sind nicht Teil dieses Quellcodearchivs. Es soll klein, übertragbar und für eine sichere Weitergabe geeignet bleiben. Zur vollständigen privaten Wiederherstellung zusätzlich Signierbackup und eine funktionierende APK separat aufbewahren.
