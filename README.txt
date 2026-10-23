TAXI L150 – VERSION 1.6.7
Stand: 22.10.2026 | Versionscode 26 | Android ab 8

BER-ANKÜNFTE: ZERTIFIKATSKOMPATIBILITÄT
Die BER-Datenquelle unter ber.berlin-airport.de verwendet eine andere CA als APCOA: GlobalSign GCC R46 AlphaSSL CA 2025 → GlobalSign Root R46. Die bisherige Ergänzung für APCOA galt ausdrücklich nur für APCOA. Für genau ber.berlin-airport.de wird jetzt zusätzlich zum normalen Android-Systemvertrauen die öffentlich verifizierte GlobalSign Root R46 eingebunden. Keine Freigabe für Unterdomains oder andere Hosts, keine deaktivierte Zertifikatsprüfung. Die System-CAs bleiben nutzbar, etwa bei Wechsel des CDN-Zertifikats.
SHA-256 der GlobalSign Root R46: 4fa3126d8d3a11d1c4855a4f807cbad6cf919d3a5a88b03bea2c6372d93c40c9
Offizielle Referenz: https://support.globalsign.com/ca-certificates/root-certificates/globalsign-root-certificates
Öffentlich beobachtete BER-Kette: SSL Labs. Root-Fingerabdruck gegen die veröffentlichten Angaben des Zertifikatsausstellers abgeglichen. Keine lokalen Proxy-Zertifikate übernommen.

INSTALLATION
Direkt über die zuletzt installierte Test-App 1.6.4–1.6.6 installieren. App-Name weiterhin „Taxi L150“, Paket de.taxil150.app.test164 und identische Test-Signieridentität. Nicht deinstallieren, damit Einstellungen und Verlauf dieser Installation erhalten bleiben. Die ganz ursprüngliche App de.taxil150.app ist ein separates Paket.
APCOA-Korrektur, Ersttaxi-Punkte und Display-an-Funktion bleiben enthalten.

PRÜFUNG UND GRENZEN
Offline-Validierung der öffentlich beobachteten BER-Kette mit zusätzlicher Root-CA; falscher Hostname und abgelaufenes Serverzertifikat müssen weiterhin abgewiesen werden. Zusätzlich Regression der APCOA-CA, genaue Domainzuordnung, APK-Bau, Signatur-/Update- und Ressourcenprüfung. Noch kein Gerätetest auf dem betroffenen Handy. GlobalSign nennt regulär Android 12 als unterstützt; konkrete Hersteller-Zertifikatsspeicher und Netzwerkbedingungen können abweichen. Ob genau die gemeldete Geräteverbindung jetzt funktioniert, muss dort getestet werden.

Vorherige Versionshinweise:

TAXI L150 – VERSION 1.6.6
Stand: 22.10.2026 | Versionscode 25 | Android ab 8

ZERTIFIKATSKOMPATIBILITÄT UND APP-NAME
• Launcher-App-Name wieder „Taxi L150“.
• Für tms-info.apcoa.de wird zusätzlich zu den normalen Android-System-CAs das öffentliche Stammzertifikat „Sectigo Public Server Authentication Root R46“ gebündelt. Die extern geprüfte APCOA-Serverkette verwendet dieses neuere Stammzertifikat. Ältere Android-Systeme können es in ihrem Zertifikatsspeicher noch nicht enthalten; der gemeldete Fehler „Trust anchor for certification path not found“ ist damit vereinbar.
• Das zusätzliche Zertifikat gilt ausschließlich für genau tms-info.apcoa.de, nicht dessen Unterdomains oder andere Datenquellen. Normale Zertifikats-, Gültigkeits- und Hostnamenprüfung bleibt aktiv. Kein Trust-all-Manager, keine ausgeschaltete Hostnamenprüfung, kein unverschlüsselter HTTP-Abruf.
• Offizielle Referenz des CA-Zertifikats: https://crt.sectigo.com/SectigoPublicServerAuthenticationRootR46.crt
• Zertifikatsbytes aus der öffentlich beobachteten TLS-Kette (SSL Labs); Identität über den veröffentlichten Root-Fingerabdruck abgeglichen.
• SHA-256: 7bb647a62aeeac88bf257aa522d01ffea395e0ab45c73f93f65654ec38f25a06
• Das unveränderte öffentliche Root-Zertifikat wurde anhand seines SHA-256-Fingerabdrucks sowie der öffentlich gemeldeten APCOA-Kette abgeglichen. Testzertifikate unter tests/tls-fixtures sind ausschließlich öffentliche Zertifikate, keine privaten Schlüssel.

INSTALLATION
Über „Taxi Monitor Test“ 1.6.4/1.6.5 installieren; danach heißt diese App „Taxi L150“. Paket de.taxil150.app.test164 und die Test-Signieridentität bleiben identisch, Einstellungen und Verlauf dieser Installation bleiben beim normalen Update erhalten. Die ganz ursprüngliche Installation de.taxil150.app ist weiterhin ein getrenntes Paket und kann nicht mit diesem Schlüssel aktualisiert werden.
Die Anzeige des ersten wartenden Taxis und das eingeschaltete Display bei geöffneter App bleiben enthalten. Keine App vorher deinstallieren.

PRÜFUNG UND GRENZEN
Die reale öffentlich beobachtete APCOA-Zertifikatskette wird offline mit der zusätzlichen CA erfolgreich validiert; falscher Hostname und abgelaufenes Serverzertifikat werden abgewiesen. Build, Paket-Metadaten und Signatur werden geprüft. Ein direkter Gerätetest auf dem betroffenen Android-12-Handy steht aus. Andere Ursachen wie veränderte Netzwerkzertifikate oder ein falsches Systemdatum werden durch das Hinzufügen dieser CA nicht automatisch gelöst.

