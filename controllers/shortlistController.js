// controllers/shortlistController.js
const pool = require('../config/db');

// POST /api/v1/applications/:application_id/shortlist (Recruiter, HR Director, Super Admin)
// Manual shortlist action (in addition to the automatic criteria-based flip
// that happens on application submit).
async function shortlistApplication(req, res) {
  const { application_id } = req.params;
  const { notes } = req.body;

  const connection = await pool.getConnection();
  try {
    await connection.beginTransaction();

    const [appRows] = await connection.query(`SELECT * FROM applications WHERE id = ?`, [application_id]);
    if (appRows.length === 0) {
      await connection.rollback();
      return res.status(404).json({ status: 'error', message: 'Application not found' });
    }

    await connection.query(`UPDATE applications SET status = 'shortlisted' WHERE id = ?`, [application_id]);

    const [result] = await connection.query(
      `INSERT INTO shortlist_entries (application_id, shortlisted_by, notes) VALUES (?, ?, ?)`,
      [application_id, req.user.id, notes || null]
    );

    await connection.commit();
    res.status(201).json({ status: 'ok', shortlist_entry_id: result.insertId });
  } catch (err) {
    await connection.rollback();
    throw err;
  } finally {
    connection.release();
  }
}

// DELETE /api/v1/shortlist-entries/:id (Recruiter, HR Director, Super Admin)
async function removeShortlistEntry(req, res) {
  const [result] = await pool.query(`DELETE FROM shortlist_entries WHERE id = ?`, [req.params.id]);
  if (result.affectedRows === 0) {
    return res.status(404).json({ status: 'error', message: 'Shortlist entry not found' });
  }
  res.json({ status: 'ok', message: 'Removed from shortlist' });
}

module.exports = { shortlistApplication, removeShortlistEntry };
