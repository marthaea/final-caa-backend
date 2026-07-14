// utils/deptId.js
// Generates the next sequential DEPT-XXX code.
// Kept in the app/service layer (not a DB trigger) per project decision.

async function generateNextDeptCode(pool) {
  const [rows] = await pool.query(
    `SELECT dept_code FROM departments ORDER BY id DESC LIMIT 1`
  );

  if (rows.length === 0) {
    return 'DEPT-001';
  }

  const lastCode = rows[0].dept_code; // e.g. "DEPT-007"
  const lastNumber = parseInt(lastCode.split('-')[1], 10);
  const nextNumber = lastNumber + 1;
  const padded = String(nextNumber).padStart(3, '0');

  return `DEPT-${padded}`;
}

module.exports = { generateNextDeptCode };