Vorherige Versionshinweise:

TAXI MONITOR TEST – VERSION 1.6.5
Stand: 22.10.2026 | Versionscode 24 | Android ab 8

DISPLAY BLEIBT AN
Solange die App im Vordergrund geöffnet ist, wird die automatische Bildschirmabschaltung verhindert. Dies gilt auch für Einstellungen, Ankünfte und Tarifrechner, unabhängig davon, ob die Taxi-Überwachung läuft. Beim Verlassen/Pausieren der Activity wird das Flag wieder entfernt; die normalen Bildschirm-Einstellungen gelten. Die Seitentaste kann das Handy weiterhin bewusst sperren. Keine zusätzliche Berechtigung und kein dauerhafter Hintergrund-Wakelock.

INSTALLATION
Über die separate Testversion 1.6.4 „Taxi Monitor Test“ installieren. Paket de.taxil150.app.test164 und dieselbe Test-Signieridentität bleiben erhalten; Einstellungen und Verlauf der Test-App bleiben beim normalen Update erhalten. Die ältere ursprüngliche App de.taxil150.app bleibt separat installiert. Die Punkte des ersten wartenden Taxis aus 1.6.4 sind enthalten.

PRÜFUNG
Java-/APK-Bau, vorhandene Parser-/Timer-/Aktualisierungsprüfungen sowie APK-Signatur und Abgleich mit dem Test-Zertifikat von Version 1.6.4. Kein Android-Gerätetest; auf dem Handy das Überschreiten der normalen Display-Abschaltzeit bei geöffneter App und anschließend die normale Abschaltung nach Verlassen der App prüfen.

Vorherige Versionshinweise:

TAXI MONITOR TEST – VERSION 1.6.4
Stand: 22.10.2026 | Versionscode 23 | Android ab 8

NEU: PUNKTE DES ERSTEN WARTENDEN TAXIS
In der Warteplatz-Kachel steht unterhalb einer Trennlinie „Erstes wartendes Taxi“ mit dessen Wartepunkten. Es sind die Punkte aus der ersten Datenzeile der Warteliste, unabhängig von deiner Taxinummer. Aufgerufene Taxis werden nicht dafür verwendet. Keine Differenz und keine berechnete Wartezeit. Bei leerer oder noch nicht geladener Liste steht „–“; null Punkte werden ausdrücklich als „0 Punkte“ angezeigt. Bei einem Abruffehler gilt die bestehende Warnung vor veralteten Werten auch für diese Anzeige.

INSTALLATION DIESER TESTVERSION
Diese APK installiert sich separat als „Taxi Monitor Test“ mit Paket de.taxil150.app.test164. Die bisherige App NICHT deinstallieren. Der ursprüngliche private Signierschlüssel ist in dieser Arbeitsumgebung nicht mehr vorhanden, daher ist diese Version kein direktes Update der alten Installation. In der Test-App eigene Taxinummer prüfen und Überwachung bei Bedarf starten. Beide Apps haben getrennte Einstellungen, Timer und Verlauf. Für den Vergleich möglichst nur in einer App die Überwachung laufen lassen.

Die installierbare Test-APK wurde mit einer neuen Test-Signieridentität erzeugt. Der private Schlüssel und Passwörter gehören nicht in dieses Quellcode-ZIP.

PRÜFUNG DIESER TESTVERSION
• Java-Parser: erste Wartelistenzeile, eigene Punkte unabhängig davon, null Punkte, leere Liste, eigene Nummer auf Rang 1 und klare Abgrenzung zu aufgerufenen Taxis bestanden.
• Datenübergabe: echte JavaScript-Renderfunktion mit simulierten Anzeigeelementen geprüft; Wertewechsel, null, fehlende Werte und bestehende Fehlerwarnung bestanden. Syntax aller eingebetteten Skripte geprüft.
• Bestehende Parser-/Aktualisierungs-/Timer- und Tarifrechnertests bestanden. Ein echter APCOA-Abruf mit HTTP 200 wurde erfolgreich geparst.
• Android-APK gegen Plattform 35 gebaut, Paket und Versionsangaben geprüft, APK-Signaturen v2/v3 verifiziert.
• Die grafische Chromium-Prüfung konnte wegen einer Betriebssystembeschränkung der Testumgebung nicht starten. Daher keine neue visuelle Browserprüfung und kein Android-Gerätetest dieser Version. Der Benutzer testet die APK auf seinem Handy.

Der Rest dieses Dokuments beschreibt die unveränderte Ausgangsversion 1.6.3. Deren Aussagen zur identischen Update-Signatur und damaligen Prüfungen gelten NICHT als Nachweis für diese Testversion.

TAXI MONITOR – VERSION 1.6.3
Stand: 20.10.2026 | Versionscode 22 | Android ab 8, aktuelles Android System WebView empfohlen

INSTALLATION ALS UPDATE
TaxiMonitor_1.6.3_AlleTerminals.apk über die bestehende App installieren; nicht vorher löschen. Die Signieridentität und das Paket de.taxil150.app bleiben gleich. App öffnen, eigene Taxinummer prüfen, gegebenenfalls Überwachung starten. Den Aufrufalarm auf dem Handy testen.

STARTSEITE
Dunkelblaues Design nach dem freigegebenen Entwurf: Nummernfeld, Überwachungsstatus, Warteplatz, Wartepunkte, wartende Taxis, Brandenburg (L), Berlin (M), neue Kachel „Ankünfte am BER“, gelbe Start-/Pausentaste und Aufruftest. Unter den L-/M-Kacheln steht der Zielbereich; darunter das Berliner Gebetszeitenfeld. Ankünfte und Timer stehen nebeneinander. Beim tatsächlichen Aufruf erscheint der Zielbereich groß im gelben Aufrufbildschirm. Einstellungen über das Zahnrad; keine untere Menüleiste.

