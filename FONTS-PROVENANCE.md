# Font Provenance

This file documents the provenance of every font shipped as a `type: font` plugin
in this repository. Each font plugin is a data-only APK that ships exactly **one
static Regular TTF** as an asset. None of the fonts is an instanced (variable-font)
export — every upstream already shipped a static Regular, which we select unchanged.

- **SPDX license (all fonts):** `OFL-1.1` (SIL Open Font License 1.1)
- **Acquisition date (all fonts):** 2026-06-14
- **Volume (per font):** one static Regular TTF
- **Processing / filter:** static Regular selected from the upstream release; no
  reinstancing, no subsetting, no renaming (see Risk Register for RFN constraints)

## Fonts

### EB Garamond

| Field | Value |
|---|---|
| Family name | EB Garamond |
| Package | `com.komgareader.font.ebgaramond` |
| Upstream URL | https://github.com/octaviopardo/EBGaramond12 |
| Acquisition note | Acquired via `google/fonts` `ofl/ebgaramond`; the static Regular matches the distro package `fonts-ebgaramond-extra` 0.016+git20210310.42d4f9f2-1 |
| Pin | `google/fonts` `ofl/ebgaramond` |
| License | OFL-1.1 |
| Volume | one static Regular TTF |
| Processing | static Regular selected; not instanced |
| Acquisition date | 2026-06-14 |
| Reserved Font Name (RFN) | No |

### Lora

| Field | Value |
|---|---|
| Family name | Lora |
| Package | `com.komgareader.font.lora` |
| Upstream URL | https://github.com/cyrealtype/Lora-Cyrillic |
| Pin | tag `v3.021` |
| License | OFL-1.1 |
| Volume | one static Regular TTF |
| Processing | static Regular selected; not instanced |
| Acquisition date | 2026-06-14 |
| Reserved Font Name (RFN) | **Yes** — "Lora" is a reserved font name; derivatives must not reuse the name |

### Merriweather

| Field | Value |
|---|---|
| Family name | Merriweather |
| Package | `com.komgareader.font.merriweather` |
| Upstream URL | https://github.com/SorkinType/Merriweather |
| Pin | commit `4481226b336843648b5b2ee64f75737f262ded15` |
| License | OFL-1.1 |
| Volume | one static Regular TTF |
| Processing | static Regular selected; not instanced |
| Acquisition date | 2026-06-14 |
| Reserved Font Name (RFN) | **Yes** — "Merriweather" is a reserved font name; derivatives must not reuse the name |

### Source Serif 4

| Field | Value |
|---|---|
| Family name | Source Serif 4 |
| Package | `com.komgareader.font.sourceserif` |
| Upstream URL | https://github.com/adobe-fonts/source-serif |
| Pin | release `4.005R` |
| License | OFL-1.1 |
| Volume | one static Regular TTF |
| Processing | static Regular selected; not instanced |
| Acquisition date | 2026-06-14 |
| Reserved Font Name (RFN) | **Yes** — Adobe Reserved Font Name "Source"; derivatives must not reuse the name "Source" |

### Atkinson Hyperlegible Next

| Field | Value |
|---|---|
| Family name | Atkinson Hyperlegible Next |
| Package | `com.komgareader.font.atkinson` |
| Upstream URL | https://github.com/googlefonts/atkinson-hyperlegible-next |
| Pin | commit `7925f50f649b3813257faf2f4c0b381011f434f1` |
| License | OFL-1.1 |
| Volume | one static Regular TTF |
| Processing | static Regular selected; not instanced |
| Acquisition date | 2026-06-14 |
| Reserved Font Name (RFN) | No (the name is a trademark — no renaming is intended in any case) |

## Risk Register

- **OFL-1.1 ↔ app license (AGPL-3.0):** A font is a separate work from the
  application. The SIL Open Font License coexists with the app's AGPL-3.0 license;
  shipping these fonts as separate data-only plugin APKs does not relicense the app,
  nor does the app's license affect the fonts.
- **Reserved Font Name (RFN) — Lora, Merriweather, Source Serif 4:** These fonts
  carry an OFL Reserved Font Name. We ship the upstream Regular **unmodified**: no
  derivative is created and the font is **not renamed**. Any future modification of
  these fonts must not reuse the reserved name(s) ("Lora", "Merriweather", "Source").
- **Atkinson Hyperlegible Next:** The name is a trademark. We do not rename or create
  a derivative; the upstream Regular is shipped unmodified.

Letzte Komplettrevision: 2026-06-14 (P3 Font-Plugins)
