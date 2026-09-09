#!/usr/bin/env node
import { createHash } from 'node:crypto';
import { createReadStream } from 'node:fs';
import { lstat, readFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { pipeline } from 'node:stream/promises';
import { Writable } from 'node:stream';
import { validateManifest } from '../lib/toolkit.mjs';

export async function hashFile(path) {
  const hash = createHash('sha256');
  let bytes = 0;
  await pipeline(createReadStream(path), new Writable({
    write(chunk, _encoding, callback) {
      bytes += chunk.length;
      hash.update(chunk);
      callback();
    }
  }));
  return { sha256: hash.digest('hex'), bytes };
}

export async function verifyDirectory(directory) {
  const root = resolve(directory);
  const manifestPath = resolve(root, 'manifest.json');
  const manifestStat = await lstat(manifestPath);
  if (!manifestStat.isFile() || manifestStat.isSymbolicLink()) {
    throw new Error('manifest.json must be a regular, non-symlink file');
  }
  const manifest = JSON.parse(await readFile(manifestPath, 'utf8'));
  validateManifest(manifest);
  for (const expected of manifest.files) {
    const path = resolve(root, expected.name);
    if (!path.startsWith(`${root}/`)) throw new Error(`Path escapes export directory: ${expected.name}`);
    const stat = await lstat(path);
    if (!stat.isFile() || stat.isSymbolicLink()) throw new Error(`${expected.name} is not a regular file`);
    const actual = await hashFile(path);
    if (actual.bytes !== expected.bytes || actual.sha256 !== expected.sha256) {
      throw new Error(`Checksum or size mismatch: ${expected.name}`);
    }
  }
  return manifest;
}

export function sqlCountChecks(manifest) {
  validateManifest(manifest);
  return manifest.files.map((file) => {
    const table = file.name.slice(0, -4);
    return `SELECT CASE WHEN count(*) = ${file.rows} THEN count(*) ` +
      `ELSE migration_stage.raise_count_mismatch('${table}', ${file.rows}, count(*)) END ` +
      `FROM migration_stage.${table};`;
  }).join('\n');
}

async function main() {
  if (process.argv.length !== 4 || !['verify', 'sql-count-check'].includes(process.argv[2])) {
    throw new Error('Usage: manifest.mjs {verify|sql-count-check} EXPORT_DIRECTORY');
  }
  const manifest = await verifyDirectory(process.argv[3]);
  if (process.argv[2] === 'verify') {
    console.log(`Verified ${manifest.files.length} files (${manifest.files.reduce((n, f) => n + f.rows, 0)} rows).`);
    return;
  }
  console.log(sqlCountChecks(manifest));
}

if (import.meta.url === `file://${process.argv[1]}`) {
  main().catch((error) => {
    console.error(`Manifest verification failed: ${error.message}`);
    process.exitCode = 1;
  });
}
