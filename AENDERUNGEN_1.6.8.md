# Änderungen in Version 1.6.8 (Versionscode 27) – 23.10.2026

## Gewünschte Änderungen
1. **Plätze getauscht:**
   - Im Feld „DEIN WARTEPLATZ“ steht unten jetzt **„Wartepunkte“** (eigene Punkte, gelb, z. B. „38 Punkte“).
   - **„Erstes wartendes Taxi“** steht jetzt in der ersten kleinen Kachel (nur Zahl, z. B. „101“).
   - Die Daten bleiben getrennt: Wartepunkte = eigene Punkte, Erstes wartendes Taxi = Punkte aus der ersten Zeile der Warteliste. Null bleibt „0“, fehlende Daten bleiben „–“.
2. **Farbschema und Aufbau nach Referenzbild:**
   - Navy-Blau ersetzt durch neutrales Dunkelgrau/Schwarz: Hintergrund #0A0D12, Karten #1A1D24→#14171C, Rahmen #2C3036, Grautext #B3B5BD, Gelb #FDCD2F. Gilt für alle Ansichten (Übersicht, Ankünfte, Fahrpreis, Einstellungen, Verlauf) sowie System-/Navigationsleiste (colors.xml).
   - Die vier Kacheln (Erstes wartendes Taxi, Wartende Taxis, Brandenburg (L), Berlin (M)) stehen wie im Bild **in einer Reihe**. Regionen: gelbes L/M-Badge statt Autosymbol.
   - Abweichung vom Bild: Bei „Brandenburg (L)“ steht das Badge über dem Text und „(L)“ bricht in die zweite Zeile. Im Bild ist dieser Text nur ca. 7 px groß – auf dem Telefon im Auto nicht lesbar.

## Geänderte Dateien
- app/src/main/assets/index.html (HTML, CSS, Rendering der beiden Werte)
- app/src/main/res/values/colors.xml
- app/src/main/AndroidManifest.xml (1.6.8 / 27)
- build-manual.sh (APK-Name 1.6.8)
- tests/FirstTaxiPointsRenderTest.cjs, tests/FirstTaxiPointsUiTest.cjs (neue Anordnung, 4 Kacheln in einer Reihe, nichts abgeschnitten 320–768 px)
- START_HIER.md, Dokumentation/02_APK_ERSTELLEN.md (Versionsangaben)

## Prüfbericht (ehrlich)
- Bestanden in Chromium (Android-Brücke simuliert): alle 9 UI-/JS-Tests (UiScript, FirstTaxiPoints Render+UI, Arrival, Countdown, Fare, FareCalculator, FlightRefresh, PrayerHistory).
- Bestanden: Java-Tests ParserTest, FlightRefreshGateTest, CountdownStateTest; Python CallHistorySqlTest.
- **Keine APK gebaut:** In der Claude-Umgebung war das Android SDK (Build Tools 35.0.0, android-35) nicht herunterladbar. Java-Code wurde nicht geändert.
- **Kein Gerätetest.**
- Der private Signierschlüssel war nicht vorhanden (richtig so). Die Update-APK muss lokal mit `build-manual.sh` und dem vorhandenen Schlüssel gebaut werden – siehe Dokumentation/02_APK_ERSTELLEN.md. Ergebnis: build/TaxiL150_1.6.8_BER_Zertifikat.apk
