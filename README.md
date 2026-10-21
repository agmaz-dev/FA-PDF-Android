# FA PDF – Android-PDF-App

Vollständiges Android-Studio-Projekt (Kotlin, Jetpack Compose, Material 3, MVVM, Room, DataStore, Android Keystore).
Alle 25 Werkzeuge der Referenzgrafik sind programmiert – ohne Platzhalter-Bildschirme.

---

## 1. Ehrlicher Stand

| Was | Status |
|---|---|
| Quellcode aller Funktionen | **vollständig geschrieben** (≈ 8500 Zeilen Kotlin in 41 Dateien) |
| Android-Build / APK | **nicht erstellt** – in der Entwicklungsumgebung war der Zugriff auf das Android SDK und Maven gesperrt |
| Tests auf einem Android-Gerät | **nicht durchgeführt** |
| Unit-Tests der Kernlogik | **27 Tests, alle bestanden** (lokal mit dem Kotlin-Compiler 2.0.21 und JUnit 4.13.2 ausgeführt) |
| Algorithmus der Textbearbeitung | **mit einem Referenz-Prototyp an einer echten PDF geprüft** (siehe Abschnitt 6) |

Da das Projekt nie gegen das Android SDK kompiliert wurde, sind beim ersten Build **Kompilierfehler wahrscheinlich**
(z. B. leicht abweichende Methodennamen in PdfBox-Android). Sie sind typischerweise schnell behoben – am einfachsten
über den GitHub-Weg unten, bei dem ich die Fehlermeldungen direkt lesen und korrigieren kann.

---

## 2. So erhalten Sie die APK

### Weg A – ohne PC, direkt vom Handy (GitHub Actions, kostenlos)

1. Auf github.com ein kostenloses Konto anlegen und ein **privates** Repository erstellen (z. B. `fa-pdf`).
2. Den Inhalt dieser ZIP-Datei hochladen („Add file → Upload files“) – oder mir den Repository-Namen nennen,
   dann lade ich den Code selbst hoch.
3. GitHub baut automatisch (Datei `.github/workflows/android.yml`): Unit-Tests + APK.
4. Unter **Actions → letzter Lauf → Artifacts → FA-PDF-APK** die APK herunterladen und auf dem Handy öffnen
   („Installation aus unbekannten Quellen“ einmalig erlauben).

### Weg B – Android Studio am PC

1. Android Studio (Ladybug oder neuer) installieren, Projektordner öffnen.
2. Android Studio lädt Gradle 8.11.1, das Android SDK 35 und alle Bibliotheken automatisch.
3. **Build → Build APK(s)** → `app/build/outputs/apk/debug/app-debug.apk`.
4. Oder per Kommandozeile: `./gradlew testDebugUnitTest assembleDebug`

Voraussetzungen: JDK 17, Android SDK 35, minSdk 26 (Android 8.0). Der PDF-Scanner benötigt Google Play-Dienste.

---

## 3. Technische Grundlage und Lizenzen (alles kostenlos)

| Aufgabe | Lösung | Lizenz / Kosten |
|---|---|---|
| PDF bearbeiten, Formulare, Verschlüsselung, Seiten | **PdfBox-Android 2.0.27** | Apache 2.0, kostenlos |
| Seitendarstellung | Android `PdfRenderer` (PDFium, im System enthalten) | kostenlos |
| Texterkennung (OCR) | **Google ML Kit Text Recognition** (Latein: DE, EN, TR …) | kostenlos, auf dem Gerät |
| Dokumentscanner | **ML Kit Document Scanner** (Zuschnitt, Perspektive) | kostenlos, Play-Dienste |
| Offline-Übersetzung | **ML Kit Translation** (DE ⇄ EN ⇄ TR) | kostenlos, auf dem Gerät |
| Ersatzschriften | **Liberation Sans/Serif/Mono** (metrisch gleich zu Arial, Times, Courier) | SIL OFL |
| KI (Chat, Zusammenfassung, Fragen) | Anthropic- oder OpenAI-API mit **eigenem Schlüssel** | kostenpflichtig beim Anbieter |

