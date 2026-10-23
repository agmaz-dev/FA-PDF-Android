# FA Taximeter – GPS-Fahrpreisrechner (nicht geeicht)

Native Android-App (Kotlin, Jetpack Compose) für eine **Fahrpreis-Schätzung** auf Basis tatsächlich
gemessener GPS-Positionen, für Berlin (Anzeige-Tarif **4**) und Fahrten ab Flughafen BER
(Anzeige-Tarif **3**).

> **Wichtig:** FA Taximeter ist **kein geeichter, abrechnungsfähiger Taxameter**. Die App ist durchgehend als
> „GPS-Fahrpreisrechner – nicht geeicht“ gekennzeichnet. Alle Beträge sind Schätzungen.

- Offline: kein Internet, kein Konto, kein Server, keine kostenpflichtigen Dienste, keine Google-Play-Dienste.
- Standortdaten bleiben ausschließlich im privaten App-Speicher des Geräts.
- Hoch- und Querformat, Größenänderung beim Auf-/Zuklappen (Galaxy Z Fold), normale Smartphones (ab Android 8.0).

---

## 1. Installation

### A) APK über GitHub Actions bauen (ohne eigenen Rechner-Build)

1. Neues GitHub-Repository anlegen (z. B. `FA-Taximeter`) und den kompletten Projektordner hochladen
   (inklusive `.github/`, `gradle/`, `gradlew`).
2. Im Repository: **Actions → „Debug-APK bauen“ → „Run workflow“**.
3. Nach ca. 5–10 Minuten im Lauf unten unter **Artifacts** `FA-Taximeter-debug-apk` herunterladen
   (ZIP, darin `app-debug.apk`).
4. APK aufs Handy kopieren und öffnen. Einmalig „Installation aus unbekannten Quellen“ für die Datei-App erlauben.

Der Workflow führt vorher alle Unit-Tests aus (`testDebugUnitTest`); Testberichte liegen als zweites Artefakt bei.

### B) Android Studio

1. Ordner in Android Studio (Ladybug oder neuer) öffnen, Gradle-Sync abwarten (JDK 17).
2. `Build → Build App Bundle(s)/APK(s) → Build APK(s)` oder auf der Kommandozeile:
   ```
   ./gradlew testDebugUnitTest assembleDebug
   ```
3. Ergebnis: `app/build/outputs/apk/debug/app-debug.apk` (Paketname `de.fa.taximeter.debug`, debug-signiert).

---

## 2. Bedienung

| Element | Funktion |
|---|---|
| **TARIF** (oben links, antippbar) | Im Zustand FREI Tarif 3 – BER oder 4 – Berlin wählen. Während der Fahrt gesperrt. |
| **ZUSCHLAG** (oben rechts) | Jedes Antippen: **+0,50 €**. Langes Drücken: Zuschlag auf 0,00 €. Funktioniert in FREI (für die nächste Fahrt), BESETZT und STOPP. |
| **BESETZT** | Startet genau eine Fahrt (nur mit brauchbarem GPS-Fix), Grundpreis einmalig. Mehrfaches Tippen ohne Wirkung. Aus STOPP: dieselbe Fahrt läuft weiter – der Zuschlag wandert wieder nach oben, unten läuft nur der Fahrpreis weiter. |
| **STOPP** | Friert die Berechnung ein. Die große Anzeige zeigt die **Summe aus Fahrpreis und Zuschlägen**, „KASSE“ leuchtet rot, die ZUSCHLAG-Anzeige oben ist leer. |
| **FREI** | Aus STOPP: Fahrt wird gespeichert (Historie unter FAHRTEN), der Zähler ist leer. Während BESETZT ohne Wirkung. |
| **G ••••** (unten rechts) | Empfang: 4 Punkte gut, 3 mäßig, 1 unzuverlässig, 0 kein Empfang bzw. Tunnel-Schätzung. |

Summe = Fahrpreis ohne Zuschläge + Zuschläge (genau einmal). Die gespeicherte Fahrt in der Historie zeigt Fahrpreis,
Zuschlag, Summe, Strecke, Dauer und Messlücken.

