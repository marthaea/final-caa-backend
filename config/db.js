// config/db.js
require('dotenv').config();
const mysql = require('mysql2/promise');

const pool = mysql.createPool({
  host: process.env.DB_HOST,
  user: process.env.DB_USER,
  password: process.env.DB_PASSWORD,
  database: process.env.DB_NAME,
  port: process.env.DB_PORT,
  waitForConnections: true,
  connectionLimit: 10,
  queueLimit: 0,
  // The Railway proxy drops idle connections, which surfaced as intermittent
  // ECONNRESET 500s. Keep connections alive and retire idle ones before the
  // proxy kills them.
  enableKeepAlive: true,
  keepAliveInitialDelay: 10000,
  idleTimeout: 55000,
  // Keep every pooled connection warm rather than only 4 — a fresh connection
  // to the remote proxy occasionally takes >10s to establish (ETIMEDOUT 500s
  // under load), so reuse beats reconnecting wherever possible.
  maxIdle: 10,
  // And when a new connection IS needed, give a slow network a fighting
  // chance instead of failing at the 10s default.
  connectTimeout: 25000
});

module.exports = pool;