Geprüft und **bewusst nicht** verwendet: Pdfium als Bearbeitungs-Engine (kann nur anzeigen), kommerzielle SDKs wie
PSPDFKit/Apryse (teuer, Lizenz nötig), iText (AGPL – problematisch für eigene Apps).

---

## 4. Funktionsübersicht

Legende: ✅ vollständig programmiert (auf Gerät noch ungetestet) · ⚠️ programmiert mit bewusster Einschränkung

### Organisieren
| Werkzeug | Umsetzung | |
|---|---|---|
| PDFs zusammenfügen | Mehrere Dateien wählen, Reihenfolge ändern, neue PDF | ✅ |
| PDF teilen | Nach Bereichen („1-3, 5“) oder jede Seite einzeln | ✅ |
| PDF drehen | Einzelne oder alle Seiten, 90°/180°/270°, mit Vorschau | ✅ |
| PDF-Seiten löschen | Auswahl per Miniatur, neue Version | ✅ |
| PDF-Seiten extrahieren | Auswahl per Miniatur, neue PDF | ✅ |
| PDF organisieren | Miniaturen verschieben, drehen, löschen | ✅ |

### Bearbeiten
| Werkzeug | Umsetzung | |
|---|---|---|
| **PDF bearbeiten** | Echte Textänderung: Originalglyphen werden aus dem Inhaltsstrom **entfernt** und der neue Text gesetzt (Zeile oder ganzer Absatz mit Neuumbruch); Text hinzufügen, löschen, verschieben; Schrift, Größe, Farbe, fett/kursiv; Bilder einfügen/verschieben/drehen; Rückgängig/Wiederherstellen; Speichern, Exportieren, Drucken, Teilen | ✅ |
| Automatische Schrifterkennung | Schriftname, Größe, Farbe, fett/kursiv, Zeilenabstand, Ausrichtung, Absatzstruktur aus dem PDF; bei Scans Größe/Farbe/Fettdruck aus den Pixeln; jederzeit korrigierbar | ✅ |
| Gescannte PDFs | OCR auf dem Gerät → Zeile antippen → Pixel werden durch die Papierfarbe ersetzt, neuer Text als Vektortext, übrige Zeilen bleiben durchsuchbar | ✅ |
| PDF kommentieren | Markieren, Unterstreichen, Durchstreichen, Notizen, Freihand – als standardkonforme Anmerkungen mit Erscheinungsbild | ✅ |
| PDF-Reader | Zoom (2 Finger), Suche mit Trefferanzeige, Seitensprung, eigene Lesezeichen, Inhaltsverzeichnis der PDF | ✅ |
| Seitenzahlen einfügen | 6 Positionen, 4 Formate, Startnummer, Größe, Randabstand, Deckblatt auslassen | ✅ |
| PDFs zuschneiden | Ränder in mm (CropBox) | ✅ |
| PDF schwärzen | Rechtecke oder Suchbegriff; Text wird **aus der Datei entfernt**, danach automatische Kontrolle; liegen Bilder im Bereich, wird die Seite als Bild neu aufgebaut; Metadaten optional entfernen | ✅ |
| Wasserzeichen | Text oder Bild, Position, Deckkraft, Größe, Drehung | ✅ |
| PDF-Formularausfüller | Erkennung von Text-, Datums-, Kontroll-, Options-, Auswahl- und Unterschriftsfeldern; Hinweis beim Öffnen; Profil-AutoFill mit Prüfliste; formularlose Vorlagen über Beschriftungserkennung | ✅ |
| PDF freigeben | Teilen-Menü, E-Mail, Drucken, „In Dateien speichern“ | ✅ |