Zurück-Taste während einer Fahrt beendet nichts: erster Druck zeigt einen Hinweis, ein zweiter innerhalb von
2,5 s schickt die App in den Hintergrund (die Fahrt läuft über den Foreground-Service weiter).

### Kartenzahlung mit SumUp

1. Einmalig: Auf me.sumup.com unter Einstellungen → Für Entwickler → Toolkit → Affiliate Keys einen Schlüssel für die
   App-ID `de.fa.taximeter.debug` erstellen und in der App unter EINSTELLUNGEN → Kartenzahlung eintragen.
   Der Schlüssel wird nur lokal auf dem Gerät gespeichert, nicht im Code.
2. Die SumUp-App muss auf demselben Gerät installiert und angemeldet sein.
3. Ablauf: STOPP → **KASSE** antippen → SumUp öffnet sich mit der Summe → Zahlung → Rückkehr ins Taximeter.
   Erfolgreich: Fahrt wird als „Karte (SumUp)“ mit Transaktionscode gespeichert, Zähler auf FREI.
   Abgebrochen/abgelehnt: Taximeter bleibt auf STOPP, erneut KASSE oder bar mit FREI.
4. Jeder Zahlungsversuch hat eine eigene ID (foreign-tx-id); fremde oder verspätete Rückmeldungen werden ignoriert.
5. Technik: SumUp „Payment Switch“ (`sumupmerchant://pay/1.0`), Rückruf `fataximeter://sumup-callback`.
   SumUp entwickelt den Payment Switch nicht mehr aktiv weiter, er funktioniert aber.

### Tunnel / kein GPS-Empfang

1. Nach 3 s ohne Messung zählt die App mit der **zuletzt gemessenen Geschwindigkeit** weiter (höchstens 100 km/h,
   höchstens 3 Minuten; unter 7 km/h – Stau – keine Schätzung). Beispiel Britzer Tunnel (A113) bei 80 km/h:
   etwa alle 3 Sekunden +0,20 €.
2. Nach der Ausfahrt wird die Strecke im Tunnel aus der gemessenen Position vor und nach dem Tunnel bestimmt
   (Luftlinie = nachweisbare Mindeststrecke), wenn die Lücke höchstens 10 Minuten dauert und die
   Durchschnittsgeschwindigkeit plausibel ist (≤ 130 km/h).
3. Der angezeigte Preis springt dabei **nie zurück**: Lag die Schätzung höher, bleibt der Zähler stehen,
   bis die echte Berechnung ihn eingeholt hat.
4. Eine Lücke erzeugt nie Wartezeit und wird in der Fahrt vermerkt.

## 3. Tarife und Quellen (geprüft am 22.10.2026)

Rechtsgrundlage: **Verordnung über Beförderungsentgelte im Taxenverkehr (Berlin) vom 06.12.2005**, zuletzt
geändert durch die **Zwölfte Änderungsverordnung vom 10.11.2022** (GVBl. S. 606, in Kraft 20.12.2022).
Die **Dreizehnte Änderungsverordnung** (2024) führt nur Festpreise/Tarifkorridor für vorbestellte Fahrten ein
und ändert Grundpreis, Kilometerpreise, Wartezeit und Zuschläge nicht. Eine neuere Änderung wurde nicht gefunden.

| | Tarif 3 – BER | Tarif 4 – Berlin |
|---|---|---|
| Amtliche Bezeichnung | Tarifstufe 3 – Flughafentarif | Tarifstufe 2 – Tarif für sonstige Fahrten |
| Interne Kennung | `berlin-ts3-flughafen` | `berlin-ts2-normal` |
| Grundpreis | 4,30 € (enthält 0,20 € für die erste Teilstrecke) | 4,30 € (dto.) |
| Kilometerpreis | 0–5 km 2,60 €/km, ab 5 km 2,00 €/km | 0–3 km 2,80 €/km, 3–7 km 2,60 €/km, ab 7 km 2,10 €/km |
| Fortschaltung | jede angefangene Teilstrecke 0,20 € | dto. |
| Wartezeit | **36,00 €/h** | 39,00 €/h |
| Wartezeit-Regel | > 1 Minute je Halt, auch verkehrsbedingt | dto. |

### Abweichung zur Ausgangsrecherche

