# Database Setup & Migration Guide for CAA Recruitment Backend

**Date:** July 30, 2026 (updated 2026-08-19)
**Status:** Production Database Migration Guide  
**Previous Host:** Railway  
**New Host:** [Your New Server]

> **Quick path (recommended):** A real production dump was already extracted
> from Railway before it expired (`recruitment_portal_backup.sql`, taken
> 2026-08-13). See **[`database/DATABASE-HANDOVER.md`](database/DATABASE-HANDOVER.md)**
> for exactly what to hand the person provisioning the new server, and exactly
> what 5 values to get back from them to reconnect the backend. That file
> supersedes the generic "run migrate.js + seed scripts" flow below when you
> have the dump — it restores schema AND all existing data in one import,
> instead of building an empty database from scratch. Use the rest of this
> document (`scripts/migrate.js` + seed scripts) only if starting fresh
> without the dump.

---

## Table of Contents
1. [Prerequisites](#prerequisites)
2. [Database Schema Setup](#database-schema-setup)
3. [Environment Configuration](#environment-configuration)
4. [Data Migration from Railway](#data-migration-from-railway)
5. [Seeding & Initial Data](#seeding--initial-data)
6. [Verification Checklist](#verification-checklist)
7. [Backup & Recovery](#backup--recovery)
8. [Troubleshooting](#troubleshooting)

---

## Prerequisites

### System Requirements
- **MySQL 8.0+** or **MariaDB 10.5+**
- **Node.js 20+**
- **npm 10+**
- **Git** (to access the repository)
- **SSH access** to the new database server
- **Database admin user** with CREATE/DROP/ALTER privileges

### Required Environment Variables
```bash
DB_HOST=<new-server-hostname-or-ip>
DB_PORT=3306
DB_USER=caa_recruitment_user
DB_PASSWORD=<strong-password-here>
DB_NAME=caa_recruitment
DB_POOL_SIZE=10
```

---

## Database Schema Setup

### Step 1: Create Database and User

```bash
# SSH into the database server
ssh user@db-server

# Connect to MySQL
mysql -u root -p

# Create database
CREATE DATABASE IF NOT EXISTS caa_recruitment CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

# Create dedicated user with limited privileges
CREATE USER 'caa_recruitment_user'@'%' IDENTIFIED BY 'your-strong-password';

# Grant privileges (adjust host from '%' to specific IP if needed for security)
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, INDEX, ALTER, 
      REFERENCES, CREATE TEMPORARY TABLES, LOCK TABLES ON caa_recruitment.* 
      TO 'caa_recruitment_user'@'%';

# For local/restricted access (recommended):
# GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, INDEX, ALTER ON caa_recruitment.* 
#       TO 'caa_recruitment_user'@'10.0.0.0/8';

FLUSH PRIVILEGES;

# Verify user creation
SELECT User, Host FROM mysql.user WHERE User='caa_recruitment_user';
```

### Step 2: Run Migration Script

The schema is defined in `scripts/migrate.js` (idempotent — safe to run multiple times).

```bash
# From your local machine or CI/CD pipeline
cd caa-recruitment-backend

# Configure environment
export DB_HOST="your-new-server-ip"
export DB_PORT="3306"
export DB_USER="caa_recruitment_user"
export DB_PASSWORD="your-password"
export DB_NAME="caa_recruitment"

# Run migration
node scripts/migrate.js

# Output should show:
# ✅ Created tables: users, jobs, applications, criteria, ...
# ✅ All 17 tables created successfully
```

### Step 3: Verify Schema

```bash
# Connect to the new database
mysql -h your-new-server-ip -u caa_recruitment_user -p

# List all tables
USE caa_recruitment;
SHOW TABLES;

# Expected output (17 tables):
# - users
# - jobs
# - applications
# - cv_profiles
# - criteria
# - settings
# - permission_overrides
# - notifications
# - sent_emails
# - audit_log
# - analytics_events
# - staff
# - chatbot_queries
# - departments
# - assessments
# - job_templates
# - candidate_scores

# Verify key table structure
DESCRIBE users;
DESCRIBE jobs;
DESCRIBE applications;

# Check constraints
SHOW CREATE TABLE applications\G
```

---

## Environment Configuration

### Backend .env File

Create `.env` in the backend root (or use environment variables):

```bash
# Database Connection
DB_HOST=your-new-server-ip
DB_PORT=3306
DB_USER=caa_recruitment_user
DB_PASSWORD=your-strong-password
DB_NAME=caa_recruitment
DB_POOL_SIZE=10
DB_POOL_WAIT_FOR_CONNECTIONS=true
DB_POOL_CONNECTION_LIMIT=20
DB_POOL_QUEUE_LIMIT=0

# Node Environment
NODE_ENV=production

# Server
PORT=5000
API_URL=https://your-api-domain.com
FRONTEND_URL=https://your-frontend-domain.com

# JWT Secrets (generate with: node -e "console.log(require('crypto').randomBytes(32).toString('hex'))")
JWT_SECRET=your-32-char-hex-secret
JWT_REFRESH_SECRET=your-32-char-hex-secret

# Email Configuration (Nodemailer)
MAIL_HOST=smtp.your-provider.com
MAIL_PORT=587
MAIL_USER=your-email@domain.com
MAIL_PASSWORD=your-app-password
MAIL_FROM=noreply@caa.go.ug

# File Storage (Cloudinary)
CLOUDINARY_CLOUD_NAME=your-cloud-name
CLOUDINARY_API_KEY=your-api-key
CLOUDINARY_API_SECRET=your-api-secret

# Logging
LOG_LEVEL=info
ENABLE_REQUEST_LOGGING=true

# Cron Jobs
ENABLE_CRON=true
CLEANUP_ANALYTICS_ENABLED=true
```

### Docker (Optional)

If running in Docker:

```dockerfile
FROM node:20-alpine

WORKDIR /app

COPY package*.json ./
RUN npm install --production

COPY . .

EXPOSE 5000

CMD ["npm", "start"]
```

```bash
# Build and run
docker build -t caa-recruitment-backend .
docker run -d \
  --name caa-backend \
  -p 5000:5000 \
  -e DB_HOST=your-db-host \
  -e DB_USER=caa_recruitment_user \
  -e DB_PASSWORD=your-password \
  -e DB_NAME=caa_recruitment \
  caa-recruitment-backend
```

---

## Data Migration from Railway

### Option A: Export/Import via SQL Dump (Recommended)

```bash
# 1. Export from Railway (can do via Railway dashboard or CLI)
# From your local machine:
mysqldump -h railway-db-host -u railway_user -p railway_db > backup-$(date +%Y%m%d-%H%M%S).sql

# 2. Verify dump file
wc -l backup-*.sql  # Should be thousands of lines
grep "^INSERT INTO" backup-*.sql | wc -l  # Show insert count

# 3. Import into new database
mysql -h your-new-server-ip -u caa_recruitment_user -p caa_recruitment < backup-*.sql

# 4. Verify data
mysql -h your-new-server-ip -u caa_recruitment_user -p
USE caa_recruitment;
SELECT COUNT(*) FROM users;
SELECT COUNT(*) FROM jobs;
SELECT COUNT(*) FROM applications;
SELECT COUNT(*) FROM candidate_scores;
```

### Option B: Node.js Migration Script

If direct SQL access isn't available:

```bash
# Create migration-data.js
cd caa-recruitment-backend
cat > scripts/migrate-data.js << 'EOF'
const mysql = require('mysql2/promise');

async function migrateData() {
  // Source (Railway)
  const sourcePool = mysql.createPool({
    host: process.env.SOURCE_DB_HOST,
    user: process.env.SOURCE_DB_USER,
    password: process.env.SOURCE_DB_PASSWORD,
    database: process.env.SOURCE_DB_NAME,
    waitForConnections: true,
    connectionLimit: 5,
    queueLimit: 0
  });

  // Destination (New Server)
  const destPool = mysql.createPool({
    host: process.env.DB_HOST,
    user: process.env.DB_USER,
    password: process.env.DB_PASSWORD,
    database: process.env.DB_NAME,
    waitForConnections: true,
    connectionLimit: 5,
    queueLimit: 0
  });

  const tables = [
    'users', 'jobs', 'applications', 'cv_profiles', 'criteria',
    'settings', 'permission_overrides', 'notifications', 'sent_emails',
    'audit_log', 'analytics_events', 'staff', 'chatbot_queries',
    'departments', 'assessments', 'job_templates', 'candidate_scores'
  ];

  try {
    for (const table of tables) {
      console.log(`Migrating ${table}...`);
      const [rows] = await sourcePool.query(`SELECT * FROM ${table}`);
      
      if (rows.length === 0) {
        console.log(`  → No data in ${table}`);
        continue;
      }

      // Disable foreign key checks during insert
      await destPool.query('SET FOREIGN_KEY_CHECKS = 0');
      
      // Clear existing data
      await destPool.query(`DELETE FROM ${table}`);
      
      // Insert data in batches
      const batchSize = 100;
      for (let i = 0; i < rows.length; i += batchSize) {
        const batch = rows.slice(i, i + batchSize);
        for (const row of batch) {
          const cols = Object.keys(row).join(', ');
          const vals = Object.values(row).map(v => 
            v === null ? 'NULL' : (typeof v === 'string' ? `'${v.replace(/'/g, "''")}'` : v)
          ).join(', ');
          await destPool.query(`INSERT INTO ${table} (${cols}) VALUES (${vals})`);
        }
      }
      
      await destPool.query('SET FOREIGN_KEY_CHECKS = 1');
      console.log(`  ✓ Migrated ${rows.length} rows`);
    }

    console.log('\n✅ Migration complete');
  } catch (err) {
    console.error('❌ Migration failed:', err.message);
  } finally {
    await sourcePool.end();
    await destPool.end();
  }
}

