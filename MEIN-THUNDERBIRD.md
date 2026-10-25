# Mein Thunderbird

Dieser Branch `mein-thunderbird` ist die eigene Version von Thunderbird für Android: Mozillas App plus die eigenen
Funktionen. Er ist der Standard-Branch des Forks JustNotesa/thunderbird-android.

## Welche Branches es gibt

|                Branch                |                                                               Wozu                                                               |
|--------------------------------------|----------------------------------------------------------------------------------------------------------------------------------|
| `main`                               | Mozillas offizieller Stand, unverändert. Nur von Mozilla aktualisieren, nie eigene Arbeit darauf.                                |
| `mein-thunderbird`                   | Die eigene App: alles zusammen, was benutzt wird.                                                                                |
| `feature/...`                        | Einzelne Funktionen in Arbeit (Editor, Konten, Suche, Ordner, Signaturen). Fertige Stände werden in `mein-thunderbird` gemischt. |
| `integration/editor-accounts-search` | Der frühere Sammelbranch, aus dem `mein-thunderbird` entstanden ist. Neue Stände gehen ab jetzt nach `mein-thunderbird`.         |

## Die App bauen

Die eigene App wird als Variante „Beta“ gebaut und mit dem eigenen Schlüssel signiert:

```bash
ANDROID_HOME=~/Library/Android/sdk ~/.claude/tools/niedrig.sh ./gradlew :app-thunderbird:assembleFossBeta -Ptb.useUploadKey=false --no-daemon
```

Die fertige App liegt danach unter `app-thunderbird/build/outputs/apk/foss/beta/app-thunderbird-foss-beta.apk`.

- **App-Kennung:** `net.thunderbird.android.beta`, im Launcher „Thunderbird Beta“. Sie läuft neben dem offiziellen
  Thunderbird und neben den bisherigen Debug-Versionen (`net.thunderbird.android.debug`); Konten müssen darin neu
  eingerichtet oder übertragen werden.
- **Nicht gleichzeitig mit Mozillas offizieller Beta aus dem Play Store**, weil beide dieselbe Kennung haben.
- **Updates:** Eine neue Version lässt sich über die alte installieren, solange sie mit demselben Schlüssel signiert
  ist. Die App prüft selbst einmal am Tag (und beim Öffnen von Einstellungen → Über die App) auf GitHub, ob es ein
  neueres Release gibt, und installiert es nach Rückfrage. Das geht nur in Builds, die mit
  `-Pmein.releaseTag=<tag>` gebaut wurden (siehe „Eine neue Version veröffentlichen“); die erste Version mit dieser
  Funktion muss einmal von Hand installiert werden.

## Der Signierschlüssel

- Schlüssel: `~/.mein-thunderbird-signing/mein-thunderbird.jks`
- Passwort und Einstellungen: `~/.mein-thunderbird-signing/tb.beta.signing.properties`
- Im Arbeitsordner liegt eine Kopie der Einstellungen in `.signing/` (von git ignoriert, wird nie hochgeladen).

**Beide Dateien sichern** (z. B. auf einen USB-Stick oder in einen Passwortmanager). Geht der Schlüssel verloren,
lässt sich die App nicht mehr aktualisieren: Man müsste sie deinstallieren und alle Konten neu einrichten.

Für einen neuen Arbeitsordner die Einstellungen wieder nach `.signing/` kopieren:

```bash
mkdir -p .signing && cp ~/.mein-thunderbird-signing/tb.beta.signing.properties .signing/
```

## Was nicht geht

- **Anmeldung per Google- oder Microsoft-Konto (OAuth)** wird in dieser App sehr wahrscheinlich nicht funktionieren,
  weil diese Anbieter die Anmeldung an Mozillas Schlüssel binden. Konten mit Passwort (z. B. web.de, eigener Server)
  funktionieren normal.

## Eine neue Version veröffentlichen

Im sauberen Arbeitsordner von `mein-thunderbird` (alles committet, `.signing/tb.beta.signing.properties` vorhanden):

```bash
scripts/mein-thunderbird-release.sh --dry-run release-notes.md   # bauen, prüfen, nichts hochladen
scripts/mein-thunderbird-release.sh release-notes.md             # nach Rückfrage (y/N) veröffentlichen
```

`release-notes.md` ist der Text des Releases (Argument, Pflicht). Das Skript

1. verweigert den Lauf, wenn der Branch nicht `mein-thunderbird` oder der Ordner nicht sauber ist,
2. bestimmt den nächsten Tag `mein-thunderbird-JJJJ-MM-TT`, bei weiteren Versionen am selben Tag `-2`, `-3`, …
   (aus den Releases und den Tags im Fork),
3. baut die Beta-Variante mit `-Pmein.releaseTag=<tag>` und prüft die Signatur mit `apksigner verify --print-certs`,
4. legt die APK als `release-preview/<tag>.apk` neben den Arbeitsordnern ab,
5. zeigt Tag und Notizen, fragt nach, pusht dann `mein-thunderbird` zum Fork und erstellt das Release als „Latest“.

**Wie die App Releases findet:** Die eingebaute Aktualisierung liest `releases/latest` des Forks. Damit sie eine
Version erkennt, muss der Tag `mein-thunderbird-JJJJ-MM-TT[-N]` heißen, das Release als **Latest** markiert und keine
Vorabversion sein, und als Datei die APK `<tag>.apk` enthalten. Ob es neuer ist, entscheidet Datum, dann N. Die App
prüft die heruntergeladene Datei per SHA-256 (aus GitHubs Digest) und übergibt sie Android zum Installieren. Ein Build
ohne `-Pmein.releaseTag=<tag>` kennt seine eigene Version nicht; in ihm ist die Aktualisierung abgeschaltet
(Debug- und lokale Builds).

## Mozillas Updates übernehmen

Etwa einmal im Monat, und immer bei Sicherheitskorrekturen:

```bash
git fetch origin main:main
```

aktualisiert `main` auf Mozillas Stand, ohne einen Arbeitsordner zu wechseln. Dann im Arbeitsordner von
`mein-thunderbird`:

```bash
git merge main
```

Gibt es Konflikte, betreffen sie meist Stellen, an denen Mozilla und die eigenen Funktionen dieselben Dateien ändern
(vor allem den Mail-Editor). Konflikte lösen, Tests der betroffenen Module laufen lassen, die App bauen und kurz
ausprobieren, dann `git push fork mein-thunderbird`.

Einfacher: Claude bitten, „Mozillas Updates in mein-thunderbird zu übernehmen“; das sind genau diese Schritte.

## Eine fertige Funktion aufnehmen

Im Arbeitsordner von `mein-thunderbird`:

```bash
git merge feature/<name>
```

Danach bauen, ausprobieren und `git push fork mein-thunderbird`.
