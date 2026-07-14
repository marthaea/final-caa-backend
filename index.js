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

const express      = require('express');
const cors         = require('cors');
const helmet       = require('helmet');
const cookieParser = require('cookie-parser');
const morgan       = require('morgan');
const compression  = require('compression');
const { generalLimiter } = require('./middleware/rateLimiter');
const requestId    = require('./middleware/requestId');
const errorHandler = require('./middleware/errorHandler');
const swagger      = require('./utils/swagger');
const { startCronJobs } = require('./utils/cron');

const app = express();

// ── Request ID (must be first so it's in every log line) ─────────────────────
app.use(requestId);

// ── Security & logging ────────────────────────────────────────────────────────
app.use(helmet());
const allowedOrigins = (process.env.FRONTEND_URL || 'http://localhost:3000')
  .split(',').map(o => o.trim());
app.use(cors({
  origin: (origin, cb) => {
    if (!origin || allowedOrigins.some(o => origin.startsWith(o))) return cb(null, true);
    cb(new Error(`CORS: origin ${origin} not allowed`));
  },
  methods:        ['GET', 'POST', 'PUT', 'DELETE', 'OPTIONS'],
  allowedHeaders: ['Content-Type', 'Authorization'],
  credentials:    true
}));
app.use(morgan(process.env.NODE_ENV === 'production' ? 'combined' : 'dev'));
app.use(compression());

// ── Body / cookie parsing ─────────────────────────────────────────────────────
app.use(express.json());
app.use(express.urlencoded({ extended: true }));
app.use(cookieParser());

// ── Rate limiting (applied globally; auth routes have tighter per-route limits)
app.use('/api', generalLimiter);

// ── API Docs (Swagger UI) — disable in production or restrict by IP/auth ──────
if (process.env.NODE_ENV !== 'production') {
  app.use('/api-docs', swagger.serve, swagger.setup);
  console.log(`[startup] Swagger UI available at http://localhost:${process.env.PORT || 5000}/api-docs`);
}

// ── Routes ────────────────────────────────────────────────────────────────────
app.use('/api', require('./routes'));

// ── 404 ───────────────────────────────────────────────────────────────────────
app.use((req, res) => {
  res.status(404).json({ success: false, error: 'Route not found' });
});

// ── Centralised error handler ─────────────────────────────────────────────────
app.use(errorHandler);

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