migrateData();
EOF

# Run migration
source .env  # Load environment variables
export SOURCE_DB_HOST=railway-host
export SOURCE_DB_USER=railway_user
export SOURCE_DB_PASSWORD=railway_password
export SOURCE_DB_NAME=railway_db

node scripts/migrate-data.js
```

---

## Seeding & Initial Data

### Step 1: Create Initial Settings

```bash
mysql -h your-new-server-ip -u caa_recruitment_user -p caa_recruitment << 'EOF'
INSERT INTO settings 
  (org_name, email_sender_name, min_age_threshold, allow_external_internal_jobs, 
   session_timeout_minutes, closing_soon_days, max_applications_per_candidate)
VALUES 
  ('Uganda Civil Aviation Authority', 'CAA HR Team', 21, 0, 30, 7, 5)
ON DUPLICATE KEY UPDATE
  org_name = VALUES(org_name);
EOF
```

### Step 2: Seed Departments

```bash
node scripts/seed-departments.js
```

### Step 3: Seed Admin Users

```bash
node scripts/seed-admins.js
```

### Step 4: Seed Staff Directory

```bash
node scripts/seed-staff.js
```

### Step 5: Seed Job Templates

```bash
node scripts/seed-job-templates.js
```

---

## Verification Checklist

Run these tests to verify the database is properly configured:

### Database Connectivity
```bash
npm test -- tests/database-connection.test.js
```

### Schema Integrity
```bash
# Verify all required tables exist
mysql -h your-new-server-ip -u caa_recruitment_user -p caa_recruitment -e "SHOW TABLES;" | wc -l
# Should output: 17 (or 18 with the header line)

