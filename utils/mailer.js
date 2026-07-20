const nodemailer = require('nodemailer');

let _transporter = null;

function isConfigured() {
  const { SMTP_HOST, SMTP_USER, SMTP_PASS } = process.env;
  if (!SMTP_HOST || !SMTP_USER || !SMTP_PASS) return false;
  // Treat the .env template placeholders as "not configured"
  if (/your_gmail|your_16_char|example\.com/i.test(SMTP_USER + SMTP_PASS)) return false;
  return true;
}

function getTransporter() {
  if (_transporter) return _transporter;
  if (!isConfigured()) return null;
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

const FROM = () => process.env.SMTP_FROM || '"CAA Recruitment" <noreply@caa.go.ug>';

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
    <p>You can log into the <a href="${process.env.FRONTEND_URL || 'https://recruitment.caa.go.ug'}">CAA Recruitment Portal</a> to view full details.</p>
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
    <p><a href="${process.env.FRONTEND_URL || 'https://recruitment.caa.go.ug'}">Visit the Portal</a></p>
    <p>Regards,<br>CAA HR &amp; Recruitment Team</p>
  `);
  return { subject: 'Welcome to CAA Recruitment Portal', html };
}

function verificationEmail({ firstName, verifyUrl }) {
  const html = wrap('Verify your email address', `
    <p>Dear <strong>${firstName}</strong>,</p>
    <p>Thank you for registering on the Uganda Civil Aviation Authority Recruitment Portal.</p>
    <p>Please confirm your email address so we can reach you about your applications:</p>
    <p style="text-align:center;margin:24px 0">
      <a href="${verifyUrl}" style="background:#1a3a6e;color:#fff;padding:12px 28px;border-radius:6px;text-decoration:none;font-weight:700">Verify my email</a>
    </p>
    <p>If the button does not work, copy this link into your browser:<br>
       <a href="${verifyUrl}">${verifyUrl}</a></p>
    <p>If you did not create this account, you can safely ignore this email.</p>
    <p>Regards,<br>CAA HR &amp; Recruitment Team</p>
  `);
  return { subject: 'Verify your email — CAA Recruitment Portal', html };
}

function passwordResetEmail({ firstName, resetUrl }) {
  const html = wrap('Reset your password', `
    <p>Dear <strong>${firstName}</strong>,</p>
    <p>We received a request to reset the password for your CAA Recruitment Portal account.</p>
    <p style="text-align:center;margin:24px 0">
      <a href="${resetUrl}" style="background:#1a3a6e;color:#fff;padding:12px 28px;border-radius:6px;text-decoration:none;font-weight:700">Reset my password</a>
    </p>
    <p>If the button does not work, copy this link into your browser:<br>
       <a href="${resetUrl}">${resetUrl}</a></p>
    <p>This link expires in 1 hour. If you did not request a password reset, you can safely ignore this email — your password will not change.</p>
    <p>Regards,<br>CAA HR &amp; Recruitment Team</p>
  `);
  return { subject: 'Reset your password — CAA Recruitment Portal', html };
}

// Interns report to a restricted site (e.g. Entebbe Airport) and need an Airport
// Pass to get in. That pass can only be applied for once HR issues a physical
// Acceptance Letter in person — so this always fires on intern acceptance,
// independent of whatever custom message an admin may also send.
function internAcceptanceEmail({ candidateName, jobTitle, location }) {
  const html = wrap('Internship Accepted — Next Steps', `
    <p>Dear <strong>${candidateName}</strong>,</p>
    <p>Congratulations! You have been accepted into the Uganda Civil Aviation Authority
       internship programme for the position of <strong>${jobTitle}</strong>.</p>
    <p>Before you can report for duty, please complete the following:</p>
    <ol>
      <li>Visit the Human Resource office at <strong>CAA Head Office</strong> in person to collect your official <strong>Acceptance Letter</strong>.</li>
      <li>This Acceptance Letter is required to apply for your <strong>Airport Pass</strong>, which grants you access to ${location ? `your workstation at <strong>${location}</strong>` : 'your assigned work location'}.</li>
      <li>Bring a valid form of identification (National ID or passport) when you come to collect your letter.</li>
    </ol>
    <p>Airport passes can take several days to process, so please collect your Acceptance Letter as soon as possible to avoid delaying your reporting date.</p>
    <p>If you have any questions, please contact the CAA HR &amp; Recruitment Team.</p>
    <p>Regards,<br>CAA HR &amp; Recruitment Team</p>
  `);
  return { subject: `Internship Accepted — Next Steps for ${jobTitle}`, html };
}

function bulkEmail({ candidateName, subject, body }) {
  const html = wrap(subject, `
    <p>Dear <strong>${candidateName}</strong>,</p>
    ${body.split('\n').map(p => `<p>${p}</p>`).join('')}
    <p>Regards,<br>CAA HR &amp; Recruitment Team</p>
  `);
  return { subject, html };
}

module.exports = { sendMail, applicationStatusEmail, welcomeEmail, bulkEmail, verificationEmail, passwordResetEmail, internAcceptanceEmail, isConfigured };