NEU IN 1.6.3 – ALLE TERMINALS OHNE UMSCHALTER
• Die obere Auswahl Terminal 1 / Alle Terminals wurde aus der Ankunftsseite entfernt. Die Liste zeigt dauerhaft Ankünfte aus allen Terminals im gewählten Zeitraum. Die Kennzeichnung T1 bzw. T2 bleibt auf jeder Flugkarte erhalten.
• Der bisher standardmäßig aktive T1-Filter entfällt vollständig; die Serverabfrage hatte bereits alle Terminals geladen. Stunden-/Tagesauswahl, Suche, Sortierung und Aktualisierung bleiben erhalten. Der frei gewordene Platz steht der Liste zur Verfügung.
• Bestehende Ankunfts-Oberflächenprüfung an die neue Anforderung angepasst und bestanden: T1- und T2-Flüge gemeinsam ohne Umschalten, Suche, Uhrzeiten, Tageswechsel, Cachefehler, Navigation und Aufrufvorrang. Layout bei 320/393/412/768 Pixel geprüft. Build-/Parsertests sowie Update-Signaturprüfung bestanden. Kein Android-Gerätetest dieser Version.

AUS VERSION 1.6.2 – GRÖSSERE GEBETSNAMEN UND UHRZEITEN
• Nur die sechs Gebetsnamen und die sechs Uhrzeiten im bestehenden Gebetszeitenfeld wurden vergrößert. Auf einem 393-Pixel-Display steigen die Namen von 10 auf ca. 12,2 Pixel, die Uhrzeiten von 15 auf ca. 18,1 Pixel. Schriftgrößen passen sich an die Displaybreite an; auf breiten Displays 16/24 statt 14/21 Pixel.
• Der Innenabstand wurde neu verteilt, damit die Kachel nicht wächst. Die Uhrzeiten sind leicht schmaler gesetzt, sodass zwischen den sechs Werten Abstand bleibt. Überschrift, Datum, Countdown und übrige Funktionen bleiben erhalten.
• Vorher-/Nachher-Messung bei 320/360/393/412/768 Pixel: Namen und Uhrzeiten jeweils größer, Kartenhöhe an keiner Breite erhöht, keine seitlichen Überläufe. Darstellung visuell kontrolliert. Bestehende Gebetszeiten-/Verlaufsprüfung, Build und Signaturprüfung bestanden. Kein Android-Gerätetest dieser Version.

AUS VERSION 1.6.1 – PERSÖNLICHE KENNZEICHNUNG
• Auf der Hauptseite steht unter den Bedienelementen dezent „Entwickelt von FA“.
• In den Einstellungen ist die Karte „Über diese App“ hinzugefügt: FA-Initialen, Taxi Monitor, „Entwickelt von Feridun“, Versionsnummer und Kontakt feridunbln@gmail.com. Vorerst wird nur der Vorname verwendet; der Familienname wird in dieser Karte nicht angezeigt.
• Die Versionsnummer wird aus dem installierten Android-Paket übernommen. Die E-Mail-Schaltfläche öffnet über ACTION_SENDTO eine vorhandene E-Mail-App mit Empfänger und Betreff „Taxi Monitor – Rückmeldung“. Es wird nichts automatisch gesendet. Ohne passende E-Mail-App zeigt Android einen Hinweis mit der Adresse.
• Prüfung: Kennzeichnung und E-Mail-Text, kein Familienname in der Infokarte, Kontaktknopf zur simulierten Android-Brücke, Paket-Versionsanzeige und Layout bei 320/393/412/768 Pixel geprüft. Bisherige Taxi-Oberflächenprüfung sowie Build-Parsertests bestanden. APK mit bisherigem Signierzertifikat gebaut. Öffnen der tatsächlich auf dem Handy installierten E-Mail-App muss dort getestet werden.

