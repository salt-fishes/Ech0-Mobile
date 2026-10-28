#!/usr/bin/env node
/**
 * 发版格式校验：node scripts/check-release.mjs 0.3.0
 * 规则见 docs/release/FORMAT.md。
 */
import { readFileSync } from 'node:fs'

const version = process.argv[2]
if (!version) {
  console.error('usage: node scripts/check-release.mjs <version>')
  process.exit(1)
}

const fail = (message) => {
  console.error(`::error::${message}`)
  process.exit(1)
}

const notesPath = `docs/release/${version}.md`
let notes
try {
  notes = readFileSync(notesPath, 'utf8')
} catch {
  fail(`missing ${notesPath}`)
}

const gradle = readFileSync('app/build.gradle.kts', 'utf8')
const versionName = gradle.match(/versionName\s*=\s*"([^"]+)"/)?.[1]
if (!versionName) fail('cannot find versionName in app/build.gradle.kts')
if (versionName !== version) {
  fail(`versionName ${versionName} does not match tag version ${version}`)
}

if (!new RegExp(`^#\\s+Ech0 Mobile v${version.replace(/\./g, '\\.')}\\s*$`, 'm').test(notes)) {
  fail(`release title must be "# Ech0 Mobile v${version}"`)
}

const sections = ['概览', '亮点', '改进', '修复', '升级方式', '校验']
for (const section of sections) {
  if (!new RegExp(`^##\\s+${section}\\s*$`, 'm').test(notes)) {
    fail(`missing section "## ${section}" in ${notesPath}`)
  }
}

if (!/SHA-256:\s*[a-f0-9]{64}/i.test(notes)) {
  fail(`missing 64-char SHA-256 in ${notesPath}`)
}

console.log(`release format ok: ${notesPath}`)
