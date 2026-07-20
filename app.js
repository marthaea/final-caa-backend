// app.js — assembles the Express app without binding a port, so index.js can
// start it and tests can drive it directly with supertest.
require('dotenv').config();

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

// ── Keep-alive ping (used by UptimeRobot / cron-job.org to prevent Render sleep)
app.get('/ping', (req, res) => res.json({ ok: true, ts: Date.now() }));

// ── Routes ────────────────────────────────────────────────────────────────────
app.use('/api', require('./routes'));

// ── 404 ───────────────────────────────────────────────────────────────────────
app.use((req, res) => {
  res.status(404).json({ success: false, error: 'Route not found' });
});

// ── Centralised error handler ─────────────────────────────────────────────────
app.use(errorHandler);

module.exports = app;