AUS VERSION 1.6.0 – TAXI-TARIFRECHNER IN DEN EINSTELLUNGEN
• Zahnrad → Taxi-Tarifrechner öffnen. Eigene Unterseite im freigegebenen dunkelblau/gelben Design: Auswahl Ab BER / Ab Berlin, Fahrstrecke, optionale Wartezeit, BER-Ladeleiste, Großraumtaxi und zusätzliche Auswahl für Gutschein/Rechnung über Taxizentrale. Ergebnis mit Kostenaufschlüsselung und Vergleich zum anderen Fahrtbeginn. Keine Änderung an der Startseite.
• Die Berechnung erfolgt offline in der App. Startwert wie im Entwurf: 30 km, keine Wartezeit, BER-Ladeleisten-Zuschlag aktiv. Schätzung ab BER 68,80 €, bei Start in Berlin 71,40 €. Die Vergleichszeile bezeichnet ausdrücklich einen anderen Fahrtbeginn; ab BER darf nicht beliebig der Berliner Stadttarif gewählt werden.
• BER-Tarifstufe 3: Grundpreis 4,30 €, erste 5 km zu 2,60 €/km, darüber 2,00 €/km. Berliner Normaltarifstufe 2: Grundpreis 4,30 €, erste 3 km zu 2,80 €/km, weitere 4 km zu 2,60 €/km, darüber 2,10 €/km. BER-Zuschlag 1,50 € nur bei Auswahl Ab BER und gesetzter Ladeleisten-Option. Bei Auswahl Ab Berlin wird diese Option ausgeschaltet und deaktiviert. Großraumzuschlag 6 €, Gutschein-/Rechnungssystem der Taxizentrale 1,50 €.
• WICHTIG ZUR WARTEZEIT: Bei der vertieften Quellenprüfung wurde ein Widerspruch zur vereinfachten Taxi-Berlin-Webübersicht festgestellt. Die amtliche Berliner Verordnung § 5 Abs. 1 unterscheidet 39 €/Stunde in Tarifstufe 2 und 36 €/Stunde in Tarifstufe 3. Der Rechner verwendet diese amtlichen unterschiedlichen Sätze. Erste Minute je Stopp frei. Das einzelne Wartezeitfeld bildet einen zusammenhängenden Stopp ab; 10 eingegebene Minuten ergeben somit 9 berechnete Minuten. Es ist nicht die gesamte Fahrtdauer einzugeben.
• Die App zeigt ausdrücklich eine Schätzung, keinen garantierten Taxameterpreis oder Festpreis. Kilometer und berechenbare Wartezeit werden anteilig gerechnet, Einzelpositionen auf Cent gerundet und anschließend zu einer konsistenten Gesamtsumme addiert. Die gesetzlich vorgesehenen 0,20-€-Taxameter-Schaltschritte sowie eine Folge mehrerer Stopps werden nicht exakt simuliert. Damit bleibt das Ergebnis des Bildentwurfs eine lineare Preisorientierung. Kein Kurzstrecken-/Winketarif, kein Tarifkorridor für vorbestellte Festpreise und kein eigenständiger allgemeiner LDS-Tag-/Nachttarif.
• Eingaben mit Komma oder Punkt, höchstens zwei Nachkommastellen. Fahrstrecke größer 0 bis 1000 km, Wartezeit 0 bis 1440 Minuten. Ungültige Eingaben erzeugen einen Hinweis. Nach Änderungen an Strecke/Wartezeit/Zuschlägen wird ein vorheriges Ergebnis ausgeblendet, bis neu berechnet wird. Beim Tarifwechsel erfolgt die Neuberechnung unmittelbar. Die Eingaben bleiben während der Navigation in der geöffneten App bestehen; nach App-Neustart beginnt der Rechner wieder beim Beispielwert.
• Tarifdetails und Quellen sind aufklappbar. Enthalten sind Satzstaffeln, Wartezeit, Voraussetzungen der Zuschläge, Erklärung der Schätzung, Pflichtfahrgebiets-Hinweis und externe Quellenknöpfe. Geprüft am 20.10.2026. Tarife sind in der App hinterlegt und werden nicht automatisch aktualisiert.
• Prüfungen: 30-km-Beispiele, 3/5/7/10-km-Grenzen, Teilkilometer, Komma/Punkt, 36/39-Euro-Wartesätze, erste Freiminute, Zuschläge, keine BER-Gebühr im Berlin-Modus, ungültige Zahlen und Cent-Summenkonsistenz bestanden. Chromium: Rechner öffnen, Tarifwechsel, Eingaben/Zuschläge, ungültige Eingaben, Ausblenden veralteter Ergebnisse, Quellenknöpfe, native Zurücksimulation, Rückkehr zur Flugansicht und Vorrang eines echten Aufrufs geprüft. Layout 320/360/393/412/768 Pixel ohne horizontales Überlaufen, visuell kontrolliert. Alle bisherigen UI-/Parsertests bestanden. Signierte APK mit identischem Updatezertifikat. Kein Android-Gerätetest dieser Version.

AUS VERSION 1.5.0 – FREIGEGEBENES LUFTIGES DESIGN UND 45-MINUTEN-TIMER
• Startseite nach dem zuletzt freigegebenen hochformatigen Entwurf: mehr Höhe und Innenabstand, dunkelblaue Karten, gelber Warteplatz, mittige Überschrift, Taxinummer/Status in gemeinsamer Karte. Reihenfolge: Warteplatz → Punkte/Gesamtzahl → kleinere Brandenburg-/Berlin-Kacheln ohne „Im Speicher“ → Zielbereich → Gebetszeiten → Ankünfte/Timer nebeneinander → dunkle Pausentaste → umrandeter Aufruftest.
• Die gleich hohen Aktionskacheln stehen nebeneinander. Links Flugzeugsymbol und „Ankünfte am BER“, rechts Stoppuhr, „45-Min.-Timer“, Restzeit und gelbe Starttaste. Die Startansicht entspricht dem Bildentwurf. Auf kleineren Geräten scrollt die Seite nach unten; die Inhalte werden nicht auf die Bildschirmhöhe zusammengestaucht.
• Timer: Start bei 45:00, sekündliche Restzeitanzeige, Pause und Fortsetzen. Nach dem Start wird zusätzlich eine kleine Zurücksetzen-Taste eingeblendet; sie setzt auf 45 Minuten und hält den Timer an. Bei 00:00 erscheint „45 Minuten abgelaufen“; Start beginnt einen neuen 45-Minuten-Lauf.
• Der Timer ist unabhängig von Taxi-Überwachung und Flugansicht. Er bleibt bei App-Schließen, Prozessneustart und Update gespeichert. Seine Restzeit wird aus einer gespeicherten Frist berechnet, nicht aus gezählten UI-Ticks. Während desselben Handy-Starts verwendet er Androids elapsedRealtime einschließlich Ruhezeit; das vermeidet Änderungen durch Korrekturen der Systemuhr. Nach einem Handy-Neustart bzw. wenn die Boot-Kennung nicht verfügbar ist, wird auf die gespeicherte Systemzeitfrist zurückgegriffen. Eine Uhrkorrektur während eines solchen Neustarts kann die Restzeit beeinflussen.
• Dies ist ein stiller Timer: kein zusätzlicher Ton und keine Timer-Benachrichtigung. Beim erneuten Öffnen zeigt die App die korrekte Restzeit bzw. den Ablauf an. Der echte Taxi-Aufruf verwendet weiterhin die vorhandene Zurna und die bestehenden Android-Kanaleinstellungen. Gebetszeiten bleiben ebenfalls ohne Ton.
• Prüfungen bestanden: Java-Timerlogik für 45 Minuten, Pause/Fortsetzen, Wiederherstellung nach Prozesswechsel, Uhrkorrektur, Handy-Neustart als Logikszenario, Ablauf, Neustart und Reset. Chromium-Bedienprüfung für Start/Pause/Reset, 44:59/00:00, Ablaufanzeige, Navigation ohne Timerverlust sowie Anordnung ohne horizontales Überlaufen bei 320/360/393/412/768 Pixel Breite. Bestehende Taxi-, Flug-, Gebetszeiten-, Verlaufs- und Aktualisierungsprüfungen erneut bestanden. Native Android-Brücke in den Browserprüfungen simuliert; kein Gerätetest dieser APK.

