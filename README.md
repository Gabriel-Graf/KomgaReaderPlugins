# Komga Reader Plugins

The official plugin collection for the [Komga E-Ink Reader](https://github.com/Gabriel-Graf/komga-reader).

This is a **monorepo**: the source for every officially supported plugin lives here as a Gradle
subproject, all built by one CI workflow and published together. The app reads `repo.json` as the
plugin index — under **Plugins tab → "Discover plugins"** the listed plugins appear; tapping one
downloads it, verifies its signature against the index entry, and installs it via the OS installer.

## What's here

| Plugin | Package | Type | What it does |
|---|---|---|---|
| `komga-kavita-source` | `com.komgareader.plugin.kavita` | source | Kavita server as a reading source (code plugin) |
| `komga-eink-preset-kindle` | `com.komgareader.preset.kindle` | preset | Muted E-Ink colour profiles for other devices |
| `komga-reader-preset-eink` | `com.komgareader.preset.reader.eink` | reader_preset | Reader setting presets tuned for E-Ink |
| `komga-ui-pack-aurora` | `com.komgareader.uipack.aurora` | ui_pack | Modern mobile look (Slate + Cobalt, floating nav) for LCD |
| `komga-lang-{es,fr,it}` | `com.komgareader.lang.*` | language | Spanish / French / Italian UI translations |
| `komga-ui-pack-sample` | `com.komgareader.uipack.sample` | — | A minimal UI-pack **template** for plugin authors (not indexed) |

All but the Kavita source are **data-only** plugins: an APK with no code (`android:hasCode="false"`),
carrying a single JSON asset and discovery metadata in its manifest. Kavita is a **code** plugin —
it implements the source contract and links the shaded `plugin-sdk` as `compileOnly`.

## Index format (`repo.json`)

```json
{
  "name": "Komga Reader Plugins",
  "plugins": [
    {
      "packageName": "com.example.plugin",
      "name": "Display name",
      "description": "Short description",
      "type": "source | preset | language | reader_preset | ui_pack",
      "abiVersion": 1,
      "versionCode": 1,
      "versionName": "0.1.0",
      "apkUrl": "https://.../plugin.apk",
      "fingerprint": "AA:BB:...:FF"
    }
  ]
}
```

`apkUrl` may be absolute or relative to `repo.json`. `fingerprint` is the signing cert's SHA-256;
the app pins it (trust-on-first-use) and refuses an APK whose signature doesn't match.

## Building locally

Requires JDK 17 + Android SDK (API 34). The Kavita code plugin links a vendored shaded SDK jar
(`libs/plugin-sdk-0.1.0.jar`, the host project's `:plugin-sdk` output).

```bash
./gradlew assembleDebug                       # build every plugin (debug APKs)
./gradlew :komga-ui-pack-aurora:assembleDebug # one plugin
./gradlew :komga-kavita-source:test           # run the Kavita plugin's unit tests
```

Debug APKs are signed with your local Android debug keystore (`~/.android/debug.keystore`).

## Releasing (CI)

Pushing a `v*` tag triggers `.github/workflows/release.yml`, which:

1. builds and signs every plugin's release APK,
2. publishes a GitHub **Release** with the APKs as assets,
3. rewrites `repo.json` (via `tools/update-repo-for-release.mjs`) to point each entry at the
   release asset, stamps the signing fingerprint and version, and commits it to `main`.

This replaces committing built APKs. The workflow needs four repository secrets
(`PLUGIN_KEYSTORE_BASE64`, `PLUGIN_KEYSTORE_PASSWORD`, `PLUGIN_KEY_ALIAS`, `PLUGIN_KEY_PASSWORD`) —
see the comments at the top of the workflow. Use the same keystore the current APKs were signed
with to keep the existing fingerprint (so installed plugins keep updating).

> APKs are no longer committed to the repo. They are built and signed by CI and published as
> **GitHub Release assets**; `repo.json` points at those assets (see the release workflow above).

## Authoring a new plugin

Copy `komga-ui-pack-sample` (data-only) or `komga-kavita-source` (code), give it a unique package
name, add it to `settings.gradle.kts`, and add an entry to `repo.json`. Plugin types, the ABI
contract, and the capability model are documented in the host repo
(`docs/ARCHITECTURE.md` → Plugins, and `docs/superpowers/specs/`). Data-only plugins declare their
category via manifest metadata (`com.komgareader.plugin.DATA_CATEGORY` / `DATA_ASSET` /
`ABI_VERSION`); source plugins declare an entry class (`com.komgareader.plugin.SOURCE`).

## License

Plugins that link the Komga Reader SDK inherit its **AGPL-3.0-or-later** licensing. See the host
repository for details.
