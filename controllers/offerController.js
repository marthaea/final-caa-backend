// controllers/offerController.js
const pool = require('../config/db');

// POST /api/v1/applications/:application_id/offer (HR Director, Super Admin)
async function extendOffer(req, res) {
  const { application_id } = req.params;
  const { salary, start_date } = req.body;

  const connection = await pool.getConnection();
  try {
    await connection.beginTransaction();

    const [result] = await connection.query(
      `INSERT INTO offers (application_id, offered_by, salary, start_date, status)
       VALUES (?, ?, ?, ?, 'pending')`,
      [application_id, req.user.id, salary || null, start_date || null]
    );

    await connection.query(`UPDATE applications SET status = 'offered' WHERE id = ?`, [application_id]);

    await connection.commit();
    res.status(201).json({ status: 'ok', offer_id: result.insertId });
  } catch (err) {
    await connection.rollback();
    throw err;
  } finally {
    connection.release();
  }
}

// PUT /api/v1/offers/:id/status (HR Director, Super Admin)
async function updateOfferStatus(req, res) {
  const { status } = req.body;
  const validStatuses = ['pending', 'accepted', 'rejected', 'withdrawn'];
  if (!validStatuses.includes(status)) {
    return res.status(400).json({ status: 'error', message: 'Invalid status' });
  }

  const connection = await pool.getConnection();
  try {
    await connection.beginTransaction();

    const [offerRows] = await connection.query(`SELECT * FROM offers WHERE id = ?`, [req.params.id]);
    const offer = offerRows[0];
    if (!offer) {
      await connection.rollback();
      return res.status(404).json({ status: 'error', message: 'Offer not found' });
    }

    await connection.query(
      `UPDATE offers SET status = ?, responded_at = CASE WHEN ? IN ('accepted','rejected') THEN NOW() ELSE responded_at END WHERE id = ?`,
      [status, status, req.params.id]
    );

    if (status === 'accepted') {
      await connection.query(`UPDATE applications SET status = 'hired' WHERE id = ?`, [offer.application_id]);
    } else if (status === 'rejected') {
      await connection.query(`UPDATE applications SET status = 'rejected' WHERE id = ?`, [offer.application_id]);
    }

    await connection.commit();
    res.json({ status: 'ok', message: 'Offer status updated' });
  } catch (err) {
    await connection.rollback();
    throw err;
  } finally {
    connection.release();
  }
}

module.exports = { extendOffer, updateOfferStatus };