AUS VERSION 1.4.2 – BER-AKTUALISIERUNG UND STATUS
• Fehlerursache: Die alte Drei-Minuten-Warnung bewertete sowohl den erfolgreichen Abrufzeitpunkt als auch updated_at aus der BER-Antwort. Ein älterer BER-Datenstand löste daher trotz gerade erfolgreichem Abruf eine Aufforderung zum erneuten Laden aus.
• Die Warnung richtet sich nun nach dem letzten erfolgreichen App-Abruf bzw. einem Abruffehler. Der vom BER gemeldete Datenstand wird separat mit Datum/Uhrzeit und bei Bedarf Alter dargestellt. Ein aktueller Abruf garantiert keinen neueren Datenstand vom Flughafen; dieser Unterschied bleibt sichtbar.
• Unter dem Filterbereich steht die genaue Uhrzeit des letzten erfolgreichen Abrufs mit vergangener Zeit. Während des Abrufs erscheint eine Ladeanzeige; das Aktualisieren-Symbol dreht sich. Eine kurze Sperre bzw. eine Fehlerpause wird über deaktivierten Knopf, dessen Beschreibung und bei Fehlern eine Wartezeit erklärt. Bei reduzierter Bewegung wird nur der Ladetext verwendet.
• Doppelte Anfragen desselben Datums beim Öffnen werden unterdrückt. Nach Abschluss eines Abrufs sind manuelle Wiederholungen nach 15 Sekunden möglich; ein anderes Datum darf sofort angefragt werden, sofern keine HTTP-Fehlerpause läuft. Der automatische Abruf bleibt bei 60 Sekunden nach Abschluss der jeweiligen geplanten Anfrage, nur bei geöffneter Ankunftsseite. Bei Fehlerpausen kann der nächste automatische Versuch später erfolgen.
• Nachweis im tatsächlichen HTTPS-Test: erfolgreiche BER-Antwort mit 361 Rohdatensätzen, Quellstand 20.10.2026 00:51:27 +02:00, Empfang etwa 01:00 Uhr. Die Differenz von mehr als drei Minuten existierte damit bereits in einer erfolgreichen Serverantwort.
• Neue Prüfungen bestanden: alter BER-Stand bei frischem Abruf, tatsächlich alter App-Abruf, manueller Klick, Ladeanzeige und aria-busy, Ablauf der Knopfsperre, HTTP-Fehler mit Wartezeit, erfolgreicher erneuter Abruf bei unverändertem BER-Stand. Java-Prüfung für Wiederholsperre, Datumswechsel und Fehlerpause. Alle bestehenden Oberflächen-/Parsertests bestanden, Update-Signatur unverändert. Android-Brücke in Chromium simuliert; kein Gerätetest dieser Version.

AUS VERSION 1.4.1 – DESIGN NACH DER FREIGEGEBENEN BILDVORLAGE
• Mittige Überschrift mit Flughafen und Wetterzeile, gelbes Taxi links, symmetrisches Zahnrad rechts.
• Taxinummer mit Bearbeitungsstift links und Überwachungsstatus rechts in derselben Karte; Trennlinie dazwischen. Beim Bearbeiten steht der Platz für Eingabe und Speichern zur Verfügung.
• Großer gelber Warteplatz, gelbe Taxi-Symbole bei Brandenburg (L) und Berlin (M), dunkle Karten mit feineren Rändern, angeglichene Schriftgrößen/Abstände und kompaktere Proportionen.
• Gebetszeiten bleiben direkt unter den beiden Taxi-Kacheln, mit Countdown unten rechts. Der Aktualisierungsstand steht in der Zielbereich-Karte; Ankunftskachel, gelbe Überwachungstaste und umrandete Aufruftest-Taste folgen darunter.
• Karten wachsen auf breiten Displays mit. Bei kleineren Displays ist die Seite vertikal scrollbar; seitliches Überlaufen wurde bei 320/360/393/412/768 Pixel Breite geprüft. Status-/Navigationshintergründe verwenden denselben dunkelblauen Farbwert wie die Seite.
• Bestehende Oberflächentests für Eingabe/Speichern, Start/Pause, Aufruf/Testaufruf, Einstellungen, Gebetszeiten/Countdown/Verlauf sowie Flugfilter/Stundenwahl erneut bestanden. Signierte APK mit identischer Update-Signieridentität erstellt. Noch kein Android-Gerätetest dieser Version.