Die Vorgabe nannte für Tarif 3 eine Wartezeit von 39,00 €/h. **Die Verordnung (§ 5 Abs. 1 in der Fassung der
12. ÄndVO) setzt für Tarifstufe 3 aber 36,00 €/h fest** („30,00 € → 36,00 €“; für Tarifstufe 2 „33,00 € → 39,00 €“).
Die App verwendet deshalb 36,00 €/h. Über eine benutzerdefinierte Tarifkopie lässt sich der Wert bei Bedarf ändern.

### Zuschläge (§ 5 Abs. 2)

| Zuschlag | Betrag | Bedingung |
|---|---|---|
| BER-Zuschlag | 1,50 € | Nur bei Aufnahme am BER durch Taxen, die die kostenpflichtige Taxeninfrastruktur mit Ladeaufruf nutzen. **Nicht** für Fahrten mit Ziel BER; wird nie automatisch gebucht. |
| Großraum | 6,00 € | 5. bis 8. Person (zwei Kinder unter 10 = eine Person) oder Gepäck, das ein Großraumtaxi erfordert. |
| Gutschein/Abrechnung | 1,50 € | Nur bei Zahlung über Gutschein-/Abrechnungssystem der Taxizentralen. Kein Zuschlag für gewöhnliche Kartenzahlung. |
| Benutzerdefiniert | frei | Ausdrücklich als „benutzerdefiniert“ gekennzeichnet. |

Quellen:
- GVBl. Nr. 52/2022, S. 606 (12. ÄndVO): https://pardok.parlament-berlin.de/starweb/adis/citat/VT/19/gvbl/g22520606.pdf
- Konsolidierte Fassung: https://lexaris.de/book/version/documentflat/head/1254175
- GVBl. Nr. 18/2024 (13. ÄndVO): https://www.berlin.de/sen/justiz/service/gesetze-und-verordnungen/2024/ausgabe-nr-18-vom-162024-s-137-144.pdf
- Amtliche Gesetzessammlung: https://gesetze.berlin.de/
- https://www.taxi-berlin.de/taxitarif/ und https://www.berlin.de/tourismus/infos/1756978-1721039-taxi-telefonnummern-preise-regeln.html
  (diese beiden Seiten waren aus der Build-Umgebung nicht abrufbar; geprüft wurde gegen die Verordnungstexte)

Tarifwerte, Quellen, Prüfdatum und Berechnungsmodus stehen getrennt vom UI-Code in
`domain/tariff/TariffCatalog.kt`. Jede Fahrt speichert eine vollständige Kopie ihres Tarifprofils (inkl. Version);
Änderungen wirken nur auf neue Fahrten.

### Ungeklärte Punkte (bewusst nicht erfunden)

1. **Umschaltung Strecke/Zeit:** Die Verordnung regelt nicht, nach welchem Verfahren der Taxameter zwischen
   Strecken- und Zeitberechnung wechselt (das ist Sache der Eichung/Bauartzulassung). Die App nutzt daher einen
   ausdrücklich als **Schätzung** bezeichneten Modus (siehe 4.).
2. **Beginn/Ende eines „Halts“:** „je Stopp“ ist nicht näher definiert. Die App definiert einen Halt als Phase
   unter der Umschaltgeschwindigkeit; er endet nach ≥ 3 s zügiger Fahrt (Hysterese) oder bei einer Messlücke.
3. **Ob die erste Minute nach Überschreiten nachberechnet wird:** Die Verordnung sagt, die Berechnung beginne
   nach der ersten vollendeten Minute. Die App berechnet daher erst ab Sekunde 61 eines Halts.
4. **Zeit-Fortschaltung:** Für die Zeit wird dieselbe 0,20-€-Einheit wie für die Strecke angenommen
   (Tarif 4: 18,46 s, Tarif 3: 20,00 s je 0,20 €).
5. **Kurzstreckenpauschaltarif (Tarifstufe 1)** und **Festpreise/Tarifkorridor** sind nicht implementiert.
6. **Tarifgebiet:** Die App schaltet nie automatisch an der Stadtgrenze um; der Tarif wird in FREI manuell gewählt.

---

## 4. Berechnungslogik