### Ausfüllen und Signieren
| Werkzeug | Umsetzung | |
|---|---|---|
| Unterschreibe PDF | Unterschrift zeichnen (transparent), mehrere speichern, Profilen zuordnen, verschieben/skalieren/drehen, Unterschriftsfeld-Erkennung – **nie ohne Bestätigung** | ✅ |
| Unterschriften anfordern | Versand per E-Mail mit Unterschriftsbitte. **Kein Signaturdienst angebunden** (für Sign.com o. ä. ist ein Vertrag/API-Zugang nötig; keine erfundene API) | ⚠️ |

### Verschlüsseln
| Werkzeug | Umsetzung | |
|---|---|---|
| PDF-Passwort entfernen | Nur mit korrektem Passwort | ✅ |
| PDF verschlüsseln | AES-256, Öffnungs- und Berechtigungspasswort, Rechte (Drucken/Kopieren/Bearbeiten) | ✅ |
| Ebenen reduzieren | Formularfelder und Anmerkungen dauerhaft in den Inhalt übernehmen | ✅ |

### KI PDF
| Werkzeug | Umsetzung | |
|---|---|---|
| Mit PDFs chatten | Antworten nur aus dem tatsächlichen Dokumenttext (mit Seitenangaben) | ⚠️ eigener API-Schlüssel |
| KI-Zusammenfassung | Aussagen, Termine, Beträge, Fristen, Maßnahmen; anschließend Fragen stellen | ⚠️ eigener API-Schlüssel |
| PDF übersetzen | **Offline auf dem Gerät** (DE/EN/TR, kostenlos) oder per KI; Ergebnis als neue PDF | ✅ |
| KI-Fragengenerator | 10 Fragen mit Musterantwort und Seitenangabe | ⚠️ eigener API-Schlüssel |

### Scan
| Werkzeug | Umsetzung | |
|---|---|---|
| PDF-Scanner | Kamera, automatischer Zuschnitt, Perspektivkorrektur, mehrseitig, anschließend OCR („durchsuchbar machen“) | ✅ |

### Weitere Anforderungen
- **Familienprofile** (beliebig viele, frei definierbare Zusatzwerte) – verschlüsselt mit AES-256-GCM, Schlüssel im Android Keystore, optional Fingerabdruck/Gesicht/PIN. Keine Cloud-Sicherung (Backup ausgeschlossen).
- **Letzte Dokumente**: Startseite nach rechts wischen; Vorschaubild, Bearbeitungs- und Importdatum, Seitenzahl, Größe; Suche, Sortierung, Favoriten, Umbenennen, Aus Verlauf entfernen, Löschen; bleibt nach Neustart erhalten (Room-Datenbank).
- **Aktionsmenü** „Was möchten Sie mit diesem Dokument machen?“ mit allen 13 Aktionen; das gewählte Werkzeug öffnet sich direkt mit der Datei.
- **Android-Integration**: „Öffnen mit“ und „Teilen mit“ (auch mehrere PDFs) aus Gmail & Co., sichere `content://`-URIs über FileProvider, Speichern unter (Dateiauswahl), Drucken, Originaldatei überschreiben (wenn Schreibrecht besteht).

---

## 5. Wie die Textbearbeitung funktioniert

1. **Analyse** (`PageAnalyzer`): Für jede Glyphe werden Position, Schrift, Größe, Farbe und der *Inhaltsstrom-Operator*
   ermittelt, der sie zeichnet. Daraus entstehen Zeilen und Absätze.
2. **Entfernen** (`ContentRewriter`): Der Operator wird in einen `TJ`-Operator umgeschrieben; jede entfernte Glyphe wird durch
   eine exakt gleich große Positionskorrektur ersetzt (PDF 32000-1, Abschnitt 9.4.4). Alle anderen Zeichen bleiben an ihrer Stelle.
   Der alte Text ist danach **nicht mehr in der Datei** – keine Überlagerung.
