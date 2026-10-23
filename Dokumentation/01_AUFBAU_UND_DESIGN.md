# 1. Herstellung, Aufbau und Design

## Architektur

Die Android-Activity lädt `app/src/main/assets/index.html` in eine WebView. Diese eine Datei enthält das endgültige Layout, CSS, Inline-SVG-Symbole und JavaScript der Ansichten. Die Daten werden durch Java abgerufen und ausgewertet; Java übergibt den Zustand an `window.renderNative(...)`. Bedienaktionen gehen über die JavaScript-Schnittstelle `TaxiAndroid` zurück an Java.

Die Oberfläche ist vollständig lokal. Ein Server für das Hosting der App-Oberfläche ist nicht erforderlich. Taxi-, Flug-, Wetter- und Gebetsdaten benötigen ihre jeweiligen Internetquellen. Einstellungen, Aufrufverlauf und Timerzustand liegen auf dem jeweiligen Telefon.

Die WebView lädt keine beliebigen fremden Webseiten als App-Oberfläche. Ihre Inhaltsrichtlinie sperrt direkte JavaScript-Netzwerkabrufe. Deshalb ist das bloße Öffnen von `index.html` im Browser kein Ersatz für die APK: Die native Schnittstelle und die nativen Datenabrufe fehlen dort.

## Dateien und Zuständigkeiten

Alle Java-Dateien liegen unter `app/src/main/java/de/taxil150/app/`.

| Datei | Aufgabe |
|---|---|
| MainActivity.java | WebView, JavaScript-Brücke, Darstellung des nativen Zustands, Navigation und Display-an-Verhalten |
| MonitorService.java | Vordergrunddienst, Taxi-Abrufe, Fehlerpausen, Erkennen eines Aufrufs |
| Parser.java | APCOA-Tabellen, eigene Position/Punkte, Anzahl L/M, Punkte des ersten wartenden Taxis |
| Config.java | Grundkonfiguration und Taxi-Datenquelle |
| Alerts.java | Android-Benachrichtigungen, Ton und Vibration |
| CallHistory.java | Lokaler SQLite-Aufrufverlauf |
| FlightRepository.java | BER-Datenabruf und Speicherung des Flugzustands |
| FlightParser.java | Umwandlung der BER-Antwort in das App-Datenmodell |
| FlightRefreshGate.java | Mindestabstand zwischen Flugabrufen und Fehlerpausen |
| WeatherRepository.java / WeatherParser.java | Wetterabruf und Auswertung für Schönefeld |
| PrayerRepository.java / PrayerParser.java | Berliner Gebetszeiten, Aktualisierung und Tageszustand |
| CountdownState.java / CountdownStore.java | 45-Minuten-Timer, Berechnung und Speicherung |
| WindowAppearance.java | Android-Systemleisten, Farben und Bildschirmränder |

Weitere wichtige Dateien:

- `app/src/main/AndroidManifest.xml`: App-Name, Paket, Version, Berechtigungen, Activity und Dienst.
- `app/src/main/assets/index.html`: alle App-Seiten und ihre Gestaltung; auch Tarifrechner-Logik.
- `app/src/main/assets/prayer_berlin.json`: mitgelieferter Gebetszeiten-Datenstand als Start-/Offlinebasis.
- `app/src/main/res/raw/zurna.mp3` und `gong.wav`: Tonressourcen.
- `app/src/main/res/xml/network_security_config.xml`: auf konkrete Domains begrenzte Zertifikatsergänzungen.
- `app/src/main/res/raw/*root*.pem`: öffentliche Stammzertifikate; keine privaten Schlüssel.
- `app/src/main/res/mipmap-*` und `drawable/taxi_launcher_foreground.png`: Launcher-Symbol.
- `design/tesla_taxi_LN-IB-26E.png`: vorhandene zusätzliche Design-Ressource. Das maßgebliche aktuelle Layout ist der Code in `index.html`, nicht dieses einzelne Bild.
- `tests/`: Logik-, Parser-, TLS- und Oberflächenprüfungen.

