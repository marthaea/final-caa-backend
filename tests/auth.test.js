const { test, beforeEach } = require('node:test');
const assert = require('node:assert/strict');
const bcrypt = require('bcrypt');
const request = require('supertest');

const { enqueueRows, resetQueries } = require('./helpers');
const app = require('../app');
const { signRefreshToken } = require('../utils/jwt');

// Low cost factor keeps the suite fast; compare() works regardless of cost
const PASSWORD = 'Password1';
const passwordHashP = bcrypt.hash(PASSWORD, 4);

async function makeUser(overrides = {}) {
  return {
    id: 1,
    email: 'jane@example.com',
    password_hash: await passwordHashP,
    first_name: 'Jane',
    last_name: 'Doe',
    account_type: 'external',
    admin_role: null,
    employee_number: null,
    effective_type: 'external',
    email_verified: 1,
    is_active: 1,
    token_version: 0,
    ...overrides
  };
}

beforeEach(() => resetQueries());

test('GET /ping responds', async () => {
  const res = await request(app).get('/ping');
  assert.equal(res.status, 200);
  assert.equal(res.body.ok, true);
});

test('register rejects invalid payloads with field errors', async () => {
  const res = await request(app)
    .post('/api/auth/register')
    .send({ email: 'not-an-email', password: 'short' });
  assert.equal(res.status, 400);
  assert.equal(res.body.success, false);
  assert.ok(Array.isArray(res.body.errors));
  const fields = res.body.errors.map(e => e.field);
  assert.ok(fields.includes('email'));
  assert.ok(fields.includes('password'));
});

test('login succeeds with correct credentials and sets refresh cookie', async () => {
  enqueueRows([await makeUser()]); // SELECT user
  enqueueRows([]);                 // audit INSERT

  const res = await request(app)
    .post('/api/auth/login')
    .send({ email: 'jane@example.com', password: PASSWORD });

  assert.equal(res.status, 200);
  assert.equal(res.body.success, true);
  assert.equal(res.body.data.email, 'jane@example.com');
  assert.ok(res.body.data.token, 'access token expected');
  const cookies = res.headers['set-cookie'] || [];
  assert.ok(cookies.some(c => c.startsWith('caa_refresh=')), 'refresh cookie expected');
});

test('login rejects a wrong password', async () => {
  enqueueRows([await makeUser()]);

  const res = await request(app)
    .post('/api/auth/login')
    .send({ email: 'jane@example.com', password: 'WrongPass1' });

  assert.equal(res.status, 401);
  assert.equal(res.body.error, 'Invalid credentials');
});

test('login rejects a deactivated account', async () => {
  enqueueRows([await makeUser({ is_active: 0 })]);

  const res = await request(app)
    .post('/api/auth/login')
    .send({ email: 'jane@example.com', password: PASSWORD });

  assert.equal(res.status, 403);
  assert.match(res.body.error, /deactivated/);
});

test('refresh issues a new access token when token_version matches', async () => {
  const user = await makeUser();
  const refresh = signRefreshToken({ id: user.id, email: user.email, tv: 0 });
  enqueueRows([user]); // SELECT user

  const res = await request(app)
    .post('/api/auth/refresh-token')
    .set('Cookie', [`caa_refresh=${refresh}`]);

  assert.equal(res.status, 200);
  assert.ok(res.body.data.token);
});

test('refresh rejects a token from before logout (stale token_version)', async () => {
  const user = await makeUser({ token_version: 1 });
  const refresh = signRefreshToken({ id: user.id, email: user.email, tv: 0 });
  enqueueRows([user]);

  const res = await request(app)
    .post('/api/auth/refresh-token')
    .set('Cookie', [`caa_refresh=${refresh}`]);

  assert.equal(res.status, 401);
  assert.match(res.body.error, /Session expired/);
});

test('forgot-password gives the same neutral answer for unknown emails', async () => {
  enqueueRows([]); // SELECT user — not found

  const res = await request(app)
    .post('/api/auth/forgot-password')
    .send({ email: 'nobody@example.com' });

  assert.equal(res.status, 200);
  assert.match(res.body.data.message, /If that email is registered/);
});

test('reset-password rejects an invalid or expired token', async () => {
  enqueueRows([]); // SELECT by token hash — no match

  const res = await request(app)
    .post('/api/auth/reset-password')
    .send({ token: 'a'.repeat(64), password: 'NewPassword1' });

  assert.equal(res.status, 400);
  assert.match(res.body.error, /invalid or has expired/);
});
