// tests/helpers.js
// Replaces the mysql2 pool's query method with a scriptable stub so route
// tests run without a database. Must be required before ../app so the stub is
// in place when routes capture the pool reference.
process.env.NODE_ENV = 'test';

const pool = require('../config/db');

// Each entry is either a mysql2-style result tuple ([rows, fields]) or a
// function (sql, params) => tuple for assertions on the query itself.
let queue = [];

pool.query = async (...args) => {
  if (queue.length === 0) return [[], []];
  const next = queue.shift();
  return typeof next === 'function' ? next(...args) : next;
};

function enqueueRows(...rowSets) {
  for (const rows of rowSets) queue.push([rows, []]);
}

function resetQueries() {
  queue = [];
}

module.exports = { pool, enqueueRows, resetQueries };
