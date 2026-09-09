import assert from 'node:assert/strict';
import { chmod, mkdtemp, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import test from 'node:test';
import { TABLE_NAMES } from '../lib/schema.mjs';
import { csvCell, csvRow, parseOptionFile, readSecureOptionFile, sha256, validateManifest } from '../lib/toolkit.mjs';
import { sqlCountChecks, verifyDirectory } from '../bin/manifest.mjs';

test('CSV encoding preserves commas, quotes, newlines, empty text, and SQL null', () => {
  assert.equal(csvCell('a,"b"\nc'), '"a,""b""\nc"');
  assert.equal(
    csvRow(['', null, 'NULL', 0]),
    '"","","NULL","0","1"\n'
  );
});

test('option parser reads only client section and normalizes dashed keys', () => {
  const parsed = parseOptionFile(`
    [other]
    password=ignored
    [client]
    host=db.example.invalid
    user=reader
    password="not-a-real-secret"
    database=railway
    ssl-mode=VERIFY_IDENTITY
  `);
  assert.deepEqual(parsed, {
    host: 'db.example.invalid',
    user: 'reader',
    password: 'not-a-real-secret',
    database: 'railway',
    ssl_mode: 'VERIFY_IDENTITY'
  });
});

test('secure option reader rejects group-readable credential files', async () => {
  const directory = await mkdtemp(join(tmpdir(), 'caa-config-test-'));
  const path = join(directory, 'mysql.cnf');
  try {
    await writeFile(path, '[client]\nhost=x\nuser=x\npassword=x\ndatabase=x\n', { mode: 0o600 });
    assert.equal((await readSecureOptionFile(path)).database, 'x');
    await chmod(path, 0o640);
    await assert.rejects(() => readSecureOptionFile(path), /group\/others/);
  } finally {
    await rm(directory, { recursive: true, force: true });
  }
});

test('manifest schema rejects missing or unexpected tables', () => {
  const files = TABLE_NAMES.map((name) => ({
    name: `${name}.csv`, rows: 0, bytes: 0, sha256: '0'.repeat(64)
  }));
  assert.doesNotThrow(() => validateManifest({ format: 'caa-migration-csv-v1', files }));
  files[0] = { ...files[0], name: 'unexpected.csv' };
  assert.throws(
    () => validateManifest({ format: 'caa-migration-csv-v1', files }),
    /approved schema/
  );
});

test('manifest generates transactional staging count assertions', () => {
  const files = TABLE_NAMES.map((name, index) => ({
    name: `${name}.csv`, rows: index, bytes: 0, sha256: '0'.repeat(64)
  }));
  const sql = sqlCountChecks({ format: 'caa-migration-csv-v1', files });
  assert.match(sql, /raise_count_mismatch\('analytics_events', 0, count\(\*\)\)/);
  assert.match(sql, /FROM migration_stage\.users;/);
  assert.equal(sql.split('\n').length, 17);
});

test('directory verification checks all file hashes and detects tampering', async () => {
  const directory = await mkdtemp(join(tmpdir(), 'caa-migration-test-'));
  try {
    const files = [];
    for (const name of TABLE_NAMES) {
      const filename = `${name}.csv`;
      const content = '"id","__nulls"\n';
      await writeFile(join(directory, filename), content, { mode: 0o600 });
      files.push({
        name: filename,
        rows: 0,
        bytes: Buffer.byteLength(content),
        sha256: sha256(content)
      });
    }
    await writeFile(
      join(directory, 'manifest.json'),
      JSON.stringify({ format: 'caa-migration-csv-v1', files }),
      { mode: 0o600 }
    );
    const verified = await verifyDirectory(directory);
    assert.equal(verified.files.length, 17);

    await writeFile(join(directory, `${TABLE_NAMES[0]}.csv`), 'tampered', { mode: 0o600 });
    await assert.rejects(() => verifyDirectory(directory), /Checksum or size mismatch/);
  } finally {
    await rm(directory, { recursive: true, force: true });
  }
});
