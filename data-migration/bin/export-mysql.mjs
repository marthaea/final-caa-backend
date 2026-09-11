#!/usr/bin/env node
import { createHash } from 'node:crypto';
import { createWriteStream } from 'node:fs';
import { lstat, mkdir, readdir, readFile, rename, rm, writeFile } from 'node:fs/promises';
import { once } from 'node:events';
import { resolve } from 'node:path';
import { TABLES, TABLE_NAMES } from '../lib/schema.mjs';
import { csvCell, csvRow, readSecureOptionFile } from '../lib/toolkit.mjs';

function argumentsFrom(argv) {
  const result = {};
  for (let i = 2; i < argv.length; i += 2) {
    if (!['--config', '--output'].includes(argv[i]) || !argv[i + 1]) {
      throw new Error('Usage: export-mysql.mjs --config MYSQL_CNF --output EMPTY_DIRECTORY');
    }
    result[argv[i].slice(2)] = argv[i + 1];
  }
  if (!result.config || !result.output) throw new Error('Both --config and --output are required');
  return result;
}

function query(connection, sql, values = []) {
  return new Promise((accept, reject) => {
    connection.query(sql, values, (error, rows) => error ? reject(error) : accept(rows));
  });
}

async function writeChunk(stream, chunk) {
  if (!stream.write(chunk)) await once(stream, 'drain');
}

async function exportTable(connection, directory, table) {
  const finalPath = resolve(directory, `${table.name}.csv`);
  const temporaryPath = `${finalPath}.partial`;
  const output = createWriteStream(temporaryPath, { flags: 'wx', mode: 0o600 });
  const hash = createHash('sha256');
  let bytes = 0;
  let rows = 0;
  const emit = async (line) => {
    const buffer = Buffer.from(line, 'utf8');
    hash.update(buffer);
    bytes += buffer.length;
    await writeChunk(output, buffer);
  };

  try {
    await emit(`${[...table.columns, '__nulls'].map(csvCell).join(',')}\n`);
    const columns = table.columns.map((column) => `\`${column}\``).join(',');
    const stream = connection.query(`SELECT ${columns} FROM \`${table.name}\` ORDER BY \`id\``)
      .stream({ highWaterMark: 128 });
    for await (const row of stream) {
      await emit(csvRow(table.columns.map((column) => row[column])));
      rows += 1;
    }
    output.end();
    await once(output, 'close');
    await rename(temporaryPath, finalPath);
    return { name: `${table.name}.csv`, rows, bytes, sha256: hash.digest('hex') };
  } catch (error) {
    output.destroy();
    await rm(temporaryPath, { force: true });
    throw error;
  }
}

async function validateSource(connection, database) {
  const placeholders = TABLE_NAMES.map(() => '?').join(',');
  const tables = await query(connection,
    `SELECT table_name, engine FROM information_schema.tables
     WHERE table_schema = ? ORDER BY table_name`,
    [database]);
  const actualNames = tables.map((row) => row.TABLE_NAME ?? row.table_name);
  if (actualNames.join(',') !== [...TABLE_NAMES].sort().join(',')) {
    throw new Error(`Source table set differs from approved 17-table schema: ${actualNames.join(',')}`);
  }
  const nonTransactional = tables.filter((row) => (row.ENGINE ?? row.engine) !== 'InnoDB');
  if (nonTransactional.length) throw new Error('All source tables must use InnoDB');

  const columns = await query(connection,
    `SELECT table_name, column_name FROM information_schema.columns
     WHERE table_schema = ? AND table_name IN (${placeholders})
     ORDER BY table_name, ordinal_position`,
    [database, ...TABLE_NAMES]);
  const byTable = new Map(TABLE_NAMES.map((name) => [name, []]));
  for (const row of columns) {
    byTable.get(row.TABLE_NAME ?? row.table_name).push(row.COLUMN_NAME ?? row.column_name);
  }
  for (const table of TABLES) {
    if (byTable.get(table.name).join(',') !== table.columns.join(',')) {
      throw new Error(`Source columns differ from approved schema for ${table.name}`);
    }
  }
}

export async function runExport(configPath, outputPath) {
  const config = await readSecureOptionFile(configPath);
  if ((config.ssl_mode ?? '').toUpperCase() !== 'VERIFY_IDENTITY' || !config.ssl_ca) {
    throw new Error('MySQL config must set ssl-mode=VERIFY_IDENTITY and ssl-ca');
  }

  const output = resolve(outputPath);
  await mkdir(output, { recursive: false, mode: 0o700 }).catch((error) => {
    if (error.code !== 'EEXIST') throw error;
  });
  const outputStat = await lstat(output);
  if (!outputStat.isDirectory() || outputStat.isSymbolicLink() || (outputStat.mode & 0o077) !== 0) {
    throw new Error('Output must be a non-symlink directory inaccessible to group/others');
  }
  if ((await readdir(output)).length !== 0) throw new Error('Output directory must be empty');

  const mysql = await import('mysql2');
  const connection = mysql.default.createConnection({
    host: config.host,
    port: Number(config.port ?? 3306),
    user: config.user,
    password: config.password,
    database: config.database,
    ssl: { ca: await readFile(config.ssl_ca, 'utf8'), rejectUnauthorized: true },
    timezone: 'Z',
    dateStrings: true,
    supportBigNumbers: true,
    bigNumberStrings: true,
    multipleStatements: false
  });

  const created = [];
  let inTransaction = false;
  try {
    await query(connection, "SET SESSION time_zone = '+00:00'");
    await query(connection, 'SET SESSION TRANSACTION ISOLATION LEVEL REPEATABLE READ');
    await query(connection, 'START TRANSACTION WITH CONSISTENT SNAPSHOT, READ ONLY');
    inTransaction = true;
    const snapshotStartedAt = new Date().toISOString();
    await validateSource(connection, config.database);
    const files = [];
    for (const table of TABLES) {
      const detail = await exportTable(connection, output, table);
      files.push(detail);
      created.push(resolve(output, detail.name));
      console.log(`Exported ${table.name}: ${detail.rows} rows`);
    }
    await query(connection, 'COMMIT');
    inTransaction = false;
    const manifest = {
      format: 'caa-migration-csv-v1',
      schema: config.database,
      snapshot_started_at_utc: snapshotStartedAt,
      generated_at_utc: new Date().toISOString(),
      files
    };
    const temporaryManifest = resolve(output, 'manifest.json.partial');
    await writeFile(temporaryManifest, `${JSON.stringify(manifest, null, 2)}\n`, { mode: 0o600, flag: 'wx' });
    await rename(temporaryManifest, resolve(output, 'manifest.json'));
    console.log(`Wrote manifest for ${files.length} tables.`);
  } catch (error) {
    if (inTransaction) await query(connection, 'ROLLBACK').catch(() => {});
    await Promise.all(created.map((path) => rm(path, { force: true })));
    await rm(resolve(output, 'manifest.json.partial'), { force: true });
    throw error;
  } finally {
    connection.destroy();
  }
}

async function main() {
  const args = argumentsFrom(process.argv);
  await runExport(args.config, args.output);
}

if (import.meta.url === `file://${process.argv[1]}`) {
  main().catch((error) => {
    console.error(`Export failed: ${error.message}`);
    process.exitCode = 1;
  });
}
