# Änderungen in Version 1.7.0 (Versionscode 29) – 14.11.2026

Grundlage: Design-Entwurf „Taxi L150 – Neues Design mit Prognose“ (Bilder 1, 2, 4, 5), mit Feridun abgestimmt.

## Startseite
- Kopf: Taxi Monitor · Flughafen BER · Wetter; rechts Menü (drei Striche) statt Zahnrad.
- Taxinummer-Karte: L150 ✎ | Überwachung aktiv/aus + Schalter (wie 1.6.9).
- Große Karte „DEIN WARTEPLATZ“: Live-Anzeige in der Ecke (grün = live, orange = gestört, grau = aus), gelber Ring mit Platz („12 von 26 wartend“, füllt sich mit dem Vorrücken), daneben Brandenburg (L), Berlin (M), Wartepunkte, Erstes Taxi.
- Feld „Voraussichtlich raus“: Uhrzeit, „in ~N Min“, Balken jetzt/frühestens/spätestens, „Du wartest seit“ und „Wartezeit gesamt“. Nicht eingereiht: „Wenn du dich jetzt einreihst“.
- Karte „Aufrufe Nord / Süd“ (letzte 60 Min) mit Skala und „Etwa alle X Min wird ein Taxi aufgerufen“.
- Gebetszeiten komplett mit Countdown und Hinweis, ob die voraussichtliche Abfahrt vor oder nach dem nächsten Gebet liegt.
- Ankünfte und 45-Min.-Timer unverändert.
- Unten „Reihenfolge deines letzten Aufrufs“: 3 Taxis vor dir, du, 3 nach dir – mit Zielbereich (1N gelb / 1S dunkel) und Uhrzeit. Bleibt bis zum nächsten eigenen Aufruf gespeichert.
- Entfallen: Feld „Zielbereich – noch kein Aufruf“ (der Aufruf-Bildschirm zeigt den Zielbereich), Knopf „Aufruf testen“ auf der Startseite (jetzt im Menü).

## Neue Seiten
- Prognose (Antippen der großen Karte oder Menü): Platzverlauf mit Prognoselinie und Spanne, Nord oder Süd je 15 Min, Ankünfte der nächsten 2 Std, eigene Wartezeiten (heute, 7 Tage, schnellste Uhrzeit), Schalter „Hinweis 10 Min vorher“.
- Menü: Messen & Kongresse, Prognose, Aufruf-Verlauf, Fahrpreisrechner, Aufruf testen, Ton & Vibration, Einstellungen.
- Messen & Kongresse: lädt einmal täglich (oder per Knopf) den öffentlichen Veranstaltungskalender der Messe Berlin, nur Termine in Berlin, nächste Messe mit Abreisetag, Filter „Demnächst“/„Nur große“. Eingebaute Startliste, falls noch nichts geladen werden konnte.

## Technik
- Parser liefert zusätzlich alle Zeilen der Warteliste und der Aufrufliste (Zeit = Sekunden seit Aufruf, Zielbereich).
- Neue lokale Datenbank queue_log.db: gesehene Aufrufe (je QNr einmal, 8 Tage), eigener Platzverlauf (12 Std), eigene Wartezeiten (60 Tage). CallHistory unverändert.
- Forecast.java (ohne Android, getestet): Rate aus eigenem Platzverlauf (Vorrang) bzw. beobachteten Aufrufen; mindestens 10 Min Beobachtung; Spanne mindestens −25 %/+40 %, weil Aufrufe gebündelt kommen und Taxis mit mehr Punkten davor einsortiert werden (beides in echten APCOA-Daten vom 14.11.2026 beobachtet).
- Hinweis 10 Min vorher: eigene Benachrichtigungskategorie, einmal pro Warteschlangen-Durchgang.
- Die neue Auswertung läuft im Dienst abgesichert neben der Aufruf-Erkennung; ein Fehler dort beeinflusst den Aufruf-Alarm nicht.

## Prüfbericht
- Java: ParserTest, ParserCallsTest (echte APCOA-Seite), ForecastTest, MesseParserTest (echter Kalender-Auszug), FlightRefreshGateTest, CountdownStateTest – bestanden; CallHistorySqlTest bestanden.
- Prognose mit 60 echten APCOA-Abrufen (10 Min, 14.11.2026) durchgespielt.
- Oberfläche (Chromium, Brücke simuliert): 8 Tests inkl. neuem OverviewV17UiTest bestanden, 320–768 px ohne seitliches Scrollen oder Abschneiden.
- Android-Build mit SDK 35 auf GitHub erfolgreich, signiert mit dem bestehenden Schlüssel.
- Kein Gerätetest. Die Prognose braucht nach dem Einschalten der Überwachung etwa 10 Minuten; „Deine Wartezeiten“ füllt sich erst über mehrere Tage.
