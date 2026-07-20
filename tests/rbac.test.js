const { test, beforeEach } = require('node:test');
const assert = require('node:assert/strict');
const request = require('supertest');

const { enqueueRows, resetQueries } = require('./helpers');
const app = require('../app');
const { signAccessToken } = require('../utils/jwt');

// GET /api/chatbot/queries is guarded by verifyToken + requirePerm('canViewAudit')
const PROTECTED = '/api/chatbot/queries';

function tokenFor(overrides = {}) {
  return signAccessToken({
    id: 1,
    email: 'admin@caa.go.ug',
    firstName: 'Ada',
    lastName: 'Admin',
    accountType: 'admin',
    adminRole: 'super',
    effectiveType: 'admin',
    ...overrides
  });
}

beforeEach(() => resetQueries());

test('rejects requests without a token', async () => {
  const res = await request(app).get(PROTECTED);
  assert.equal(res.status, 401);
});

test('rejects non-admin accounts', async () => {
  const token = tokenFor({ accountType: 'external', adminRole: null, effectiveType: 'external' });
  const res = await request(app)
    .get(PROTECTED)
    .set('Authorization', `Bearer ${token}`);
  assert.equal(res.status, 403);
});

test('rejects admin roles whose defaults lack the permission', async () => {
  enqueueRows([]); // permission_overrides lookup — none, falls back to role defaults
  const token = tokenFor({ adminRole: 'recruiter' }); // canViewAudit: false
  const res = await request(app)
    .get(PROTECTED)
    .set('Authorization', `Bearer ${token}`);
  assert.equal(res.status, 403);
  assert.equal(res.body.error, 'Permission denied');
});

test('allows a super admin through', async () => {
  enqueueRows([]); // permission_overrides lookup — none
  enqueueRows([]); // chatbot_queries SELECT — empty list
  const res = await request(app)
    .get(PROTECTED)
    .set('Authorization', `Bearer ${tokenFor()}`);
  assert.equal(res.status, 200);
  assert.deepEqual(res.body.data, []);
});