AUS VERSION 1.4.0
• Kompaktes Gebetszeitenfeld direkt unter Brandenburg (L) und Berlin (M): İmsak, Güneş, Öğle, İkindi, Akşam, Yatsı. Berlin ist fest voreingestellt, ohne Standortfreigabe. Ausschließlich Anzeige: keine Gebetstöne, keine Vibration, keine Gebetsbenachrichtigungen.
• Unten rechts zählt die Restzeit zum nächsten Gebet sekundengenau herunter. Der Name des nächsten Gebets erscheint darüber; die entsprechende heutige Zeit ist gelb. Güneş ist der Sonnenaufgang und kein zusätzliches Gebet: Der Countdown springt von İmsak zu Öğle. Nach Yatsı wird bis zum İmsak des Folgetags gezählt, mit Zusatz „morgen“. Alle Zeiten und Daten verwenden Europe/Berlin einschließlich Sommer-/Winterzeit.
• Quelle ist die offizielle Berliner Diyanet-Tabelle (11002). Beim Öffnen wird normalerweise alle sechs Stunden nach neuen Tabellen gesucht; bei Fehlern höchstens stündlich bei offener App erneut. Geladene Tage bleiben gespeichert. Die APK enthält als Startbestand die offiziellen Tageswerte vom 20.10. bis 19.11.2026, damit die Anzeige sofort verfügbar ist. Es sind echte datierte Tabellenwerte, keine täglich wiederholten Beispielzeiten. Ohne gespeicherte Daten für das heutige Datum werden Striche und ein Hinweis angezeigt; es wird keine Restzeit aus alten Tageswerten berechnet.
• Zahnrad → Aufrufverlauf ansehen: Datum, Uhrzeit bis zur Sekunde, Taxinummer, Zielbereich und APCOA-Aufrufnummer. Erfasst wird der Zeitpunkt, zu dem die App einen echten Aufruf beim Abruf erkennt; die Quelle liefert keine gesicherte ursprüngliche Rufzeit. Die Erkennung wird unabhängig vom erfolgreichen Abspielen eines Tons gespeichert. Wiederholte Abrufe desselben Aufrufs erzeugen keinen zweiten Eintrag. Später ergänzte Zielbereiche lassen den ursprünglichen Erkennungszeitpunkt bestehen.
• Der Verlauf verwendet eine lokale SQLite-Datenbank, wird nicht automatisch gekürzt und bleibt bei App-Schließen, Handy-Neustart und Update-Installation erhalten. Ältere Einträge werden in Seiten zu 50 angezeigt. Löschen der App bzw. ihrer Daten entfernt auch den Verlauf. Erfasst werden neue Erkennungen ab dieser Version, keine rekonstruierte Vergangenheit. Testalarme und simulierte Aufrufe bleiben ausgeschlossen. Die bestehende Zurna-Datei für echte Taxi-Aufrufe bleibt unverändert.

AUS VERSION 1.3.1
• Flugkarten ungefähr halb so hoch: kleinere Abstände, kompakte Zeitzeile und gemeinsame untere Zeile für Ankunft/Gepäckband/Status. Terminal bleibt gut sichtbar oben rechts. Die Uhrzeiten werden nicht einfach auf die Hälfte verkleinert.
• Das bisherige Android-Aufklappmenü entfällt. Antippen des Zeitraums öffnet eine dunkelblaue rollbare Auswahl direkt in der Seite. Tag: Gestern, Heute oder Morgen. Stunde: 00:00 bis 23:00, in ganzen Stunden. Nach der Wahl „Fertig“ antippen. „Jetzt“ stellt die aktuelle Ansicht wieder her; „Ganzer Tag“ zeigt den gewählten Tag vollständig.
• „Ab HH:00“ filtert nach der erwarteten Ankunftsstunde, falls keine Prognose vorliegt nach der geplanten Zeit. Der Filter zeigt Flüge ab dieser Stunde bis zum Ende des gewählten Tages. Terminalfilter und Suche wirken zusätzlich. Stundenwechsel verwenden die bereits geladenen Tagesdaten; ein anderer Tag fordert dessen Daten an.
• Wetterzeile für den festen Standort Schönefeld unter der Hauptüberschrift: Temperatur und Wetterzustand mit Tag-/Nachtsymbol. Aktuelle DWD-Wetterdaten werden über Bright Sky geladen, normalerweise von der Station Berlin Brandenburg. Aktualisierung alle 15 Minuten bei geöffneter App. Wetterdaten werden gespeichert; veraltete/bei Fehlern gespeicherte Daten sind markiert. Bei fehlenden Daten erscheint keine erfundene Temperatur. Stationsname, Datenstand und Quellenhinweis finden sich in den Einstellungen.

ANKÜNFTE AM BER
Die neue Kachel öffnet die Ankunftsliste im Design des freigegebenen zweiten Entwurfs. Die Liste zeigt alle Terminals gemeinsam, einschließlich Terminal 1 und Terminal 2; ein Terminal-Umschalter ist nicht mehr vorhanden. Zeitraum über die rollbare Tag-/Stundenwahl; Standard ist „Heute · aktuell“. Die aktuelle Ansicht schließt die letzten 45 Minuten ein, damit kürzlich gelandete Flüge sichtbar bleiben. Suchfunktion nach Ort, Flugnummer oder Airline. Sortierung nach erwarteter Ankunft, bei fehlender Prognose nach geplanter Ankunft.
Jede Karte zeigt Herkunft und Flughafen-Code, Flugnummer und Airline, geplante und erwartete Zeit, Differenz in Minuten, Terminal, Ankunftsbereich, Gepäckband und den vom BER gelieferten Status. Fehlende Werte bleiben als „–“ oder „Keine Prognose“ erkennbar. Daten werden nicht aus Beispielen des Entwurfs erzeugt.
Die Datenanbindung verwendet die öffentliche Ankunftsanfrage der offiziellen BER-Webseite. Aktualisierung alle 60 Sekunden nach Abschluss eines Abrufs und nur bei geöffneter Ankunftsseite im Vordergrund. Ein manueller Aktualisieren-Knopf ist vorhanden. Gespeicherte Daten stehen beim Wiederöffnen zur Verfügung; ältere oder bei einem Fehler weiter angezeigte Daten werden gekennzeichnet. Der BER-Datenstand und der Abrufzeitpunkt werden getrennt angezeigt. Seit Version 1.4.2 löst allein ein älterer BER-Quellstand keine fehlgeschlagene App-Aktualisierung aus. Bei einem Abruffehler lässt sich die offizielle BER-Ankunftsseite im Browser öffnen.
Ein Flug wird anhand seiner ID nur einmal angezeigt. Bei mehrfachen Datensätzen verwendet die App den neueren Änderungsstand. Ein API-Datensatz ohne Prognose erzeugt keine erfundene erwartete Zeit. Terminal, Ankunftsbereich und Gepäckband sind getrennte Felder. Zeitstempel werden mit Europe/Berlin verarbeitet; Datumswechsel und Zeitzonen werden berücksichtigt. Die Zeitraumfilter beziehen sich auf den erwarteten Ankunftstag, soweit eine Prognose vorliegt, sonst auf den geplanten Tag.

