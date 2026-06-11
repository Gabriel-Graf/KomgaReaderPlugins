# Komga Reader Plugins

Offizielles Plugin-Sammel-Repo für den [Komga E-Ink Reader](https://github.com/Gabriel-Graf/komga-reader).

Die App liest `repo.json` als Plugin-Index. Im **Plugins-Tab → „Plugins entdecken"** erscheinen die
hier gelisteten Plugins; per Tap werden sie heruntergeladen, ihre Signatur gegen den Eintrag verifiziert
und über den OS-Installer installiert.

## Index-Format (`repo.json`)

```json
{
  "name": "Komga Reader Plugins",
  "plugins": [
    {
      "packageName": "com.example.plugin",
      "name": "Anzeigename",
      "description": "Kurzbeschreibung",
      "type": "source | preset",
      "abiVersion": 1,
      "versionCode": 1,
      "versionName": "0.1.0",
      "apkUrl": "plugins/datei.apk",
      "fingerprint": "AB:CD:… (Cert-SHA-256, Doppelpunkte/Case egal)"
    }
  ]
}
```

- `apkUrl` ist absolut oder **relativ zur `repo.json`** (z. B. `plugins/foo.apk`).
- `fingerprint` = SHA-256 des Signaturzertifikats des APK. Stimmt es beim Download nicht, wird **nicht** installiert.
- `versionCode` höher als das installierte Paket ⇒ die App zeigt **Update**.
- `abiVersion` außerhalb der App-Spanne ⇒ Eintrag wird als **inkompatibel** markiert.

## Aktuelle Plugins

| Plugin | Typ | Version | Paket |
|---|---|---|---|
| Kindle E-Ink Presets | preset | 0.1.0 | `com.komgareader.preset.kindle` |
| Kavita | source | 0.1.0 | `com.komgareader.plugin.kavita` |

> Hinweis: Die APKs sind aktuell mit dem Android-Debug-Keystore signiert (frühe Entwicklung).
> Für ein Release werden sie mit einem stabilen Schlüssel neu signiert und der `fingerprint` im Index angepasst.
