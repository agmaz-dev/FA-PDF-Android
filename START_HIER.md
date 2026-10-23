# Taxi L150 – vollständige Projektübergabe

Version **1.6.9**, Versionscode **28**. Dokumentation: **23.10.2026**.

Dieses Archiv enthält den tatsächlichen Android-Quellcode des zuletzt ausgelieferten Stands einschließlich Oberfläche, Design-Ressourcen, Tönen, Zertifikaten und Tests. Der Nutzer hat nach Installation zurückgemeldet: „Geht alles“. Das ist eine Rückmeldung zum getesteten Gerät und keine Zusicherung für jedes Android-Modell.

## Wo anfangen?

1. Für Überblick und technische Herstellung: `Dokumentation/01_AUFBAU_UND_DESIGN.md`.
2. Für eine neue APK: `Dokumentation/02_APK_ERSTELLEN.md`.
3. Für Weiterarbeit mit Claude oder einem Entwickler: `Dokumentation/03_AUFTRAG_FUER_CLAUDE.md`.
4. Für Wartung und Fehlersuche: `Dokumentation/04_WARTUNG_UND_PRUEFUNG.md`.
5. Ältere Versionshinweise: `README.txt`. Dortige Aussagen „noch kein Gerätetest“ dokumentieren den damaligen Auslieferungsstand; die oben genannte Nutzer-Rückmeldung erfolgte anschließend.

Die Anwendung ist eine native Android-App mit lokal eingebauter HTML/CSS/JavaScript-Oberfläche und Java-Datenabruf. Es ist **kein Flutter-Projekt**, kein Gradle-Projekt und keine eigenständig im Browser funktionsfähige Web-App. Der dokumentierte Build erfolgt mit `build-manual.sh`.

## Wichtig für spätere Updates

Das installierte Paket heißt `de.taxil150.app.test164`, der sichtbare App-Name ist **Taxi L150**. Diesen Paketnamen trotz „test164“ nicht ändern: Er gehört zur funktionierenden Update-Linie ab 1.6.4.

Für Updates werden zusätzlich der **bisherige private Signierschlüssel und sein Passwort** benötigt. Sie sind bewusst NICHT in diesem Archiv. Das separate private Backup heißt `TaxiMonitor_Test_Signierung_Privat.zip`. Es darf nicht öffentlich oder zusammen mit diesem Projekt bei Claude hochgeladen werden. Ohne denselben Schlüssel ist keine normale Aktualisierung der vorhandenen Installation möglich.

Das Projekt kann bei Claude hochgeladen oder privat gesichert werden. Ein Upload allein erzeugt keine APK: Die ausführende Umgebung braucht die in der Bauanleitung genannten Werkzeuge. Dieses Archiv enthält keine bereits eingerichtete Entwicklungsumgebung und keine neue APK.
