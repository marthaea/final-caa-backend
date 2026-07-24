// scripts/seed-job-templates.js — seeds a handful of starter job templates by
// role family, so HR isn't starting from a blank page the first time they use
// the template picker in job creation. Non-destructive: only inserts templates
// whose name doesn't already exist.
//
// Usage: node scripts/seed-job-templates.js

require('dotenv').config();
const pool = require('../config/db');

const TEMPLATES = [
  {
    name: 'Technical / Engineering role',
    content: {
      aboutRole: 'Provide technical expertise and oversight in support of UCAA\'s operational and regulatory mandate, ensuring work is carried out to the applicable engineering, safety and regulatory standards.',
      accountabilities: [
        { area: 'Technical delivery', activities: ['Plan and execute assigned technical work to specification and schedule', 'Maintain accurate technical records and documentation'] },
        { area: 'Compliance & safety', activities: ['Ensure all work complies with relevant civil aviation and engineering standards', 'Identify and report safety or compliance risks promptly'] },
      ],
      specialSkills: ['Technical report writing', 'Relevant regulatory standards', 'Problem-solving under operational pressure'],
      requiredQualification: 'Degree',
      minAge: 23,
      requiredExperience: 3,
      requirements: [
        { id: 'seed-1', kind: 'qualificationLevel', label: 'Must hold at least a Degree in a relevant engineering/technical field', gradeValue: 'Degree', usage: 'disqualifier', mandatory: true },
        { id: 'seed-2', kind: 'experienceYears', label: 'Must have at least 3 years of relevant technical experience', numberValue: 3, usage: 'qualifier', mandatory: true },
      ],
    },
  },
  {
    name: 'Professional / Administrative role',
    content: {
      aboutRole: 'Deliver professional support services within the department, ensuring efficient administration and compliance with UCAA policy and procedure.',
      accountabilities: [
        { area: 'Service delivery', activities: ['Handle assigned administrative and professional tasks accurately and on time', 'Prepare correspondence, reports and records as required'] },
        { area: 'Stakeholder support', activities: ['Respond to internal and external stakeholder queries professionally', 'Support departmental planning and reporting cycles'] },
      ],
      specialSkills: ['MS Office proficiency', 'Written and verbal communication', 'Organisational skills'],
      requiredQualification: 'Degree',
      minAge: 23,
      requiredExperience: 2,
      requirements: [
        { id: 'seed-1', kind: 'qualificationLevel', label: 'Must hold at least a Degree in a relevant field', gradeValue: 'Degree', usage: 'disqualifier', mandatory: true },
        { id: 'seed-2', kind: 'experienceYears', label: 'Must have at least 2 years of relevant experience', numberValue: 2, usage: 'qualifier', mandatory: true },
      ],
    },
  },
  {
    name: 'Graduate Entry / Internship role',
    content: {
      aboutRole: 'A structured entry-level opportunity for recent graduates to build practical experience within UCAA under the guidance of experienced staff.',
      accountabilities: [
        { area: 'Learning & development', activities: ['Complete assigned tasks under supervision to build practical competence', 'Participate actively in structured training and mentorship'] },
        { area: 'Support duties', activities: ['Assist the team with day-to-day operational or administrative tasks as assigned'] },
      ],
      specialSkills: ['Willingness to learn', 'Basic computer literacy', 'Team collaboration'],
      requiredQualification: 'Degree',
      minAge: 21,
      requiredExperience: 0,
      requirements: [
        { id: 'seed-1', kind: 'qualificationLevel', label: 'Must hold at least a Degree in a relevant field', gradeValue: 'Degree', usage: 'disqualifier', mandatory: true },
        { id: 'seed-2', kind: 'custom', label: 'Must be a recent graduate (within the last 3 years)', usage: 'qualifier', mandatory: true },
      ],
    },
  },
];

async function main() {
  console.log(`Connecting to ${process.env.DB_NAME}@${process.env.DB_HOST} ...`);
  let added = 0, skipped = 0;

  for (const t of TEMPLATES) {
    const [existing] = await pool.query('SELECT id FROM job_templates WHERE name = ?', [t.name]);
    if (existing.length > 0) {
      skipped++;
      continue;
    }
    await pool.query(
      'INSERT INTO job_templates (name, content) VALUES (?, ?)',
      [t.name, JSON.stringify(t.content)]
    );
    console.log(`  added template — ${t.name}`);
    added++;
  }

  console.log(`Done. ${added} added, ${skipped} already existed.`);
  process.exit(0);
}

main().catch((err) => {
  console.error('Failed:', err.message);
  process.exit(1);
});
