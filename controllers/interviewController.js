// controllers/interviewController.js
const pool = require('../config/db');

// GET /api/v1/applications/:application_id/interviews
async function listInterviewsForApplication(req, res) {
  const { application_id } = req.params;

  const [interviews] = await pool.query(
    `SELECT * FROM interviews WHERE application_id = ? ORDER BY scheduled_at ASC`,
    [application_id]
  );

  // Attach panel members to each interview
  for (const interview of interviews) {
    const [panel] = await pool.query(
      `SELECT u.id, u.email, u.role
       FROM interview_panel ip
       JOIN users u ON u.id = ip.interviewer_id
       WHERE ip.interview_id = ?`,
      [interview.id]
    );
    interview.panel = panel;
  }

  res.json({ status: 'ok', interviews });
}

// POST /api/v1/applications/:application_id/interviews (HR Director, Super Admin)
// Body: { scheduled_at, mode, location, interviewer_ids: [1,2,3] }
async function scheduleInterview(req, res) {
  const { application_id } = req.params;
  const { scheduled_at, mode, location, interviewer_ids } = req.body;

  if (!scheduled_at) {
    return res.status(400).json({ status: 'error', message: 'scheduled_at is required' });
  }

  const connection = await pool.getConnection();
  try {
    await connection.beginTransaction();

    const [result] = await connection.query(
      `INSERT INTO interviews (application_id, scheduled_at, mode, location, status)
       VALUES (?, ?, ?, ?, 'scheduled')`,
      [application_id, scheduled_at, mode || 'in_person', location || null]
    );

    if (Array.isArray(interviewer_ids) && interviewer_ids.length > 0) {
      const values = interviewer_ids.map(id => [result.insertId, id]);
      await connection.query(
        `INSERT INTO interview_panel (interview_id, interviewer_id) VALUES ?`,
        [values]
      );
    }

    // Move application into interview stage
    await connection.query(`UPDATE applications SET status = 'interview' WHERE id = ?`, [application_id]);

    await connection.commit();
    res.status(201).json({ status: 'ok', interview_id: result.insertId });
  } catch (err) {
    await connection.rollback();
    throw err;
  } finally {
    connection.release();
  }
}

// PUT /api/v1/interviews/:id (Recruiter, HR Director, Super Admin)
async function updateInterview(req, res) {
  const { scheduled_at, mode, location, status, feedback } = req.body;

  const [result] = await pool.query(
    `UPDATE interviews
     SET scheduled_at = COALESCE(?, scheduled_at),
         mode = COALESCE(?, mode),
         location = COALESCE(?, location),
         status = COALESCE(?, status),
         feedback = COALESCE(?, feedback)
     WHERE id = ?`,
    [scheduled_at, mode, location, status, feedback, req.params.id]
  );

  if (result.affectedRows === 0) {
    return res.status(404).json({ status: 'error', message: 'Interview not found' });
  }
  res.json({ status: 'ok', message: 'Interview updated' });
}

module.exports = { listInterviewsForApplication, scheduleInterview, updateInterview };