Implementiert in `domain/tariff/FareEngine.kt` (ohne Android-Abhängigkeiten, BigDecimal mit 34 Stellen,
keine Zwischenrundung je GPS-Punkt).

1. Die GPS-Messung liefert Intervalle *(Dauer, Strecke, Geschwindigkeit, gemessen ja/nein)*.
2. **Umschaltgeschwindigkeit** `v_u = Wartezeitpreis/h ÷ aktueller Kilometerpreis`
   (Tarif 4: 13,93 / 15,00 / 18,57 km/h; Tarif 3: 13,85 / 18,00 km/h).
3. Intervall mit `v ≥ v_u` → wird **nur nach Strecke** berechnet.
4. Intervall mit `v < v_u` (auch langsame Fahrt, nicht nur 0 km/h) gehört zu einem **Halt**:
   - die ersten 60 s des Halts sind Karenz → in dieser Zeit wird weiter **nur die Strecke** berechnet,
   - danach **nur die Zeit** (nie beides gleichzeitig → keine Doppelzählung).
5. **Kilometerstaffeln abschnittsweise:** Ein Intervall, das eine Staffelgrenze überquert, wird geteilt;
   frühere Strecke wird nie umbewertet. Die Staffelposition zählt die gesamte gemessene Strecke.
6. **Fortschaltung:** Exakter variabler Betrag `V` (Strecke + Zeit). Angezeigter Fahrpreis
   `= 4,30 € + 0,20 € × (max(1, ⌈V / 0,20 €⌉) − 1)` – der Grundpreis enthält die erste Einheit.
   Beispiel Tarif 4: 3,000 km → 12,50 €; 3,001 km → 12,70 €; 7 km → 22,90 €.
7. **Zuschläge** werden getrennt geführt und erst beim Gesamtbetrag genau einmal addiert.
8. **Messlücken** erzeugen nie Wartezeit und setzen einen Halt zurück.

---

## 5. GPS-Streckenmessung

Android `LocationManager`, Anbieter **GPS**, Intervall 1 s, Mindestabstand 0 (gefiltert wird in der App).
Implementiert in `domain/gps/DistanceTracker.kt`. Zeitdifferenzen ausschließlich über die monotone Zeitbasis
`elapsedRealtimeNanos`.

| Filter | Grenze | Zweck |
|---|---|---|
| Genauigkeit | > 30 m → verworfen | ungenaue Messungen |
| „gut“ | ≤ 10 m | Statusanzeige |
| Alter | > 5 s → verworfen | veraltete Messungen |
| Zeitstempel | ≤ letzter → verworfen | doppelte/rückwärts laufende Messungen |
| Sprung | (Abstand − Genauigkeiten) / Zeit > 70 m/s → verworfen | GPS-Sprünge; nach 5 Sprüngen in Folge Neuverankerung **ohne** Strecke |
| Ankerschwelle | Genauigkeit, begrenzt auf 5…25 m; ohne Doppler-Bewegung (< 1 m/s) doppelte Schwelle | Stillstandsrauschen erzeugt keine Kilometer |
| Messlücke | > 10 s ohne akzeptierte Messung | „GPS verloren“, keine Wartezeit |
| Überbrückung (Tunnel) | Lücken ≤ 10 min, Ø ≤ 130 km/h, beide Fixe ≤ 20 m | Luftlinie als nachweisbare Untergrenze; sonst **keine** Strecke |
| Anzeige „unzuverlässig“ | > 3 s ohne Messung oder Genauigkeit > 30 m | Warnhinweis |

Langsame echte Fahrt wird nicht verschluckt: Die Verschiebung wird vom letzten Ankerpunkt gemessen und bei
Überschreiten der Schwelle vollständig gezählt. Jede Messlücke wird mit Beginn, Dauer, Grund und ggf.
überbrückter Strecke in der Fahrt und in der Abschlussanzeige vermerkt. Es gibt keine nachträgliche Preiskorrektur.

Normale Fahrten starten nur mit brauchbarem Fix (Genauigkeit ≤ 30 m, ≤ 5 s alt, kein simulierter Standort).
Der **Testmodus** (Einstellungen) erzeugt eine synthetische Fahrt und ist dauerhaft als SIMULATION markiert.

