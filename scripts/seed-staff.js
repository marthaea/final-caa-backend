// scripts/seed-staff.js — inserts a realistic internal staff register into the
// `staff` table. Like seed-admins.js, this only adds missing rows (matched by
// employee_number) — it never truncates or deletes, so it's safe to run
// against a database that already has real data in it.
//
// Usage: node scripts/seed-staff.js

require('dotenv').config();
const pool = require('../config/db');

const STAFF = [
  // Air Traffic Mgmt
  { empNo: 'CAA-1001', firstName: 'Sarah',     lastName: 'Namutebi',    dept: 'Air Traffic Mgmt',   position: 'Director',       joined: '2014-03-10' },
  { empNo: 'CAA-1002', firstName: 'James',     lastName: 'Okello',      dept: 'Air Traffic Mgmt',   position: 'Senior Officer', joined: '2016-06-01' },
  { empNo: 'CAA-1003', firstName: 'Patricia',  lastName: 'Akello',      dept: 'Air Traffic Mgmt',   position: 'Officer',        joined: '2019-09-15' },
  // Aviation Safety
  { empNo: 'CAA-1004', firstName: 'Robert',    lastName: 'Ssebayiga',   dept: 'Aviation Safety',    position: 'Manager',        joined: '2015-01-20' },
  { empNo: 'CAA-1005', firstName: 'Grace',     lastName: 'Atim',        dept: 'Aviation Safety',    position: 'Senior Officer', joined: '2017-11-05' },
  { empNo: 'CAA-1006', firstName: 'David',     lastName: 'Mugisha',     dept: 'Aviation Safety',    position: 'Officer',        joined: '2020-02-12' },
  // Finance & Admin
  { empNo: 'CAA-1007', firstName: 'Florence',  lastName: 'Nansubuga',   dept: 'Finance & Admin',    position: 'Manager',        joined: '2014-08-18' },
  { empNo: 'CAA-1008', firstName: 'Charles',   lastName: 'Opio',        dept: 'Finance & Admin',    position: 'Analyst',        joined: '2018-04-22' },
  { empNo: 'CAA-1009', firstName: 'Anita',     lastName: 'Nakazibwe',   dept: 'Finance & Admin',    position: 'Officer',        joined: '2021-07-01' },
  // ICT & Systems
  { empNo: 'CAA-1010', firstName: 'Peter',     lastName: 'Wanyama',     dept: 'ICT & Systems',      position: 'Manager',        joined: '2016-10-10' },
  { empNo: 'CAA-1011', firstName: 'Christine', lastName: 'Nassali',     dept: 'ICT & Systems',      position: 'Specialist',     joined: '2019-03-25' },
  { empNo: 'CAA-1012', firstName: 'Joseph',    lastName: 'Abalo',       dept: 'ICT & Systems',      position: 'Officer',        joined: '2022-01-14' },
  // Legal
  { empNo: 'CAA-1013', firstName: 'Esther',    lastName: 'Nakiganda',   dept: 'Legal',              position: 'Director',       joined: '2013-05-06' },
  { empNo: 'CAA-1014', firstName: 'Moses',     lastName: 'Kiggundu',    dept: 'Legal',              position: 'Officer',        joined: '2020-09-09' },
  { empNo: 'CAA-1015', firstName: 'Agnes',     lastName: 'Achola',      dept: 'Legal',              position: 'Coordinator',    joined: '2021-11-03' },
  // Operations
  { empNo: 'CAA-1016', firstName: 'Deborah',   lastName: 'Naigaga',     dept: 'Operations',         position: 'Manager',        joined: '2015-06-15' },
  { empNo: 'CAA-1017', firstName: 'Michael',   lastName: 'Tumwesigye',  dept: 'Operations',         position: 'Senior Officer', joined: '2017-02-20' },
  { empNo: 'CAA-1018', firstName: 'Irene',     lastName: 'Kansiime',    dept: 'Operations',         position: 'Officer',        joined: '2019-12-01' },
  // Human Resources
  { empNo: 'CAA-1019', firstName: 'Samuel',    lastName: 'Byaruhanga',  dept: 'Human Resources',    position: 'Manager',        joined: '2014-11-11' },
  { empNo: 'CAA-1020', firstName: 'Judith',    lastName: 'Nabatanzi',   dept: 'Human Resources',    position: 'Officer',        joined: '2018-08-08' },
  { empNo: 'CAA-1021', firstName: 'Emmanuel',  lastName: 'Kato',        dept: 'Human Resources',    position: 'Coordinator',    joined: '2022-04-18' },
  // Procurement
  { empNo: 'CAA-1022', firstName: 'Rebecca',   lastName: 'Namusisi',    dept: 'Procurement',        position: 'Manager',        joined: '2016-01-25' },
  { empNo: 'CAA-1023', firstName: 'Isaac',     lastName: 'Mwesigwa',    dept: 'Procurement',        position: 'Officer',        joined: '2020-05-05' },
  { empNo: 'CAA-1024', firstName: 'Betty',     lastName: 'Nabirye',     dept: 'Procurement',        position: 'Assistant',      joined: '2023-02-01' },
  // Engineering
  { empNo: 'CAA-1025', firstName: 'Vincent',   lastName: 'Odongo',      dept: 'Engineering',        position: 'Director',       joined: '2013-09-30' },
  { empNo: 'CAA-1026', firstName: 'Diana',     lastName: 'Kyomuhendo',  dept: 'Engineering',        position: 'Senior Officer', joined: '2017-07-07' },
  { empNo: 'CAA-1027', firstName: 'Francis',   lastName: 'Ariong',      dept: 'Engineering',        position: 'Officer',        joined: '2021-03-19' },
  // Communications
  { empNo: 'CAA-1028', firstName: 'Harriet',   lastName: 'Nakalembe',   dept: 'Communications',     position: 'Manager',        joined: '2018-10-14' },
  { empNo: 'CAA-1029', firstName: 'Patrick',   lastName: 'Ochieng',     dept: 'Communications',     position: 'Specialist',     joined: '2020-06-22' },
  { empNo: 'CAA-1030', firstName: 'Winnie',    lastName: 'Nabukenya',   dept: 'Communications',     position: 'Officer',        joined: '2022-08-30' },
];

async function main() {
  console.log(`Connecting to ${process.env.DB_NAME}@${process.env.DB_HOST} ...`);
  let added = 0, skipped = 0;

  for (const s of STAFF) {
    const [existing] = await pool.query(
      'SELECT id FROM staff WHERE employee_number = ?', [s.empNo]
    );
    if (existing.length > 0) {
      skipped++;
      continue;
    }
    const email = `${s.firstName.toLowerCase()}.${s.lastName.toLowerCase()}@caa.go.ug`;
    await pool.query(
      `INSERT INTO staff (employee_number, first_name, last_name, dept, position, email, joined_date, status)
       VALUES (?, ?, ?, ?, ?, ?, ?, 'Active')`,
      [s.empNo, s.firstName, s.lastName, s.dept, s.position, email, s.joined]
    );
    console.log(`  added ${s.empNo} — ${s.firstName} ${s.lastName} (${s.dept})`);
    added++;
  }

  console.log(`Done. ${added} added, ${skipped} already existed.`);
  process.exit(0);
}

main().catch((err) => {
  console.error('Failed:', err.message);
  process.exit(1);
});