TAXI-ÜBERWACHUNG
APCOA-Abstand weiterhin fünf Sekunden nach Abschluss einer Anfrage, einschließlich der bestehenden Fehlerpausen. Brandenburg (L) und Berlin (M) zählen die wartenden Rufnummern mit entsprechendem Präfix, keine bereits aufgerufenen Fahrzeuge. Die Flugliste läuft unabhängig davon. Ein echter Taxi-Aufruf wechselt aus der Flugansicht auf den gelben Aufrufbildschirm.
Zurna ist weiterhin fest als MP3 enthalten. „Aufruf testen“ zeigt nach fünf Sekunden eine als Test gekennzeichnete Simulation mit Beispielziel 1N und löst den Android-Testalarm aus. Tests ändern keine echten Wartedaten oder Aufrufhistorie. Ton und Vibration unterliegen weiterhin den Android-Benachrichtigungseinstellungen.

PRÜFUNGEN
• 1.4.0: Offizielle Berliner HTML-Tabelle tatsächlich über HTTPS abgerufen und mit dem Java-Parser verarbeitet (396 unterschiedliche Tage aus Monats-/Jahrestabelle). Startbestand stimmt mit den heutigen Werten überein. Tageswechsel, nächster İmsak, Überspringen des Sonnenaufgangs, Zeitumstellung am 25.10.2026, abgelaufene gespeicherte Tabellen und ungültige Antworten geprüft.
• 1.4.0: Chromium-Oberfläche mit simulierter Android-Brücke bei 320/393/412/768 Pixel Breite geprüft und visuell kontrolliert. Sechs Gebetszeiten ohne seitliches Überlaufen, Countdown inklusive Sekundentakt und Folgetag, keine alten Werte für ein fremdes Datum, Verlauf öffnen/zurück/blättern, Quelllink und echter Aufrufbildschirm geprüft. Bestehende Taxi-/Flugtests erneut bestanden.
• 1.4.0: Mit ausgeliefertem SQLite-Schema und Abfrage geprüft: doppelte Aufrufe, getrennte Taxinummern, Zielbereich-Aktualisierung ohne Überschreiben der ursprünglichen Zeit, Daten nach Schließen/Öffnen der Datenbank und Verlauf über mehrere Seiten. Dies ist ein SQLite-Engine-Test; die Android-Datenbankintegration muss noch auf einem Handy getestet werden.
• APK gegen Android-Plattform 35 gebaut, Signaturen v2/v3 geprüft. Zertifikat mit der bisherigen APK verglichen. Versionscode von 21 auf 22 erhöht.
• APCOA-Parser-Tests der bisherigen App bestanden.
• BER-Parser: Zuordnung der tatsächlichen Quellfelder, Zeitstempel/Zeitzone, Verspätungen einschließlich Mitternacht, fehlende Prognose, T1/T2, Ankunft/Gepäckband, Sortierung, leere und fehlerhafte Antworten, Entfernen von Abflügen und Behandlung älterer doppelter Flugdaten geprüft.
• Tatsächlicher HTTPS-Abruf mit den für die App vorgesehenen Parametern und Headern erfolgreich. Im angefragten Zeitraum kamen 401 Datensätze zurück; nach Zusammenführung doppelter Flug-IDs 400 eindeutige Flüge. Dieser Testzeitraum umfasst auch den Vorabend und bedeutet nicht „400 restliche Ankünfte heute“. Alle Datensätze wurden mit dem Java-Parser verarbeitet.
• Oberflächenprüfung im echten Chromium, Android-Schnittstelle simuliert: Startseite und Ankunftsseite bei 320/393/412/768 Pixel Breite. Kein seitliches Überlaufen und keine JavaScript-Fehler. Warteansicht, Aufruf, freie Nummer, Start/Stopp, Einstellungen und Testalarm weiterhin geprüft.
• Neue Ankunftsansicht: Terminalfilter, Suche/keine Treffer, geplante/erwartete Zeiten, Verspätung, fehlende Prognose, Ankunft und Gepäckband, Zeitraumwechsel über Mitternacht, Cache mit Fehlerhinweis, offizieller Browser-Link, Rücknavigation, Einstellungen aus der Flugansicht sowie Wechsel zu einem echten Taxi-Aufruf geprüft. Warte- und Ankunftsansicht visuell kontrolliert.

