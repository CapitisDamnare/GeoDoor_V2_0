## GeoDoor Android App

**Diese Android App sollte in Kombination mit dem Java GeoDoorServer Programm benutzt werden.**

**Link: https://github.com/CapitisDamnare/GeoDoorServer2**

## Vision
Die App soll sich mit einem Server des "Smart Homes" verbinden und den aktuellen Standort ermittleln.
Ist man auf dem Weg nach Hause so soll die App automatisch das elektrische Tor öffnen, sodass man sofort einparken kann und nicht
gefühlte 20min warten muss bis das Tor offen ist. Anschließend schließt sich das Tor wieder automatisch.

## Ziel dieses Projektes
Das Ziel dieses Projektes ist es eine App zu entwickeln welche die derzeitigen Koordinaten via GPS ermittelt und die Distanz
zum "Smart Home" berechnet. Anschließend muss eine Verbindung zum Server des "Smart Homes" aufgebaut werden und das Signal zum Öffnen
des elektrischen Tors gegeben werden.

## Master Branch
Derzeit wurde eine Android App erstellt welche sich mit dem GeoDoor Server verbindet.
Die erfolgreiche Verbindung sowie der Status des Tores (offen oder geschlossen) wird angezeigt.
Zusätzlich ist es möglich eine Tür (elektrischer Öffner) an zu steuern.
Der Service der App soll bei Bedarf permanent im Hintergrund arbeiten dürfen ohne das eine Anzeige (Activity) gestarte werden muss.
Im Auto Mode wird automatische eine Nachricht gesendet, sobal man sich dem zu Hause einen einstellbaren Wert nähert.
