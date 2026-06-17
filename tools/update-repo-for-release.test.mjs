// Run: node tools/update-repo-for-release.test.mjs
import { readFileSync, writeFileSync, mkdtempSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { execFileSync } from 'node:child_process'
import assert from 'node:assert'

const dir = mkdtempSync(join(tmpdir(), 'repotest-'))
const repoPath = join(dir, 'repo.json')
const fixture = {
  name: 'T',
  plugins: [
    { packageName: 'a.b.calibre', name: 'Calibre', type: 'source', abiVersion: 1, versionCode: 30000, versionName: '0.3.0', apkUrl: 'https://x/releases/download/v0.3.0/komga-calibre-source-0.3.0.apk', fingerprint: 'OLD' },
    { packageName: 'a.b.lora', name: 'Lora', type: 'font', abiVersion: 2, versionCode: 30000, versionName: '0.3.0', apkUrl: 'https://x/releases/download/v0.3.0/komga-font-lora-0.3.0.apk', fingerprint: 'OLD' },
  ],
}
writeFileSync(repoPath, JSON.stringify(fixture, null, 2) + '\n')

execFileSync('node', ['tools/update-repo-for-release.mjs', 'komga-calibre-source',
  'https://x/releases/download/komga-calibre-source-v0.3.1', 'NEWFP', '0.3.1', '30001', repoPath])

const out = JSON.parse(readFileSync(repoPath, 'utf8'))
const cal = out.plugins.find((p) => p.packageName === 'a.b.calibre')
const lora = out.plugins.find((p) => p.packageName === 'a.b.lora')
assert.equal(cal.versionName, '0.3.1')
assert.equal(cal.versionCode, 30001)
assert.equal(cal.fingerprint, 'NEWFP')
assert.equal(cal.apkUrl, 'https://x/releases/download/komga-calibre-source-v0.3.1/komga-calibre-source-0.3.1.apk')
// other entry untouched
assert.equal(lora.versionName, '0.3.0')
assert.equal(lora.versionCode, 30000)
assert.equal(lora.fingerprint, 'OLD')

// unknown module → non-zero exit
let failed = false
try {
  execFileSync('node', ['tools/update-repo-for-release.mjs', 'komga-nope', 'https://x', 'F', '1.0.0', '10000', repoPath])
} catch { failed = true }
assert.ok(failed, 'unknown module must exit non-zero')

console.log('update-repo-for-release.test.mjs: OK')
