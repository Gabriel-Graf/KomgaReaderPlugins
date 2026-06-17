# Calibre Source Plugin

Connects the Komga-Reader to a **Calibre Content Server** via its `/ajax/` JSON API.
Books are grouped into Series → volumes by Calibre's `series` metadata.

> **Alternative without this plugin:** Calibre also speaks OPDS. You can add it in the
> reader as Server → OPDS pointing at `http://<host>:<port>/opds`. This plugin exists
> because Calibre's `/ajax/` API gives proper series grouping and richer metadata than
> its OPDS feed.

## Server setup (Calibre Content Server)

Run the Calibre **Content Server** (not calibre-web):

- Desktop calibre: *Connect/share → Start Content Server*, or
- Headless: `calibre-server /path/to/library --port 8080`
- Optional auth: add `--enable-auth` and create a user (`--manage-users`).

The default port is 8080. Confirm `http://<host>:8080/ajax/library-info` returns JSON.

## Connect from the reader

1. Settings → **Server hinzufügen** → **Plugin** → install **Calibre** from the repo,
   confirm the trust (TOFU) dialog.
2. Fill the config form:
   - **Server-URL** — `http://<host>:<port>` (e.g. `http://192.168.1.10:8080`)
   - **Benutzername** / **Passwort** — only if you started the server with `--enable-auth`
   - **Bibliothek** — leave blank to use the server's default library; set it to a key
     from `/ajax/library-info`'s `library_map` for a specific one.

## Quirks & limitations

- **Content Server only.** calibre-web is a different project and does not serve `/ajax/`.
- **Whole-file reading.** Calibre has no page streaming; books are downloaded whole, then
  rendered by the reader (EPUB/PDF/CBZ/CBR). Readable formats are picked in the order
  EPUB > PDF > CBZ > CBR; books with none of these are hidden.
- **Series cover** = the cover of the series' first volume (Calibre has no per-series cover).
- **Read-only — progress is NOT synced to Calibre yet.** Reading position stays on the
  device. Calibre's last-read API is EPUB-CFI based (not page-based), so a future version
  will need a CFI↔page bridge. This is the planned next step.

## Verified against

_(updated by the E2E verification task on 2026-06-17)_
- Docker image: `lscr.io/linuxserver/calibre:latest` (calibre 9.9)
- Demo library:
  - **Demo Saga #1** — *Pride and Prejudice* by Jane Austen (Gutenberg id 1342, EPUB, 24 MB)
  - **Demo Saga #2** — *Bureaucracy* by Honoré de Balzac (Gutenberg id 1343, EPUB, 261 KB)
  - **Standalone** — *Alice's Adventures in Wonderland* by Lewis Carroll (Gutenberg id 11, EPUB, 184 KB)
- Series mapping: books 1+2 tagged `series:"Demo Saga"` with `series_index` 1 and 2 via `calibredb set_metadata`
- Verified: browse (returns "Demo Saga" series tile), books (sorted volumes Pride #1, Bureaucracy #2), downloadFile (24 MB EPUB bytes), coverBytes (229 KB JPEG cover from Calibre)