3. **Neu setzen** (`TextEditEngine`), in dieser Reihenfolge:
   - mit der eingebetteten **Originalschrift**, wenn sie alle Zeichen enthält,
   - sonst mit Glyphen, die **an anderer Stelle im Dokument** mit derselben Schrift vorkommen (wirkt auch bei Teil-Einbettung),
   - sonst mit der passenden **Ersatzschrift** (Liberation, metrisch kompatibel zu Arial/Times/Courier) – mit Hinweis an den Benutzer.
4. **Scans**: OCR → Pixel der Zeile werden durch die gemessene Papierfarbe ersetzt → neuer Vektortext in gemessener Größe und Farbe.

---

## 6. Testprotokoll

### Tatsächlich durchgeführt
- **27 Unit-Tests – alle bestanden** (`app/src/test`): Koordinatenumrechnung für alle Seitendrehungen und CropBox-Versätze,
  Zeilen-/Absatzbildung, Leerzeichen-Rekonstruktion, Spaltentrennung, doppelt gezeichneter Fettdruck, TJ-Kompensation inkl.
  Zeichen- und Wortabstand, Umbruch, Ausrichtung, Feldzuordnung (Deutsch/Englisch/Türkisch, technische Feldnamen, Ausschlüsse,
  Kontextregeln), Seitenbereiche, Schriftklassifikation.
- **Referenz-Prototyp** (`tools/validierung/textbearbeitung_prototyp.py`): An einer erzeugten Brief-PDF mit Zeichen- und Wortabstand
  wurde „Mai“ entfernt und „Juni“ gesetzt. Ergebnis geprüft mit `pdftotext`: Originalwort nicht mehr in der Datei,
  **alle übrigen Wörter exakt an derselben Position**, neuer Text korrekt im Textfluss („15. Juni 2026“).

### Noch offen – auf einem Android-Gerät durchzuführen
Alle 18 Pflichtprüfungen aus dem Auftrag stehen noch aus, weil kein Build möglich war:

| Nr. | Prüfung | Ergebnis |
|---|---|---|
| 1 | Digitales PDF öffnen und ein Wort ändern | offen |
| 2 | Geändertes PDF speichern und erneut öffnen | offen |
| 3 | Gescanntes PDF per OCR erkennen | offen |
| 4 | OCR-Text bearbeiten und speichern | offen |
| 5 | Unterschiedliche Schriftarten analysieren | offen |
| 6 | Formularfelder ausfüllen | offen |
| 7 | Familienprofil anlegen | offen |
| 8 | Formular mit gespeicherten Daten ausfüllen | offen |
| 9 | Unterschrift erstellen und einfügen | offen |
| 10 | Mehrere PDFs zusammenfügen | offen |
| 11 | PDF-Seiten teilen | offen |
| 12 | PDF-Seiten drehen | offen |
| 13 | PDF verschlüsseln | offen |
| 14 | Dokumenthistorie nach App-Neustart prüfen | offen |
| 15 | Aktionsmenü und Navigation testen | offen |
| 16 | PDF per Android-Freigabe weitergeben | offen |
| 17 | KI-Funktionen mit tatsächlichem Dokumentinhalt prüfen | offen |
| 18 | Fehlerbehandlung bei beschädigten PDFs testen | offen |

---

## 7. Technische Einschränkungen

**Textbearbeitung**
- Bearbeitet wird zeilen- bzw. absatzweise. Enthält eine Zeile mehrere Schriften (z. B. ein fettes Wort), wird sie mit der vorherrschenden Schrift neu gesetzt.
- Ist die Originalschrift nur teilweise eingebettet und fehlen Zeichen auch im übrigen Dokument (z. B. „ğ“ in einem rein deutschen Brief), wird die Ersatzschrift verwendet – die App weist darauf hin.
- Text in eingebetteten Objekten (Form-XObjects) und gedrehter/senkrechter Text können nicht direkt geändert werden; Ausweg: „OCR → Seite erkennen“ (Bearbeitung als Bild).
- Wird ein Absatz länger als vorher, fließt er nicht in nachfolgenden Inhalt um (Hinweis erscheint).
- Bereits eingefügte Bilder lassen sich nach dem Übernehmen nicht mehr verschieben (vorher frei positionierbar; danach „Rückgängig“).