---

## 6. Android-Lebenszyklus und Speicherung

- Während BESETZT/STOPP läuft ein **Foreground-Service vom Typ `location`** mit sichtbarer Benachrichtigung.
  Er wird nur aus der sichtbaren App gestartet (Vorgabe ab Android 14). Keine Hintergrund-Standortberechtigung.
- Berechtigungen: genauer + ungefährer Standort, Foreground-Service (Location), Benachrichtigungen (ab Android 13).
  Bei nur ungefährer Freigabe erscheint ein deutlicher Hinweis; eine normale Fahrt startet dann nicht.
- Fahrtzustand wird bei jeder Bedienung und während der Fahrt alle 5 s atomar gesichert
  (`files/taximeter/active_state.json`), Historie in `history.json`.
- **Prozessabbruch:** Beim nächsten Start wird eine laufende Fahrt im Zustand STOPP angeboten; die nicht gemessene
  Zeit wird als Messlücke ohne Strecke und ohne Wartezeit vermerkt. Keine Garantie, dass die Messung nach
  erzwungenem Beenden oder Neustart weiterläuft (`START_NOT_STICKY`).
- Bildschirmdrehung/Auf- und Zuklappen: Zustand liegt prozessweit im `MeterRepository`, nicht in der Activity.
- Bildschirm bleibt während aktiver Fahrt an (abschaltbar in den Einstellungen).

---

## 7. Tests

Unit-Tests in `app/src/test/java/de/fa/taximeter/domain/`:

- `FareEngineTest` – Grundpreis genau einmal; Staffelgrenzen 3/5/7 km knapp darunter/darauf/darüber;
  abschnittsweise Staffeln; keine Zwischenrundung; Umschaltgeschwindigkeit; Karenz; gemischte Strecke/Zeit ohne
  Doppelzählung; langsame Fahrt; Halt-Rücksetzung und Hysterese; Messlücke ohne Wartezeit; Zeit-Schaltstufen.
- `DistanceTrackerTest` – Stillstandsrauschen (mit/ohne Doppler); normale und langsame Fahrt; ungenaue/veraltete/
  doppelte Messungen; Sprünge; Neuverankerung; GPS-Ausfall und Wiederempfang (lang ohne, kurz mit geprüfter
  Überbrückung); Abschluss bei STOPP; Qualitätsanzeige; Startbedingung.
- `MeterMachineTest` – BESETZT genau einmal; kein Start ohne Fix; Tarifsperre; STOPP/Fortsetzen ohne
  Nachberechnung; KASSE nur nach STOPP; Gesamtbetrag/Zuschläge genau einmal; Neue Fahrt; Serialisierung
  (Bildschirmdrehung/Prozessende); Wiederherstellung nach Prozessabbruch; Historie; Tarifversion bleibt fest.
- `SimulationTest` – Ende-zu-Ende-Simulation einer Stadtfahrt.
- `SegmentGeometryTest` – Siebensegment-Anzeige (Komma, Ausrichtung, keine abgeschnittenen Segmente).

Nicht automatisiert getestet: Darstellung auf echten Geräten (kleines Smartphone, Querformat, Fold innen/außen),
Foreground-Service-Verhalten und echte GPS-Messung im Fahrzeug – bitte vor Nutzung im Fahrzeug selbst prüfen.

---

## 8. Projektstruktur

```
app/src/main/java/de/fa/taximeter/
  domain/            reine Logik ohne Android (testbar)
    tariff/          TariffProfile, TariffCatalog (amtliche Werte), FareEngine
    gps/             GpsFix, DistanceTracker (Filter), SimulatedRoute (Testmodus)
    trip/            MeterMachine (FREI/BESETZT/STOPP/KASSE), Zuschläge, TripRecord
    persist/         MiniJson, Codecs (lokale Speicherung)
  data/              MeterRepository, FileStore, AppSettings
  location/          GpsHub (LocationManager), MeterService (Foreground-Service)
  ui/                Compose-Oberfläche (rote Taximeter-Optik), Siebensegment-Anzeige
.github/workflows/build-debug-apk.yml   manuell auslösbarer APK-Build
```