• Neue Oberflächenprüfungen: kompakte Kartenhöhe unter 190 Pixel bei 412 Pixel Breite, Stundenwahl bis 00/23 Uhr, Scrollen des Stundenrads mit automatischer Einrastung, Tastaturbedienung, Jetzt/Ganzer Tag, Schließen per Zurück und Zeitraumwechsel. Wetterzeile für Schönefeld und Darstellung auf Start-/Ankunftsseite geprüft.
• Echter Abruf der Bright-Sky-Antwort erfolgreich; Java-Wetterparser mit dem realen Datensatz der Station Berlin Brandenburg geprüft. Zusätzlich Null-/Minusgrade, Regen/Bewölkung/Nacht, Stationszuordnung und ungültige/fehlende Wetterwerte geprüft.

NOCH AM ANDROID-HANDY ZU PRÜFEN
Installation dieser Version, echte Netzwerkverbindung aus Android, tatsächliche Ton-/Vibrationsausgabe, Systemleisten, native Zurücktaste und längerer Sperrbildschirm-/Hintergrundbetrieb. Der erfolgreiche Serverabruf und Chromium-Test ersetzen keinen Android-Gerätetest.

BESTEHENDE GRENZEN
Android kann den dataSync-Vordergrunddienst durch Zeitlimits, Energiesparen oder Prozessbeendigung stoppen. Kein Push-Server und kein automatischer Dienststart allein beim Handy-Neustart. Beim Öffnen der App wird eine zuvor aktive Überwachung wieder gestartet, sofern Android dies erlaubt. Die Aktiv-Anzeige prüft den aktuell laufenden Dienst.
Die öffentlichen APCOA- und BER-Webseitenabfragen sind keine zugesicherte vertragliche API. Anbieteränderungen, blockierte Abrufe oder veraltete Quelldaten können die Anzeige beeinträchtigen. Bei Fehlern verlängert die App die Abrufpause. Es gibt keine Garantie für Echtzeit oder vollständige Taxi-Nachfrageprognosen. Eine Landung sagt nicht, wann Reisende am Taxistand eintreffen.

QUELLCODE UND BUILD
TaxiL150_Quellcode.zip enthält Java, lokale Oberfläche, Manifest, Ressourcen, Zurna, Tesla-Symbol, Buildskript und Tests; keine privaten Signierschlüssel oder Passwörter.
Voraussetzungen: JDK 17, Android-Plattform 35, Build Tools 35.0.0 und zip. ANDROID_SDK_ROOT und JAVA_HOME setzen; bash build-manual.sh. Für signierte Updates TAXI_SIGNING_KEYSTORE und TAXI_SIGNING_PASSWORD auf die vorhandene private Signieridentität setzen. Ohne diese Variablen entsteht build/aligned.apk (unsigniert). Mit Signierschlüssel entsteht build/TaxiMonitor_1.6.3_AlleTerminals.apk.
Der APCOA-Parser-Test sowie Prüfungen von Aktualisierungssperre und Countdown-Logik gehören zum Build. Für den BER-/Wetter-Parser-Test optional JSON_TEST_JAR auf die org.json-JAR setzen (geprüft mit json-20240303.jar); dann führt das Buildskript auch FlightParserTest, WeatherParserTest und PrayerParserTest aus. Die APK verwendet Androids eingebautes org.json und benötigt diese Test-JAR nicht.
Für Oberflächenprüfungen Playwright und Chromium bereitstellen: node tests/UiScriptTest.cjs, node tests/ArrivalUiTest.cjs und node tests/PrayerHistoryUiTest.cjs und node tests/FlightRefreshUiTest.cjs und node tests/CountdownUiTest.cjs und node tests/FareUiTest.cjs. Reine Tarifberechnung: node tests/FareCalculatorTest.cjs. SQLite-Prüfung: python3 tests/CallHistorySqlTest.py. Optional PLAYWRIGHT_MODULE und CHROME_BIN setzen. Screenshots werden in build/ui-qa gespeichert; diese verwenden Testdaten und sind keine Flugauskunft.

QUELLEN
BER-Ankünfte: https://ber.berlin-airport.de/de/fliegen/abfluege-ankuenfte.html
Öffentliche BER-Webseitenanfrage: https://ber.berlin-airport.de/api.flights.json
APCOA: https://tms-info.apcoa.de/external/display/98AD0D50-A726-11E2-9E96-0800200C9A66/zones/139A83F0-B534-4832-ADC0-9C1717820849
Android-Dienstzeitlimit: https://developer.android.com/develop/background-work/services/fgs/timeout
Android-Benachrichtigungskanäle: https://developer.android.com/develop/ui/views/notifications/channels

Wetterquelle: Deutscher Wetterdienst (DWD), bereitgestellt durch Bright Sky: https://brightsky.dev/ und https://brightsky.dev/docs/

Gebetszeiten Berlin (Diyanet): https://namazvakitleri.diyanet.gov.tr/tr-TR/11002/gebetszeit-fur-berlin

TARIFQUELLEN (geprüft 20.10.2026)
Amtliche Berliner Regelung § 5 zu Wartezeit und Zuschlägen: https://gesetze.berlin.de/bsbe/?docId=jlr-NNLBE00004AA2NN00000000029
Berliner Grund-/Kilometerpreise in der amtlichen Änderung: https://www.taxi-berlin.de/wp-content/uploads/2022/12/Zwoelfte-Verordnung.pdf (GVBl. Berlin 78. Jahrgang Nr. 52 vom 22.11.2022, S. 606)
Aktuelle Tarifübersicht: https://www.taxi-berlin.de/taxitarif/ (abweichende vereinfachte Wartezeitangabe für BER wird nicht übernommen)
Berlin.de Tarifinformation: https://www.berlin.de/tourismus/infos/1756978-1721039-taxi-telefonnummern-preise-regeln.html