**Scans**
- Die bearbeitete Seite wird als neues Bild gespeichert (JPEG, ca. 200 dpi). Mehrfaches Bearbeiten derselben Seite kann die Bildqualität leicht mindern.
- Die Schrift eines Scans kann nicht sicher erkannt werden; vorgeschlagen werden Größe, Farbe und Fettdruck, die Schriftart wählen Sie bei Bedarf selbst.
- Handschrift wird nicht zuverlässig erkannt.

**Formulare & Unterschriften**
- Reine XFA-Formulare (manche Behördenformulare) werden nur von Adobe-Programmen vollständig unterstützt – die App meldet das.
- Die Beschriftungserkennung für Formulare ohne Felder ist eine Hilfe; Positionen bitte in der Vorschau prüfen.
- Unterschriften sind **einfache elektronische Unterschriften (Bild)**. Qualifizierte, kryptografische Signaturen (QES) benötigen ein Zertifikat eines Vertrauensdiensteanbieters und sind nicht enthalten.

**Weitere**
- Zuschneiden blendet Ränder aus (CropBox); die Inhalte bleiben in der Datei. Für vertrauliche Inhalte „Schwärzen“ verwenden.
- Beim Bearbeiten einer verschlüsselten PDF wird die Arbeitskopie ohne Passwort gespeichert (Hinweis erscheint); erneut schützen mit „PDF verschlüsseln“.
- Android `PdfRenderer` zeigt Anmerkungen nicht zuverlässig an; die App erzeugt deshalb intern eine abgeflachte Vorschau – die gespeicherte Datei behält die Anmerkungen.
- Sehr lange Dokumente werden für die KI auf ca. 150 000 Zeichen gekürzt (Hinweis erscheint).
- Release-Signatur und ProGuard/R8 sind noch nicht eingerichtet (Debug-APK ist installierbar).

---

## 8. KI: Schlüssel, Kosten, Datenschutz

- In der APK ist **kein** API-Schlüssel enthalten. Unter *Einstellungen → KI-Funktionen* tragen Sie Ihren eigenen Schlüssel ein (Anthropic oder OpenAI); er wird mit dem Android Keystore verschlüsselt.
- Vor **jeder** Übertragung erscheint ein Zustimmungsdialog mit Anbieter, Datenmenge (Zeichen, geschätzte Tokens) und Link zu den aktuellen Preisen.
- Kosten entstehen nur beim Anbieter und hängen vom Modell ab (für ein mehrseitiges Schreiben typischerweise Cent-Beträge – bitte die Preisliste des Anbieters prüfen).
- Ohne Schlüssel funktionieren alle übrigen Werkzeuge, OCR und die Offline-Übersetzung vollständig.

---

## 9. Projektstruktur

```
app/src/main/java/de/fapdf/
├── FaPdfApp.kt, MainActivity.kt
├── data/        Room-Datenbank, Repositories, Keystore-Verschlüsselung, Profile, Einstellungen
├── pdf/         PDF-Engine: Analyse, Textbearbeitung, OCR/Scan, Formulare, Anmerkungen, Schwärzen, Seitenwerkzeuge
├── ai/          KI-Client (eigener Schlüssel), Textextraktion, Offline-Übersetzung
└── ui/          Compose-Oberfläche: Startseite, Arbeitsbereich (Lesen/Bearbeiten/Kommentieren/Formular/Unterschrift/Schwärzen),
                 Werkzeuge, KI, Profile, Unterschriften, Einstellungen
app/src/test/    Unit-Tests
tools/validierung/  Referenz-Prototyp der Textbearbeitung
.github/workflows/  Automatischer APK-Build
```