## Gestaltung

Die Design-Grundfarben stehen am Anfang von `index.html`:

| CSS-Variable | Farbe | Verwendung |
|---|---|---|
| --bg | #0c1523 | Dunkelblauer Hintergrund |
| --card | #172438 | Karten-Grundfarbe |
| --line | #2b405d | Linien und Kartenränder |
| --muted | #afbed3 | Beschriftungen und Zusatzinfos |
| --yellow | #ffdc29 | Hervorhebungen und Hauptaktionen |

Karten verwenden zusätzlich einen dunkelblauen Verlauf. Schriften stammen aus den Systemschriften; die Oberfläche passt sich mit Flexbox, CSS Grid, `clamp()` und Media Queries an verschiedene Breiten an. Viele Regeln werden weiter unten in derselben Datei ergänzt oder überschrieben. Vor CSS-Änderungen deshalb die vollständige Datei berücksichtigen.

### Hauptseite

- Titel mit Taxi-Symbol, Einstellungszahnrad und Wetter Schönefeld.
- Eigene Taxinummer mit Bearbeitung und Überwachungsstatus.
- Große Warteplatz-Anzeige mit eigener Position.
- Darunter Punkte des **ersten wartenden Taxis**. Es ist keine Differenz zu den eigenen Punkten.
- Eigene Wartepunkte und Gesamtzahl wartender Taxis.
- Zwei kompakte Karten: **Brandenburg (L)** und **Berlin (M)**.
- Zielbereich unter diesen Karten und vor den Gebetszeiten.
- Berliner Gebetszeiten mit größeren Namen/Uhrzeiten und Countdown zum nächsten Gebet; ohne Gebetston.
- Zwei nebeneinanderliegende Karten: BER-Ankünfte und 45-Minuten-Timer.
- Überwachungssteuerung und Aufruftest.
- Einstellungen über das Zahnrad; keine untere Navigationsleiste.

### Aufruf, Ankünfte und Einstellungen

Ein echter Aufruf erhält eine große gelbe Darstellung mit eigener Nummer und Zielbereich. Ein Test wird als Simulation gekennzeichnet.

Die Ankunftsseite zeigt immer alle Terminals, kompakte Flugkarten, geplante und erwartete Zeiten, Terminal, Ankunftsbereich, Gepäckband und Status, soweit die Quelle diese Werte liefert. Suche und Tages-/Stundenauswahl bleiben vorhanden. Es gibt keinen T1/Alle-Terminals-Umschalter mehr.

In den Einstellungen liegen Ton/Vibration, Aufrufhistorie, Tarifrechner und App-Informationen/Kontakt. Der Tarifrechner ist eine Schätzung anhand der eingebauten Tariflogik; er ersetzt kein geeichtes Taxameter.

## Herstellung in Arbeitsschritten

1. Die lokale HTML-Oberfläche wurde mit dem freigegebenen dunkelblau-gelben Design aufgebaut.
2. Native Java-Klassen wurden für Datenabruf, Parser, Benachrichtigung und Speicherung ergänzt.
3. Oberfläche und Java wurden über `TaxiAndroid` und `renderNative` verbunden.
4. Flug-, Wetter-, Gebets-, Timer-, Verlauf- und Tarifmodule wurden integriert.
5. Ab 1.6.4 wurden die Ersttaxi-Punkte ergänzt; ab 1.6.5 bleibt das Display bei sichtbarer Activity an.
6. 1.6.6 ergänzt die APCOA-Stammzertifikatskompatibilität und stellt den Namen Taxi L150 wieder her; 1.6.7 ergänzt die BER-Stammzertifikatskompatibilität.
7. Android-Ressourcen werden mit aapt paketiert, Java mit javac übersetzt, mit d8 in DEX umgewandelt und anschließend in die APK eingebaut.
8. zipalign richtet die APK aus; apksigner signiert und prüft sie. Danach folgen die fachlichen Tests und der Gerätetest.
