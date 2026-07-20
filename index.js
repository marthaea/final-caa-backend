require('dotenv').config();

// ── Required env variables — fail fast if any are missing ────────────────────
// DB_PASSWORD is excluded — it may be empty on local MySQL with no root password
const REQUIRED_ENV = [
  'DB_HOST', 'DB_USER', 'DB_NAME',
  'JWT_SECRET', 'JWT_REFRESH_SECRET'
];
const missing = REQUIRED_ENV.filter(k => process.env[k] === undefined || process.env[k] === '');
if (missing.length) {
  console.error(`[startup] Missing required env variables: ${missing.join(', ')}`);
  process.exit(1);
}

const app = require('./app');
const { startCronJobs } = require('./utils/cron');

// ── Start server ──────────────────────────────────────────────────────────────
const PORT = process.env.PORT || 5000;
const server = app.listen(PORT, () => {
  console.log(`[${new Date().toISOString()}] CAA Recruitment API running on port ${PORT}`);
  startCronJobs();
});

// ── Graceful shutdown ─────────────────────────────────────────────────────────
function shutdown(signal) {
  console.log(`[${new Date().toISOString()}] ${signal} received — shutting down gracefully`);
  server.close(() => {
    console.log('HTTP server closed');
    process.exit(0);
  });
  // Force-exit if connections linger beyond 10 s
  setTimeout(() => { console.error('Force exiting'); process.exit(1); }, 10000);
}
process.on('SIGTERM', () => shutdown('SIGTERM'));
process.on('SIGINT',  () => shutdown('SIGINT'));
