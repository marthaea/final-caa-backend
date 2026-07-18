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
  maxIdle: 4
});

module.exports = pool;
