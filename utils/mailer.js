const nodemailer = require('nodemailer');

let _transporter = null;

function getTransporter() {
  if (_transporter) return _transporter;
  if (!process.env.SMTP_HOST) return null;
  _transporter = nodemailer.createTransport({
    host:   process.env.SMTP_HOST,
    port:   parseInt(process.env.SMTP_PORT) || 587,
    secure: process.env.SMTP_SECURE === 'true',
    auth: {
      user: process.env.SMTP_USER,
      pass: process.env.SMTP_PASS
    }
  });
  return _transporter;
}

const FROM = () => process.env.SMTP_FROM || '"CAA Recruitment" <noreply@caa.co.ug>';

async function sendMail({ to, subject, html, text }) {
  const transporter = getTransporter();
  if (!transporter) {
    console.warn(`[mailer] SMTP not configured — skipping: ${subject} → ${to}`);
    return null;
  }
  return transporter.sendMail({
    from:    FROM(),
    to,
    subject,
    html,
    text: text || html.replace(/<[^>]+>/g, '').replace(/\n{3,}/g, '\n\n').trim()
  });
}

// ── Email templates ──────────────────────────────────────────────────────────
function wrap(title, bodyHtml) {
  return `<!DOCTYPE html>
<html>
<head><meta charset="utf-8"><title>${title}</title>
<style>
  body{font-family:Arial,sans-serif;background:#f5f5f5;margin:0;padding:0}
  .container{max-width:600px;margin:32px auto;background:#fff;border-radius:8px;overflow:hidden;box-shadow:0 2px 8px rgba(0,0,0,.1)}
  .header{background:#1a3a6e;color:#fff;padding:24px 32px}
  .header h1{margin:0;font-size:20px;font-weight:600}
  .body{padding:32px}
  .body p{color:#333;line-height:1.6;margin:0 0 16px}
  .badge{display:inline-block;padding:6px 16px;border-radius:4px;font-weight:700;font-size:14px}
  .footer{background:#f0f0f0;padding:16px 32px;font-size:12px;color:#999;text-align:center}
</style></head>
<body>
  <div class="container">
    <div class="header"><h1>Uganda Civil Aviation Authority</h1></div>
    <div class="body">${bodyHtml}</div>
    <div class="footer">This is an automated message from the CAA Recruitment Portal. Please do not reply to this email.</div>
  </div>
</body></html>`;
}

const STATUS_COLORS = {
  Shortlisted: '#2e7d32',
  Interview:   '#1565c0',
  Offered:     '#6a1b9a',
  Declined:    '#c62828',
  Pending:     '#e65100'
};

function applicationStatusEmail({ candidateName, jobTitle, status, message }) {
  const color = STATUS_COLORS[status] || '#333';
  const html = wrap(`Application Update — ${jobTitle}`, `
    <p>Dear <strong>${candidateName}</strong>,</p>
    <p>We are writing to inform you that your application for the position of
       <strong>${jobTitle}</strong> has been updated.</p>
    <p>Current status: <span class="badge" style="background:${color};color:#fff">${status}</span></p>
    ${message ? `<p>${message}</p>` : ''}
    <p>You can log into the <a href="${process.env.FRONTEND_URL || 'https://recruitment.caa.co.ug'}">CAA Recruitment Portal</a> to view full details.</p>
    <p>Thank you for your interest in joining Uganda Civil Aviation Authority.</p>
    <p>Regards,<br>CAA HR &amp; Recruitment Team</p>
  `);
  return { subject: `Application Update: ${status} — ${jobTitle}`, html };
}

function welcomeEmail({ firstName, lastName, email }) {
  const html = wrap('Welcome to CAA Recruitment Portal', `
    <p>Dear <strong>${firstName} ${lastName}</strong>,</p>
    <p>Your account has been created successfully on the Uganda Civil Aviation Authority Recruitment Portal.</p>
    <p>You can now:</p>
    <ul>
      <li>Browse and apply for available vacancies</li>
      <li>Upload your CV and supporting documents</li>
      <li>Track the status of your applications</li>
    </ul>
    <p><a href="${process.env.FRONTEND_URL || 'https://recruitment.caa.co.ug'}">Visit the Portal</a></p>
    <p>Regards,<br>CAA HR &amp; Recruitment Team</p>
  `);
  return { subject: 'Welcome to CAA Recruitment Portal', html };
}

function bulkEmail({ candidateName, subject, body }) {
  const html = wrap(subject, `
    <p>Dear <strong>${candidateName}</strong>,</p>
    ${body.split('\n').map(p => `<p>${p}</p>`).join('')}
    <p>Regards,<br>CAA HR &amp; Recruitment Team</p>
  `);
  return { subject, html };
}

module.exports = { sendMail, applicationStatusEmail, welcomeEmail, bulkEmail };
