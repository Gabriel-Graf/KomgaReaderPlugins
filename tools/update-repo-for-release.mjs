#!/usr/bin/env node
// Rewrites ONE plugin's entry in repo.json for a per-plugin release: points its apkUrl at the
// release asset for the given tag and stamps fingerprint/versionName/versionCode. Every other
// entry is left untouched. repo.json stays the hand-maintained source of truth for
// name/description/type/abiVersion.
//
// Usage:
//   node tools/update-repo-for-release.mjs <moduleDir> <releaseBaseUrl> <fingerprint> <versionName> <versionCode> [repoJsonPath]
import { readFileSync, writeFileSync } from 'node:fs'

const [, , moduleDir, releaseBaseUrl, fingerprint, versionName, versionCode, repoPath = 'repo.json'] = process.argv
if (!moduleDir || !releaseBaseUrl || !fingerprint || !versionName || !versionCode) {
  console.error('usage: update-repo-for-release.mjs <moduleDir> <releaseBaseUrl> <fingerprint> <versionName> <versionCode> [repoJsonPath]')
  process.exit(1)
}

const repo = JSON.parse(readFileSync(repoPath, 'utf8'))
const base = releaseBaseUrl.replace(/\/+$/, '')
const prefix = `${moduleDir}-`
const matches = repo.plugins.filter((p) => {
  const name = p.apkUrl.split('/').pop()
  return name.startsWith(prefix)
})
if (matches.length !== 1) {
  console.error(`expected exactly one entry for module "${moduleDir}", found ${matches.length}`)
  process.exit(2)
}
const p = matches[0]
p.apkUrl = `${base}/${moduleDir}-${versionName}.apk`
p.fingerprint = fingerprint
p.versionName = versionName
p.versionCode = Number(versionCode)
writeFileSync(repoPath, JSON.stringify(repo, null, 2) + '\n')
console.log(`Updated ${p.packageName} → ${p.apkUrl} (vc ${p.versionCode})`)