# Verify key constraints
mysql -h your-new-server-ip -u caa_recruitment_user -p caa_recruitment -e "
  SELECT TABLE_NAME, CONSTRAINT_TYPE 
  FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS 
  WHERE TABLE_SCHEMA='caa_recruitment';
"
```

### Data Integrity
```bash
# Check for orphaned records (applications without jobs)
mysql -h your-new-server-ip -u caa_recruitment_user -p caa_recruitment -e "
  SELECT COUNT(*) as orphaned_applications 
  FROM applications a 
  LEFT JOIN jobs j ON a.job_id = j.id 
  WHERE j.id IS NULL;
"

# Check admin users exist
mysql -h your-new-server-ip -u caa_recruitment_user -p caa_recruitment -e "
  SELECT COUNT(*) as admin_count 
  FROM users 
  WHERE account_type = 'admin';
"

# Check staff records exist
mysql -h your-new-server-ip -u caa_recruitment_user -p caa_recruitment -e "
  SELECT COUNT(*) as staff_count 
  FROM staff;
"
```

### API Connectivity
```bash
# Start the backend with new database
npm start

# Test health endpoint
curl http://localhost:5000/api/settings

# Test login endpoint
curl -X POST http://localhost:5000/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@caa.co.ug","password":"Admin@2026"}'
```

---

## Backup & Recovery

### Daily Backup Strategy

```bash
# Create backup script: backup.sh
#!/bin/bash
BACKUP_DIR="/backups/caa-recruitment"
TIMESTAMP=$(date +%Y%m%d-%H%M%S)
BACKUP_FILE="$BACKUP_DIR/caa-recruitment-$TIMESTAMP.sql.gz"

mkdir -p $BACKUP_DIR

mysqldump -h $DB_HOST -u $DB_USER -p$DB_PASSWORD $DB_NAME | gzip > $BACKUP_FILE

