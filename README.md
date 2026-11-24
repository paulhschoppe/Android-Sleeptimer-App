# Android Sleeptimer

Minimaler Sleeptimer für Android: Stunden, Minuten und Sekunden (in 5er-Schritten) an Rädern einstellen wie in der Samsung-Uhr → **Start** → nach Ablauf pausiert die laufende Musik (Spotify, YouTube Music usw.).
Der zuletzt eingestellte Wert bleibt gespeichert, beim nächsten Öffnen reicht ein Tipp auf **Start**.

- Keine Internetverbindung, keine Bibliotheken – nur das Android-SDK
- Timer läuft über den System-Wecker weiter, auch wenn die App geschlossen oder der Bildschirm gesperrt ist
- Mindestversion: Android 13

## Projektstruktur

```
app/src/main/
├── AndroidManifest.xml
└── java/de/paul/sleeptimer/
    ├── MainActivity.kt    # Bildschirm: Einstellräder, Countdown, Start/Stopp
    ├── Timer.kt           # Timer starten/stoppen, Dauer speichern (AlarmManager + SharedPreferences)
    ├── TimerReceiver.kt   # wird bei Ablauf aufgerufen → Musik pausieren
    └── Media.kt           # simuliert die Pause-Medientaste
```

## Einmalige Einrichtung (ohne Android Studio)

**Windows:** im Projektordner `setup.cmd` doppelklicken (oder im Terminal `.\setup.cmd`).

Das Skript lädt und installiert alles Nötige nach `%USERPROFILE%\Android`:
JDK 17, Android SDK Command-line Tools, `platform-tools` (adb), Platform 35, Build-Tools 35.0.0.
Es setzt `JAVA_HOME`, `ANDROID_HOME` und den `PATH` für den Benutzer und legt `local.properties` an.
Keine Adminrechte nötig. Bereits Installiertes wird übersprungen, abgebrochene Downloads werden fortgesetzt –
bei Fehlern einfach nochmal starten. Anderer Ordner: `.\setup.cmd -SdkRoot D:\Android`.

Danach ein **neues** Terminal öffnen, damit die Umgebungsvariablen greifen.

<details>
<summary>Manuell (z. B. auf dem Mac)</summary>

1. **JDK 17** installieren (z. B. [Eclipse Temurin](https://adoptium.net)).
2. **Android SDK Command-line Tools** von <https://developer.android.com/studio#command-line-tools-only> laden,
   entpacken nach `~/Android/cmdline-tools/latest/`.
3. Pakete installieren:
   ```
   ~/Android/cmdline-tools/latest/bin/android --sdk=$HOME/Android sdk install platform-tools platforms/android-35 build-tools/35.0.0
   ```
   (ältere Tools ohne `android`: `sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"`)
4. Im Projektordner `local.properties` anlegen (wird nicht eingecheckt): `sdk.dir=/Users/<name>/Android`
5. `~/Android/platform-tools` zum `PATH` hinzufügen (dort liegt `adb`).

</details>

## Am Handy (Samsung S22)

Einstellungen → Telefoninfo → Softwareinformationen → 7× auf **Buildnummer** tippen.
Dann unter **Entwickleroptionen** → **USB-Debugging** einschalten.

## Bauen & installieren

Handy per USB anschließen, dann im Projektordner:

```
./gradlew installDebug        # Mac/Linux
gradlew.bat installDebug      # Windows
```

Der erste Lauf lädt Gradle und das Android-Plugin herunter (dauert ein paar Minuten).

**Kabellos:** Entwickleroptionen → *Kabelloses Debugging* → *Gerät mit Kopplungscode koppeln*, dann
`adb pair <IP:Port>` und `adb connect <IP:Port>` (PC und Handy im selben WLAN).

**Logs ansehen:** `adb logcat`

## Tipp für Samsung

Damit One UI den Timer nicht ausbremst: App lange drücken → App-Info → Akku → **Nicht eingeschränkt**.
