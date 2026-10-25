# Mein Thunderbird

**Mein Thunderbird** ist eine eigene Version von Thunderbird für Android: Mozillas App, ergänzt um zusätzliche
Funktionen. Sie ist mit einem eigenen Schlüssel signiert und erscheint auf dem Handy als **„Thunderbird Beta“**
(`net.thunderbird.android.beta`). Dieser Fork ist kein offizielles Mozilla-Produkt; der unveränderte Stand von
Mozilla liegt im Branch `main`, die eigene App im Branch `mein-thunderbird` (siehe [MEIN-THUNDERBIRD.md](./MEIN-THUNDERBIRD.md)).

### Herunterladen

**[Neueste Version herunterladen](https://github.com/JustNotesa/thunderbird-android/releases/latest)**: die
APK-Datei im Release öffnen und installieren (Android fragt einmal nach „Unbekannte Apps installieren“).

### Was diese Version zusätzlich kann

#### Mail schreiben (Editor)
- **Formatierter Text:** fett, kursiv, unterstrichen, durchgestrichen, Farben, Links, Listen, Zitate, Einrücken und
  Ausrichtung, bedient über eine Leiste über der Tastatur.
- **Schriftgröße und Schriftart** lassen sich in Nachrichten und in Signaturen wählen.
- **Rückgängig/Wiederholen**, sauberes Einfügen und automatisches Speichern als Entwurf. Beim erneuten Öffnen eines
  Entwurfs bleibt die Formatierung erhalten.
- **Bilder im Text:** Bilder lassen sich mitten in die Nachricht einfügen; die Anzeigebreite wird durch Wischen
  über das Bild eingestellt.
- **Zitat in Antworten bearbeiten:** Das Zitat einer HTML-Mail kann samt Formatierung direkt geändert werden.
- **HTML-Code bearbeiten:** Der Nachrichtentext kann als HTML-Code geöffnet werden; die App sagt vorher, ob sich der
  HTML-Text ohne Verlust als formatierter Text zeigen lässt.
- **Zoom mit zwei Fingern** im Editor, ein ruhigeres, helleres Aussehen mit feinen Linien zwischen den Feldern.

#### Nachrichten lesen
- **Zwei-Finger-Zoom** für den Text einer Nachricht.
- **Ruhigere Nachrichtenliste** mit abgerundeten Zeilen ohne Trennlinien.
- **Ordner und Konto einer Nachricht** werden als Label hinter dem Betreff gezeigt; ein Tipp führt zum Posteingang
  bzw. Ordner zurück.

#### Konten einrichten und übertragen
- **Alle Wege, ein Konto hinzuzufügen,** auf der ersten Startseite (E-Mail-Konto,
  QR-Code, Thundermail, aus Passwortmanager einfügen).
- **Konten auf ein neues Gerät übertragen** (Einstellungen → „Konten übertragen“): per verschlüsseltem QR-Code, über
  das WLAN, über den Hotspot des sendenden Geräts oder als Text für einen Passwortmanager. Vor der Übertragung von
  Passwörtern wird nachgefragt, Bildschirmfotos der Übertragungsseiten sind nur dort blockiert, wo Geheimnisse
  sichtbar sind.
- Nach dem Import zeigt die App, **wie er ausgegangen ist**, und prüft die Verbindungen. Ein schon eingerichtetes Konto
  wird nicht doppelt angelegt; übernommene Konten bekommen jeweils eine eigene Farbe.
- **Serverdaten wiederverwenden:** Hat ein Konto derselben Domain schon passende Servereinstellungen, werden sie
  übernommen.
- **Mehrere Konten auf einmal löschen.**

#### Suche
- **Server-Suche in Stufen:** erst lokal, dann über Ordner und Konten hinweg auf dem Server, zuletzt auch in den
  Nachrichteninhalten; mit sichtbarem Fortschritt und Fehlermeldungen.
- **Status der Server-Suche:** zeigt, ob gerade verbunden oder auf Antwort gewartet wird und was die Suche aufhält;
  sie lässt sich anhalten, fortsetzen und pausiert, während eine Nachricht gelesen wird.
- **Alle Wörter finden:** Treffer müssen jedes Wort der Suchanfrage enthalten; die Suchanfrage lässt sich in der
  Trefferliste ändern, zuletzt benutzte Suchen werden vorgeschlagen.
- **Ordner im Ergebnis:** In den Treffern steht der Ordner jeder Nachricht, der Ordner lässt sich aus der Nachricht
  heraus öffnen; eine aus einem Ordner gestartete Suche kann auf diesen Ordner beschränkt werden.
- Ordner laden ihre Nachrichten in wachsenden Schritten beim Scrollen und nennen einen Grund, wenn das Laden scheitert.

#### Ordner und Posteingang
- **Ordnerverwaltung auf dem Handy:** Ordner auf dem Server anlegen, umbenennen, per Ziehen verschieben und löschen
  (samt Unterordnern); Unterordner erscheinen als Baum, das Konto lässt sich in der Verwaltung wechseln. Scheitert
  der Server, wird das gemeldet.
- **Vereinheitlichte Ordner:** Gemeinsamer Posteingang als Standard, dazu gemeinsame Spezialordner (z. B. Gesendet,
  Entwürfe, Papierkorb) im Ordner-Menü; Spezialordner werden auch am Namen erkannt, wenn der Server sie nicht
  kennzeichnet.
- **Seitenmenü:** der gewählte Ordner wird sichtbar gemacht, der Konto-Avatar öffnet dessen Posteingang.
- **Speicher:** Anzeige, wie viel Platz ein Konto auf dem Gerät und (bei IMAP-Servern) auf dem Server belegt.

#### Signaturen
- **Mehrere benannte Signaturen pro Konto**, Auswahl beim Schreiben einer Nachricht.
- **Signaturen formatieren** (Schrift, Größe, Vorschau) und aus der Auswahl per langem Druck bearbeiten; eine
  HTML-Signatur kann auch nur für die aktuelle Nachricht angepasst werden.

#### Updates
- **Eingebaute Aktualisierung:** Die App fragt einmal am Tag und beim Öffnen von Einstellungen → Über die App bei
  GitHub nach einer neueren Version, zeigt eine Benachrichtigung, lädt die APK herunter, prüft sie per SHA-256 und
  übergibt sie an den Installer von Android. Dafür muss einmal „Unbekannte Apps installieren“ für die App erlaubt
  werden. Mozillas App hat keine solche Funktion und verlässt sich auf Play Store bzw. F-Droid.

### Bekannte Einschränkungen
- **Anmeldung mit Google oder Microsoft (OAuth)** funktioniert mit dem eigenen Schlüssel sehr wahrscheinlich nicht,
  weil diese Anbieter die Anmeldung an Mozillas Schlüssel binden. Konten mit Passwort (z. B. web.de, eigener Server)
  sind nicht betroffen.
- **Die erste Version mit eingebauter Aktualisierung muss einmal von Hand installiert werden**; erst von da an
  findet und lädt die App neue Versionen selbst; die Installation bestätigt man nur noch im Dialog von Android.
  Voraussetzung ist immer derselbe Signierschlüssel.
- Die App läuft neben dem offiziellen Thunderbird, kann aber nicht gleichzeitig mit Mozillas Beta aus dem Play Store
  installiert sein (gleiche App-Kennung). Konten müssen neu eingerichtet oder übertragen werden.

---

# Thunderbird for Android

<a href="https://play.google.com/store/apps/details?id=net.thunderbird.android&referrer=utm_campaign%3Dandroid_metadata%26utm_medium%3Dweb%26utm_source%3Dgithub.com%26utm_content%3Dbadge" target="_blank"><img src="./docs/assets/get-it-on-play.png" alt="Get it on Google Play" height="28"></a>
<a href="https://f-droid.org/packages/net.thunderbird.android"><img src="./docs/assets/get-it-on-fdroid.png" alt="Get it on F-Droid" height="28"></a>
<a href="https://apps.obtainium.imranr.dev/"><img src="./docs/assets/get-it-on-obtainium.png" alt="Get it on Obtainium" height="28"></a>
[![Latest release](https://img.shields.io/github/release/thunderbird/thunderbird-android.svg?style=for-the-badge&filter=THUNDERBIRD_*&logo=thunderbird)](https://github.com/thunderbird/thunderbird-android/releases/latest)
[![Latest beta release](https://img.shields.io/github/release/thunderbird/thunderbird-android.svg?include_prereleases&style=for-the-badge&label=beta&filter=THUNDERBIRD_*b*&logo=thunderbird)](https://github.com/thunderbird/thunderbird-android/releases)

Thunderbird for Android is a powerful, privacy-focused email app. Effortlessly manage multiple email accounts from one app, with a Unified Inbox option for maximum productivity. Built on open-source technology and supported by a dedicated team of developers alongside a global community of volunteers, Thunderbird never treats your private data as a product.

Thunderbird for Android is based on K-9 Mail, which comes with a rich history of success and functionality in open source email.

## Download

Thunderbird for Android can be downloaded from a couple of sources:

- Thunderbird on [Google Play](https://play.google.com/store/apps/details?id=net.thunderbird.android&referrer=utm_campaign%3Dandroid_metadata%26utm_medium%3Dweb%26utm_source%3Dgithub.com%26utm_content%3Dlink) or [F-Droid](https://f-droid.org/packages/net.thunderbird.android)
- Thunderbird Beta on [Google Play](https://play.google.com/store/apps/details?id=net.thunderbird.android.beta&referrer=utm_campaign%3Dandroid_metadata%26utm_medium%3Dweb%26utm_source%3Dgithub.com%26utm_content%3Dlink) or [F-Droid](https://f-droid.org/packages/net.thunderbird.android.beta)
- [Github Releases](https://github.com/thunderbird/thunderbird-android/releases)
- [FFUpdater](https://f-droid.org/packages/de.marmaro.krt.ffupdater/) allows installing the latest versions from ftp.mozilla.org
- [Obtainium](https://obtainium.imranr.dev/): Use "Filter release title by regular expression" under "Additional options" with `Thunderbird`. More info in [Complex Obtainium Apps list](https://apps.obtainium.imranr.dev/).

By using Thunderbird for Android Beta, you have early access to current development and are able to try new features earlier.

Check out the [Release Notes](https://github.com/thunderbird/thunderbird-android/releases) to find out what changed in each version of Thunderbird for Android.

The SHA-256 fingerprints for our signing certificates are available in [SECURITY.md](./SECURITY.md#verifying-fingerprints).

## Need Help? Found a bug? Have an idea? Want to chat?

If the app is not behaving like it should, or you are not sure if you've encountered a bug:

- Check out our [knowledge base](https://support.mozilla.org/products/thunderbird-android) and [frequently asked questions](https://support.mozilla.org/kb/thunderbird-android-8-faq)
- Ask a question on our [support forum](https://support.mozilla.org/en-US/questions/new/thunderbird-android)

If you are certain you've identified a bug in Thunderbird for Android and would like to help fix it:

- File an issue on [our GitHub issue tracker](https://github.com/thunderbird/thunderbird-android/issues)

If you have an idea how to improve Thunderbird for Android:

- Tell us about and vote on your feature ideas on [connect.mozilla.org](https://connect.mozilla.org/t5/ideas/idb-p/ideas/label-name/thunderbird%20android).
- Join the discussion about the latest changes in the [Thunderbird Android Beta Topicbox](https://thunderbird.topicbox.com/groups/android-beta).

The Thunderbird Community uses Matrix to communicate:

- General chat about Thunderbird for Android and K-9 Mail: [#tb-android:mozilla.org](https://matrix.to/#/#tb-android:mozilla.org)
- Development and other ways to contribute: [#tb-android-dev:mozilla.org](https://matrix.to/#/#tb-android-dev:mozilla.org)
- Reach the broader Thunderbird Community in the [community space](https://matrix.to/#/#thunderbird-community:mozilla.org)

## Roadmap

To learn more about all the wonderful things planned for this year please see our
[roadmap](https://github.com/orgs/thunderbird/projects/19/views/1). The core team's day to day activities are additionally
tracked in our [sprint board](https://github.com/orgs/thunderbird/projects/20/views/1).

## Contributing

We welcome contributions from everyone.

- Development: Have you done a little bit of Kotlin? The [CONTRIBUTING](docs/CONTRIBUTING.md) guide will help you get started
- Translations: Do you speak a language aside from English? [Translating is easy](https://hosted.weblate.org/projects/thunderbird/thunderbird-android/) and just takes a few minutes for your first success.
- We have [a number of other contribution opportunities](https://blog.thunderbird.net/2024/09/contribute-to-thunderbird-for-android/) available.
- Thunderbird is supported solely by financial contributions from users like you. [Make a financial contribution today](https://www.thunderbird.net/donate/mobile/?form=tfa)!
- Make sure to check out the [Mozilla Community Participation Guidelines](https://www.mozilla.org/about/governance/policies/participation/).

### Engineering Process

We use a structured engineering process to propose, decide, and deliver technical changes. This includes:
- [Requests for Comments (RFCs)](docs/engineering/rfcs/README.md) for technical direction.
- [Technical Designs](docs/engineering/technical-designs/README.md) for implementation details.
- [Architecture Decision Records (ADRs)](docs/engineering/adr/README.md) for durable architectural decisions.

You can find more information in the [`docs/engineering`](docs/engineering) directory.

We encourage team members and contributors to read through our engineering documentation to understand the
processes and decisions that have shaped this project so far.

## K-9 Mail

In June 2022, [K-9 Mail joined the Thunderbird family](https://k9mail.app/2022/06/13/K-9-Mail-and-Thunderbird.html)
as the foundation for Thunderbird on Android. Since then, we’ve been updating both apps to give
users the same solid experience, so it’s normal to notice that K-9 Mail and Thunderbird look and
feel nearly identical. They’re built on the same code, and that’s intentional. You'll notice some
features are selectively enabled for Thunderbird as opposed to K-9 Mail, usually when they are
simply a better fit for Thunderbird (like the import from K-9 functionality).

If you prefer the robot dog and would like to keep K-9 Mail around, you can find it here:

- [K-9 Mail on Google Play](https://play.google.com/store/apps/details?id=com.fsck.k9&utm_source=thunderbird-android-github&utm_campaign=download-section)
- [K-9 Mail on F-Droid](https://f-droid.org/packages/com.fsck.k9/)
- [K-9 Mail on Obtainium](https://obtainium.imranr.dev/) (use `K-9 Mail` as filter, see [notes](#download) above)

## Forking

If you want to use a fork of this project please ensure that you replace the OAuth client setup in the `app-k9mail/src/{debug,release}/kotlin/app/k9mail/auth/K9OAuthConfigurationFactory.kt` and `app-thunderbird/src/{debug,daily,beta,release}/kotlin/net/thunderbird/android/auth/TbOAuthConfigurationFactory.kt` with your own OAuth client setup and ensure that the `redirectUri` is different to the one used in the main project. This is to prevent conflicts with the main app when both are installed on the same device.

## License

Thunderbird for Android is licensed under the [Apache License, Version 2.0](LICENSE).
