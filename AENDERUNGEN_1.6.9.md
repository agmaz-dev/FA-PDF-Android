# Änderungen in Version 1.6.9 (Versionscode 28) – 23.10.2026

- **Überwachung per Schalter oben:** In der Taxinummer-Karte steht rechts „Überwachung aus/aktiv“ mit einem Schalter (aus = grau, aktiv = gelb). Antippen startet bzw. pausiert die Überwachung (gleiche native Funktionen startMonitor/stopMonitor wie vorher).
- **Unterer Button „Überwachung starten/pausieren“ entfernt.** „Aufruf testen“ bleibt.
- Karte kompakter wie im Referenzbild: Beschriftung „Deine Taxinummer“ nur noch für Screenreader, Nummer größer.
- Grüner Punkt entfällt; der orange Punkt erscheint weiterhin bei Verbindungsfehlern.
- Der Schalter nimmt keinen Eingabefokus, damit das Antippen nicht den Bearbeitungsmodus der Taxinummer auslöst.

Prüfung: alle 9 UI-Tests (Chromium, Brücke simuliert) inkl. Schalter per Klick und Touch, kein Abschneiden bei 320–768 px; Java-Tests im GitHub-Build. Kein Gerätetest.