# Keep only last 30 days
find $BACKUP_DIR -name "*.sql.gz" -mtime +30 -delete

echo "✅ Backup completed: $BACKUP_FILE"

# Schedule with cron (backup daily at 2 AM)
# 0 2 * * * /opt/caa-recruitment/backup.sh
```

### Point-in-Time Recovery

```bash
# 1. Stop the application
systemctl stop caa-recruitment-backend

# 2. Restore from backup
gunzip < caa-recruitment-2026-07-30-020000.sql.gz | \
  mysql -h your-new-server-ip -u caa_recruitment_user -p caa_recruitment

# 3. Verify restoration
mysql -h your-new-server-ip -u caa_recruitment_user -p caa_recruitment -e "SELECT COUNT(*) FROM applications;"

# 4. Restart application
systemctl start caa-recruitment-backend
```

---

## Troubleshooting

### Connection Issues

**Error:** `Error: connect ECONNREFUSED`

```bash
# Check if MySQL is running
systemctl status mysql  # or: systemctl status mariadb

# Check if port 3306 is open
sudo netstat -tlnp | grep 3306

# Test connection manually
mysql -h your-new-server-ip -u caa_recruitment_user -p -e "SELECT 1;"
```

**Error:** `Access denied for user 'caa_recruitment_user'`

```bash
# Verify user exists
mysql -u root -p -e "SELECT User, Host FROM mysql.user WHERE User='caa_recruitment_user';"

# Reset password
mysql -u root -p -e "ALTER USER 'caa_recruitment_user'@'%' IDENTIFIED BY 'new-password';"
FLUSH PRIVILEGES;
```

### Schema Issues

**Error:** `Table 'caa_recruitment.users' doesn't exist`

```bash
# Run migration script again
node scripts/migrate.js

# Or check if database exists
mysql -u root -p -e "SHOW DATABASES LIKE 'caa_recruitment';"
```

**Error:** `Duplicate entry for key 'PRIMARY'`

```bash
# Check if data already exists
mysql -h your-new-server-ip -u caa_recruitment_user -p caa_recruitment -e "
  SELECT TABLE_NAME, TABLE_ROWS 
  FROM INFORMATION_SCHEMA.TABLES 
  WHERE TABLE_SCHEMA='caa_recruitment';
"

# If importing duplicate data, either:
# 1. Truncate tables: TRUNCATE TABLE users; TRUNCATE TABLE jobs; ...
# 2. Use INSERT ... ON DUPLICATE KEY UPDATE in migration script
```

### Performance Issues

**Slow Queries:**

```bash
# Enable slow query log
mysql -u root -p -e "
  SET GLOBAL slow_query_log = 'ON';
  SET GLOBAL long_query_time = 2;
"

# Check slow log
mysql -u root -p -e "SHOW VARIABLES LIKE 'slow_query_log_file';"
tail -f /var/log/mysql/slow.log

# Add indexes if needed
mysql -h your-new-server-ip -u caa_recruitment_user -p caa_recruitment -e "
  ALTER TABLE applications ADD INDEX idx_status (status);
  ALTER TABLE applications ADD INDEX idx_job_id (job_id);
  ALTER TABLE applications ADD INDEX idx_candidate_email (candidate_email);
"
```

**High Memory Usage:**

```bash
# Check pool settings in .env
DB_POOL_SIZE=10       # connections per pool
DB_POOL_WAIT_FOR_CONNECTIONS=true
DB_POOL_CONNECTION_LIMIT=20  # max total connections

# Reduce if needed:
DB_POOL_SIZE=5
DB_POOL_CONNECTION_LIMIT=10
```

---

## Post-Migration Checklist

- [ ] Database created and user configured
- [ ] All 17 tables exist with correct structure
- [ ] Data migrated from Railway (if applicable)
- [ ] Admin users seeded and can log in
- [ ] Staff directory populated
- [ ] Job templates created
- [ ] Settings initialized
- [ ] Backup strategy implemented
- [ ] API health endpoint responding
- [ ] Frontend can connect to backend
- [ ] SSL/TLS certificates installed
- [ ] Database port secured (not exposed publicly)
- [ ] Connection pooling configured
- [ ] Slow query logging enabled
- [ ] Regular backups scheduled

---

## Emergency Contacts

- **Database Admin:** [Contact Info]
- **Backend Deployment:** [Contact Info]
- **On-Call Support:** [Contact Info]

---

**Document Version:** 1.0  
**Last Updated:** July 30, 2026  
**Next Review:** August 30, 2026
