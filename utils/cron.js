const cron = require('node-cron');
const pool = require('../config/db');

function startCronJobs() {
  // Daily at 00:05 — close expired jobs
  cron.schedule('5 0 * * *', async () => {
    try {
      const [result] = await pool.query(
        `UPDATE jobs
         SET    visibility = 'closed'
         WHERE  closes_at < CURDATE()
           AND  visibility != 'closed'`
      );
      if (result.affectedRows > 0) {
        console.log(`[${new Date().toISOString()}] [cron] Closed ${result.affectedRows} expired job(s)`);
      }
    } catch (e) {
      console.error(`[${new Date().toISOString()}] [cron] Job expiry error:`, e.message);
    }
  });

  // Daily at 00:10 — purge analytics events older than 90 days
  cron.schedule('10 0 * * *', async () => {
    try {
      const [result] = await pool.query(
        'DELETE FROM analytics_events WHERE created_at < DATE_SUB(NOW(), INTERVAL 90 DAY)'
      );
      if (result.affectedRows > 0) {
        console.log(`[${new Date().toISOString()}] [cron] Purged ${result.affectedRows} old analytics event(s)`);
      }
    } catch (e) {
      console.error(`[${new Date().toISOString()}] [cron] Analytics purge error:`, e.message);
    }
  });

  console.log(`[${new Date().toISOString()}] [cron] Scheduled: job-expiry (00:05), analytics-purge (00:10)`);
}

module.exports = { startCronJobs };
