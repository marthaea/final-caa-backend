// add-vacancies.js — inserts 8 more aviation job vacancies into Railway MySQL
// Usage: node add-vacancies.js <host> <port> <password>

const mysql = require('mysql2/promise');

const [,, HOST, PORT, PASSWORD] = process.argv;
if (!HOST || !PORT || !PASSWORD) {
  console.error('Usage: node add-vacancies.js <host> <port> <password>');
  process.exit(1);
}

const JOBS = [
  {
    abbr: 'ATS-02', title: 'Air Traffic Controller', dept: 'Air Traffic Management',
    dept_key: 'atm', salary: 'UGX 4,200,000 – 5,800,000', salary_band: 'UG4',
    type: 'Full-time', closes: '15 Aug 2026', closes_at: '2026-08-15',
    visibility: 'external', min_age: 23, required_experience: 2,
    required_qualification: 'Degree',
    description: 'Provide safe, orderly, and expeditious flow of air traffic at Entebbe International Airport. Issue clearances and instructions to aircraft, monitor radar displays, and coordinate with adjacent ATC units. Must hold or be eligible for an ICAO ATC licence.',
    featured: 1,
  },
  {
    abbr: 'AVI-03', title: 'Aviation Safety Inspector (Operations)', dept: 'Aviation Safety',
    dept_key: 'safety', salary: 'UGX 3,800,000 – 5,000,000', salary_band: 'UG4',
    type: 'Full-time', closes: '20 Aug 2026', closes_at: '2026-08-20',
    visibility: 'external', min_age: 25, required_experience: 3,
    required_qualification: 'Degree',
    description: 'Conduct safety audits, ramp inspections, and regulatory oversight of airline operations in Uganda. Investigate incidents, review safety management systems, and issue corrective action notices. Commercial Pilot Licence (CPL) or Air Transport Pilot Licence (ATPL) required.',
    featured: 0,
  },
  {
    abbr: 'ENG-04', title: 'Aeronautical Engineer (Airworthiness)', dept: 'Engineering',
    dept_key: 'engineering', salary: 'UGX 3,500,000 – 4,800,000', salary_band: 'UG4',
    type: 'Full-time', closes: '25 Aug 2026', closes_at: '2026-08-25',
    visibility: 'external', min_age: 24, required_experience: 2,
    required_qualification: 'Degree',
    description: 'Perform continuing airworthiness reviews, approve maintenance programmes, and oversee aircraft type certification for the Ugandan register. Liaise with EASA and ICAO on technical standards. Degree in Aeronautical or Mechanical Engineering required; EASA Part-66 licence is an advantage.',
    featured: 0,
  },
  {
    abbr: 'MET-01', title: 'Meteorological Officer', dept: 'Meteorology',
    dept_key: 'met', salary: 'UGX 2,800,000 – 3,600,000', salary_band: 'UG3',
    type: 'Full-time', closes: '30 Aug 2026', closes_at: '2026-08-30',
    visibility: 'external', min_age: 22, required_experience: 1,
    required_qualification: 'Degree',
    description: 'Prepare and disseminate meteorological information for aviation operations at Entebbe International and up-country aerodromes. Issue TAFs, METARs, SIGMETs, and aerodrome warnings. Degree in Meteorology, Atmospheric Science, or Physics required.',
    featured: 0,
  },
  {
    abbr: 'ICT-05', title: 'Systems Administrator (CNS/ATM)', dept: 'ICT & Systems',
    dept_key: 'ict', salary: 'UGX 3,200,000 – 4,200,000', salary_band: 'UG3',
    type: 'Full-time', closes: '5 Sep 2026', closes_at: '2026-09-05',
    visibility: 'external', min_age: 22, required_experience: 2,
    required_qualification: 'Degree',
    description: 'Administer and maintain Communication, Navigation, and Surveillance (CNS) ground systems including VHF radio, ILS, VOR/DME, and ADS-B networks. Provide 24/7 system support, coordinate with ANSP partners, and maintain technical documentation. IT or Telecommunications engineering background required.',
    featured: 0,
  },
  {
    abbr: 'FIN-03', title: 'Senior Finance Officer', dept: 'Finance & Administration',
    dept_key: 'finance', salary: 'UGX 3,600,000 – 4,600,000', salary_band: 'UG4',
    type: 'Full-time', closes: '10 Sep 2026', closes_at: '2026-09-10',
    visibility: 'external', min_age: 25, required_experience: 4,
    required_qualification: 'Degree',
    description: 'Oversee financial reporting, budget preparation, and expenditure control for the Authority. Prepare monthly management accounts, coordinate external audits, and ensure compliance with PFMA and IPSAS standards. CPA (Uganda) or ACCA qualification required with at least 4 years of relevant experience.',
    featured: 0,
  },
  {
    abbr: 'LEG-02', title: 'Legal Counsel', dept: 'Legal Affairs',
    dept_key: 'legal', salary: 'UGX 4,000,000 – 5,200,000', salary_band: 'UG4',
    type: 'Full-time', closes: '15 Sep 2026', closes_at: '2026-09-15',
    visibility: 'external', min_age: 27, required_experience: 5,
    required_qualification: 'Degree',
    description: 'Provide legal advice on aviation regulatory matters, contract negotiations, and institutional governance. Draft and review statutory instruments, bilateral air service agreements, and corporate contracts. Bachelor of Laws (LLB) with a Post-Graduate Diploma in Legal Practice required; aviation law experience is an advantage.',
    featured: 0,
  },
  {
    abbr: 'HRD-04', title: 'Human Resource Officer (Talent Acquisition)', dept: 'Human Resources',
    dept_key: 'hr', salary: 'UGX 2,600,000 – 3,400,000', salary_band: 'UG3',
    type: 'Full-time', closes: '20 Sep 2026', closes_at: '2026-09-20',
    visibility: 'external', min_age: 22, required_experience: 2,
    required_qualification: 'Degree',
    description: 'Manage the end-to-end recruitment lifecycle including job advertising, shortlisting, interview coordination, and on-boarding. Maintain the e-Recruitment portal, update job descriptions, and ensure compliance with HR policies and labour laws. Degree in Human Resource Management or related field required.',
    featured: 0,
  },
];

async function main() {
  const conn = await mysql.createConnection({
    host: HOST, port: parseInt(PORT),
    user: 'root', password: PASSWORD, database: 'railway',
    ssl: { rejectUnauthorized: false },
  });

  console.log('Connected. Inserting vacancies...\n');

  for (const j of JOBS) {
    try {
      const [result] = await conn.execute(
        `INSERT INTO jobs
           (abbr,title,dept,dept_key,location,salary,salary_band,type,closes,closes_at,
            visibility,min_age,required_experience,required_qualification,description,featured,created_by)
         VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,NULL)`,
        [j.abbr, j.title, j.dept, j.dept_key, 'Entebbe, Uganda',
         j.salary, j.salary_band, j.type, j.closes, j.closes_at,
         j.visibility, j.min_age, j.required_experience,
         j.required_qualification, j.description, j.featured]
      );
      console.log(`  ✓ [${j.abbr}] ${j.title}`);
    } catch (e) {
      if (e.message.includes('Duplicate entry')) {
        console.log(`  — [${j.abbr}] already exists, skipped`);
      } else {
        console.warn(`  ✗ [${j.abbr}] ${e.message}`);
      }
    }
  }

  const [[{ total }]] = await conn.execute('SELECT COUNT(*) AS total FROM jobs');
  console.log(`\nDone. Total jobs in database: ${total}`);
  await conn.end();
}

main().catch(err => { console.error('Failed:', err.message); process.exit(1); });
