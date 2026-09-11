import { createHash } from 'node:crypto';
import { lstat, readFile } from 'node:fs/promises';
import { TABLE_NAMES } from './schema.mjs';

export function csvCell(value) {
  const text = Buffer.isBuffer(value) ? value.toString('hex') : String(value);
  return `"${text.replaceAll('"', '""')}"`;
}

export function csvRow(values) {
  const nullIndexes = [];
  const cells = values.map((value, index) => {
    if (value === null) {
      nullIndexes.push(index);
      return '""';
    }
    return csvCell(value);
  });
  cells.push(csvCell(nullIndexes.join(',')));
  return `${cells.join(',')}\n`;
}

export function parseOptionFile(text) {
  let section = '';
  const result = {};
  for (const original of text.split(/\r?\n/)) {
    const line = original.trim();
    if (!line || line.startsWith('#') || line.startsWith(';')) continue;
    const sectionMatch = line.match(/^\[([^\]]+)\]$/);
    if (sectionMatch) {
      section = sectionMatch[1].toLowerCase();
      continue;
    }
    if (section !== 'client') continue;
    const equals = line.indexOf('=');
    if (equals < 1) throw new Error(`Invalid option-file line: ${original}`);
    const key = line.slice(0, equals).trim().replaceAll('-', '_');
    let value = line.slice(equals + 1).trim();
    if ((value.startsWith('"') && value.endsWith('"')) ||
        (value.startsWith("'") && value.endsWith("'"))) {
      value = value.slice(1, -1);
    }
    result[key] = value;
  }
  return result;
}

export async function readSecureOptionFile(path) {
  const stat = await lstat(path);
  if (!stat.isFile() || stat.isSymbolicLink()) {
    throw new Error('MySQL config must be a regular, non-symlink file');
  }
  if ((stat.mode & 0o077) !== 0) {
    throw new Error('MySQL config must not be readable or writable by group/others');
  }
  const config = parseOptionFile(await readFile(path, 'utf8'));
  for (const required of ['host', 'user', 'password', 'database']) {
    if (!config[required]) throw new Error(`MySQL config [client] is missing ${required}`);
  }
  return config;
}

export function sha256(content) {
  return createHash('sha256').update(content).digest('hex');
}

export function validateManifest(manifest) {
  if (manifest.format !== 'caa-migration-csv-v1') throw new Error('Unsupported manifest format');
  if (!Array.isArray(manifest.files) || manifest.files.length !== 17) {
    throw new Error('Manifest must describe exactly 17 table files');
  }
  const names = new Set();
  for (const file of manifest.files) {
    if (!/^[a-z_]+\.csv$/.test(file.name) || names.has(file.name)) {
      throw new Error(`Unsafe or duplicate manifest filename: ${file.name}`);
    }
    if (!/^[a-f0-9]{64}$/.test(file.sha256)) throw new Error(`Invalid checksum for ${file.name}`);
    if (!Number.isSafeInteger(file.rows) || file.rows < 0) throw new Error(`Invalid row count for ${file.name}`);
    if (!Number.isSafeInteger(file.bytes) || file.bytes < 0) throw new Error(`Invalid byte count for ${file.name}`);
    names.add(file.name);
  }
  const expected = TABLE_NAMES.map((name) => `${name}.csv`).sort();
  if ([...names].sort().join(',') !== expected.join(',')) {
    throw new Error('Manifest table files differ from the approved schema');
  }
}
