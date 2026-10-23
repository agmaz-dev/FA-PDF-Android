# 3. Kopiervorlage für Claude oder andere Entwickler

Den folgenden Text zusammen mit diesem Projektarchiv übergeben. Das private Signierbackup NICHT mit hochladen. Gewünschte Änderungen am Ende konkret ergänzen.

---

Bitte bearbeite mein bestehendes Android-Projekt Taxi L150 auf Basis des beigefügten vollständigen Quellcodes 1.6.7. Lies zuerst START_HIER.md und die Dokumentation. Das ist eine funktionierende Java-Android-App mit lokaler HTML/CSS/JavaScript-WebView, kein Flutter- und kein Gradle-Projekt.

Arbeite am vorhandenen Projekt weiter. Ersetze es nicht durch eine neue Demo, reine Browserseite oder nachgebildete Oberfläche. Bewahre das bestehende freigegebene Design und alle vorhandenen Funktionen, soweit meine konkrete Änderung sie nicht betrifft. Die tatsächliche Oberfläche steht in app/src/main/assets/index.html; die Daten werden über native Java-Klassen und TaxiAndroid geladen.

Verbindliche Punkte:

- Sichtbarer App-Name Taxi L150; Update-Paket de.taxil150.app.test164 beibehalten.
- Java-Namensraum de.taxil150.app, vollständig qualifizierte Manifest-Komponenten und --custom-package im Build erhalten.
- Android ab API 26, derzeit Plattform/Target 35 und Build Tools 35.0.0, JDK 17.
- L bedeutet Brandenburg, M bedeutet Berlin.
- „Erstes wartendes Taxi“ zeigt die Punkte aus der ersten Datenzeile der Warteliste, keine Differenz und keine Punkte aus der Aufrufliste.
- Eigene Wartepunkte bleiben separat. Null ist ein gültiger Wert, fehlende Daten sind ein Strich.
- Zielbereich, Gebetszeiten mit Countdown, Wetter, Ankünfte und 45-Minuten-Timer erhalten.
- Ankünfte zeigen alle Terminals; keine erneute Terminalauswahl einbauen.
- Echte Aufrufe und Tests unterscheiden; Tests nicht als echte Aufrufe im Verlauf speichern.
- Das Display bleibt bei sichtbarer Activity an; beim Verlassen normale Bildschirmabschaltung wieder zulassen.
- TLS- und Hostnamenprüfung nicht deaktivieren. Die beiden domainbezogenen Root-Ergänzungen in network_security_config.xml erhalten.
- Bei fehlerhaften oder veralteten Daten keine erfundenen Livewerte anzeigen.
- Einstellungen und lokale Daten bei Updates erhalten.
- Keine Schlüssel oder Passwörter ins Projekt aufnehmen. Für die Signierung wird der vorhandene private Schlüssel lokal bereitgestellt.

Prüfe die betroffenen Funktionen mit passenden Tests. Erstelle eine vollständige ZIP mit Änderungen, Anleitung und ehrlichem Prüfbericht. Behaupte eine APK oder einen Gerätetest nur dann als erfolgreich, wenn wirklich gebaut bzw. getestet wurde. Wenn deine Umgebung keinen Android-Build erlaubt, liefere den Quellcode und genaue lokale Bauschritte; erfinde keine Download-Datei.

Für eine neue Release-Version versionCode über 26 erhöhen, versionName und APK-Dateinamen konsistent ändern. Das mitgelieferte build-manual.sh ist der dokumentierte Buildweg.

Meine gewünschte Änderung:

[HIER DIE ÄNDERUNG EINTRAGEN]

---

## Wenn mit „Cloud“ ein Speicher-/Hosting-Anbieter gemeint ist

Als private Sicherung kann das Archiv bei OneDrive oder einem anderen eigenen Speicher abgelegt werden. Zum Bauen braucht es weiterhin eine geeignete Android-Buildumgebung. Ein Upload des ZIP zu einem Webhoster wandelt die App nicht automatisch in eine Web-App um. Für eine Browser-Version müssten native Schnittstellen und Datenabrufe separat ersetzt werden.
