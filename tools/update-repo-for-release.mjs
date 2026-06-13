#!/usr/bin/env node
// Rewrites repo.json for a release: points every plugin's apkUrl at the GitHub
// Release asset for the given tag, and stamps the signing-key fingerprint + version.
//
// repo.json stays the hand-maintained source of truth for name/description/type/
// abiVersion; this script only fills the build-derived fields on release. The
// fingerprint is the signing keystore's cert SHA-256 (one key signs every APK, so
// every entry shares one fingerprint) — read it once with:
//   keytool -list -v -keystore <ks> -alias <alias> -storepass <pw> | grep SHA256
//
// Usage:
//   node tools/update-repo-for-release.mjs <releaseBaseUrl> <fingerprint> <versionName> [repoJsonPath]
// Example:
//   node tools/update-repo-for-release.mjs \
//     https://github.com/Gabriel-Graf/KomgaReaderPlugins/releases/download/v0.2.0 \
//     F4:16:...:DA 0.2.0
import { readFileSync, writeFileSync } from 'node:fs'
import { basename } from 'node:path'

const [, , releaseBaseUrl, fingerprint, versionName, repoPath = 'repo.json'] = process.argv
if (!releaseBaseUrl || !fingerprint || !versionName) {
  console.error('usage: update-repo-for-release.mjs <releaseBaseUrl> <fingerprint> <versionName> [repoJsonPath]')
  process.exit(1)
}

const repo = JSON.parse(readFileSync(repoPath, 'utf8'))
const base = releaseBaseUrl.replace(/\/+$/, '')
for (const p of repo.plugins) {
  // The committed basename is "<module>-<oldVersion>.apk"; re-stamp the version
  // segment so the release asset name tracks the tag (CI names APKs the same way).
  const apk = basename(p.apkUrl).replace(/-\d+\.\d+\.\d+\.apk$/, `-${versionName}.apk`)
  p.apkUrl = `${base}/${apk}`
  p.fingerprint = fingerprint
  p.versionName = versionName
}
writeFileSync(repoPath, JSON.stringify(repo, null, 2) + '\n')
console.log(`Updated ${repo.plugins.length} entries → ${base}/... (fingerprint ${fingerprint.slice(0, 11)}…, version ${versionName})`)
