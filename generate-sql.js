// generate-sql.js
// Generates a complete SQL dump file (caa-seed.sql) that you can import
// via phpMyAdmin on AlwaysData (or any MySQL host).
//
// Usage:
//   node generate-sql.js
//
// Output: caa-seed.sql  (import this file in phpMyAdmin → Import tab)

const bcrypt = require('bcryptjs');
const fs     = require('fs');

// ─── LCG — identical to seed.js ──────────────────────────────────────────────
function lcg(s) { return (((s * 1664525) + 1013904223) >>> 0); }

function mysqlStr(val) {
  if (val === null || val === undefined) return 'NULL';
  return "'" + String(val).replace(/\\/g, '\\\\').replace(/'/g, "\\'").replace(/\n/g, '\\n').replace(/\r/g, '\\r') + "'";
}
function mysqlNum(val) { return val === null || val === undefined ? 'NULL' : String(val); }

function toMysqlDate(displayStr) {
  const d = new Date(displayStr);
  const p = n => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth()+1)}-${p(d.getDate())} 00:00:00`;
}
function toMysqlDateFromTs(ts) {
  const d = new Date(ts);
  const p = n => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth()+1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`;
}

// ─── Jobs ─────────────────────────────────────────────────────────────────────
const JOBS = [
  { id:1,  abbr:'ATC',  title:'Senior Air Traffic Controller',                        dept:'Air Traffic Mgmt', dept_key:'atm',     location:'Entebbe Airport', salary:'UGX 4.2M–5.8M', salary_band:'UG4', type:'Full-time', closes:'Jun 15, 2026', closes_at:'2026-06-15', visibility:'external', min_age:25, required_experience:5,  required_qualification:'Degree',  featured:1, description:'Direct en-route and approach traffic at Entebbe ACC.' },
  { id:2,  abbr:'ASI',  title:'Principal Safety Inspector (Airworthiness)',           dept:'Aviation Safety',  dept_key:'safety',  location:'Kampala HQ',      salary:'UGX 3.8M–5.2M', salary_band:'UG3', type:'Full-time', closes:'Jun 20, 2026', closes_at:'2026-06-20', visibility:'external', min_age:28, required_experience:7,  required_qualification:'Degree',  featured:1, description:null },
  { id:3,  abbr:'SYS',  title:'Systems Administrator',                                dept:'ICT & Systems',   dept_key:'ict',     location:'Kampala HQ',      salary:'UGX 2.6M–3.5M', salary_band:'UG5', type:'Full-time', closes:'Jul 1, 2026',  closes_at:'2026-07-01', visibility:'external', min_age:23, required_experience:3,  required_qualification:'Degree',  featured:0, description:null },
  { id:4,  abbr:'FIN',  title:'Finance Officer (Revenue Assurance)',                  dept:'Finance & Admin', dept_key:'finance', location:'Kampala HQ',      salary:'UGX 2.8M–3.6M', salary_band:'UG5', type:'Contract',  closes:'Jun 30, 2026', closes_at:'2026-06-30', visibility:'external', min_age:25, required_experience:4,  required_qualification:'Degree',  featured:0, description:null },
  { id:5,  abbr:'LEG',  title:'Legal Counsel (Aviation Regulations)',                 dept:'Legal',           dept_key:'legal',   location:'Kampala HQ',      salary:'UGX 3.2M–4.4M', salary_band:'UG4', type:'Full-time', closes:'Jul 10, 2026', closes_at:'2026-07-10', visibility:'external', min_age:27, required_experience:5,  required_qualification:'Masters', featured:0, description:null },
  { id:6,  abbr:'ATT',  title:'ATC Trainee (Graduate Entry)',                         dept:'Air Traffic Mgmt', dept_key:'atm',    location:'Entebbe Airport', salary:'UGX 1.8M–2.4M', salary_band:'UG7', type:'Full-time', closes:'Jul 15, 2026', closes_at:'2026-07-15', visibility:'external', min_age:21, required_experience:0,  required_qualification:'Degree',  featured:0, description:null },
  { id:7,  abbr:'INT',  title:'Internal — Manager, Aerodrome Operations',             dept:'Operations',      dept_key:'ops',     location:'Entebbe Airport', salary:'UGX 5.5M–7.0M', salary_band:'UG2', type:'Full-time', closes:'Jun 25, 2026', closes_at:'2026-06-25', visibility:'internal', min_age:30, required_experience:8,  required_qualification:'Masters', featured:0, description:'Open to verified CAA staff only.' },
  { id:8,  abbr:'ACO',  title:'Approach Control Officer',                             dept:'Air Traffic Mgmt', dept_key:'atm',    location:'Entebbe Airport', salary:'UGX 3.5M–4.8M', salary_band:'UG4', type:'Full-time', closes:'Jul 20, 2026', closes_at:'2026-07-20', visibility:'external', min_age:24, required_experience:3,  required_qualification:'Degree',  featured:1, description:null },
  { id:9,  abbr:'FOI',  title:'Flight Operations Inspector',                          dept:'Aviation Safety', dept_key:'safety',  location:'Kampala HQ',      salary:'UGX 3.2M–4.5M', salary_band:'UG4', type:'Full-time', closes:'Jul 5, 2026',  closes_at:'2026-07-05', visibility:'external', min_age:28, required_experience:6,  required_qualification:'Degree',  featured:0, description:null },
  { id:10, abbr:'DGI',  title:'Dangerous Goods Inspector',                            dept:'Aviation Safety', dept_key:'safety',  location:'Entebbe Airport', salary:'UGX 2.8M–3.8M', salary_band:'UG5', type:'Full-time', closes:'Jul 8, 2026',  closes_at:'2026-07-08', visibility:'external', min_age:25, required_experience:4,  required_qualification:'Degree',  featured:0, description:null },
  { id:11, abbr:'ASec', title:'Aviation Security Inspector',                          dept:'Aviation Safety', dept_key:'safety',  location:'Entebbe Airport', salary:'UGX 2.9M–3.9M', salary_band:'UG5', type:'Full-time', closes:'Jul 12, 2026', closes_at:'2026-07-12', visibility:'external', min_age:25, required_experience:4,  required_qualification:'Degree',  featured:0, description:null },
  { id:12, abbr:'PRO',  title:'Procurement Officer',                                  dept:'Finance & Admin', dept_key:'finance', location:'Kampala HQ',      salary:'UGX 2.4M–3.2M', salary_band:'UG6', type:'Contract',  closes:'Jul 3, 2026',  closes_at:'2026-07-03', visibility:'external', min_age:23, required_experience:2,  required_qualification:'Degree',  featured:0, description:null },
  { id:13, abbr:'NET',  title:'Network Engineer',                                     dept:'ICT & Systems',   dept_key:'ict',     location:'Kampala HQ',      salary:'UGX 2.8M–3.7M', salary_band:'UG5', type:'Full-time', closes:'Jul 18, 2026', closes_at:'2026-07-18', visibility:'external', min_age:24, required_experience:3,  required_qualification:'Degree',  featured:0, description:null },
  { id:14, abbr:'AIS',  title:'Internal — Principal, Aeronautical Information Services', dept:'Operations', dept_key:'ops',      location:'Entebbe Airport', salary:'UGX 4.5M–6.0M', salary_band:'UG3', type:'Full-time', closes:'Jul 22, 2026', closes_at:'2026-07-22', visibility:'internal', min_age:28, required_experience:6,  required_qualification:'Degree',  featured:0, description:'Open to verified CAA staff only.' },
];

// ─── Staff ────────────────────────────────────────────────────────────────────
const STAFF = [
  { employee_number:'CAA-1001', first_name:'Sarah',     last_name:'Namutebi',   dept:'Air Traffic Mgmt', position:'Director',       email:'sarahnamutebi@caa.go.ug',    joined_date:'2014-01-01', status:'Active' },
  { employee_number:'CAA-1002', first_name:'James',     last_name:'Okello',     dept:'Aviation Safety',  position:'Manager',        email:'jamesokello@caa.go.ug',      joined_date:'2015-02-01', status:'Active' },
  { employee_number:'CAA-1003', first_name:'Patricia',  last_name:'Akello',     dept:'Finance & Admin',  position:'Senior Officer', email:'patriciaakello@caa.go.ug',   joined_date:'2016-03-01', status:'Active' },
  { employee_number:'CAA-1004', first_name:'Robert',    last_name:'Ssebayiga',  dept:'ICT & Systems',    position:'Officer',        email:'robertssebayiga@caa.go.ug',  joined_date:'2017-04-01', status:'Active' },
  { employee_number:'CAA-1005', first_name:'Grace',     last_name:'Atim',       dept:'Legal',            position:'Analyst',        email:'graceatim@caa.go.ug',        joined_date:'2018-05-01', status:'Active' },
  { employee_number:'CAA-1006', first_name:'David',     last_name:'Mugisha',    dept:'Operations',       position:'Coordinator',    email:'davidmugisha@caa.go.ug',     joined_date:'2019-06-01', status:'Active' },
  { employee_number:'CAA-1007', first_name:'Florence',  last_name:'Nansubuga',  dept:'Human Resources',  position:'Specialist',     email:'florencenansubuga@caa.go.ug', joined_date:'2020-07-01', status:'Active' },
  { employee_number:'CAA-1008', first_name:'Charles',   last_name:'Opio',       dept:'Procurement',      position:'Assistant',      email:'charlesopio@caa.go.ug',      joined_date:'2021-08-01', status:'Active' },
  { employee_number:'CAA-1009', first_name:'Anita',     last_name:'Nakazibwe',  dept:'Engineering',      position:'Director',       email:'anitanakazibwe@caa.go.ug',   joined_date:'2022-09-01', status:'Active' },
  { employee_number:'CAA-1010', first_name:'Peter',     last_name:'Wanyama',    dept:'Communications',   position:'Manager',        email:'peterwanyama@caa.go.ug',     joined_date:'2014-10-01', status:'Active' },
  { employee_number:'CAA-1011', first_name:'Christine', last_name:'Nassali',    dept:'Air Traffic Mgmt', position:'Senior Officer', email:'christinenassali@caa.go.ug', joined_date:'2015-11-01', status:'Active' },
  { employee_number:'CAA-1012', first_name:'Joseph',    last_name:'Abalo',      dept:'Aviation Safety',  position:'Officer',        email:'josephabalo@caa.go.ug',      joined_date:'2016-12-01', status:'Active' },
  { employee_number:'CAA-1013', first_name:'Esther',    last_name:'Nakiganda',  dept:'Finance & Admin',  position:'Analyst',        email:'esthernakiganda@caa.go.ug',  joined_date:'2017-01-01', status:'Active' },
  { employee_number:'CAA-1014', first_name:'Moses',     last_name:'Kiggundu',   dept:'ICT & Systems',    position:'Coordinator',    email:'moseskiggundu@caa.go.ug',    joined_date:'2018-02-01', status:'Active' },
  { employee_number:'CAA-1015', first_name:'Agnes',     last_name:'Achola',     dept:'Legal',            position:'Specialist',     email:'agnesachola@caa.go.ug',      joined_date:'2019-03-01', status:'Active' },
];

// ─── Users ────────────────────────────────────────────────────────────────────
const ADMIN_USERS = [
  { email:'admin@caa.go.ug',      plainPassword:'Admin@2026',   first_name:'Alex',  last_name:'Mukasa',   account_type:'admin', admin_role:'super',     effective_type:'admin' },
  { email:'hrdirector@caa.go.ug', plainPassword:'HrDir@2026',   first_name:'Jane',  last_name:'Mirembe',  account_type:'admin', admin_role:'hr',        effective_type:'admin' },
  { email:'recruit@caa.go.ug',    plainPassword:'Recruit@2026', first_name:'David', last_name:'Ssempala', account_type:'admin', admin_role:'recruiter', effective_type:'admin' },
];
const CANDIDATE_USERS = [
  { email:'jbukenya@gmail.com',           plainPassword:'Demo@2026', first_name:'John',   last_name:'Bukenya',   account_type:'external', effective_type:'external' },
  { email:'mauma@gmail.com',              plainPassword:'Demo@2026', first_name:'Mary',   last_name:'Auma',      account_type:'external', effective_type:'external' },
  { email:'pnkutu@gmail.com',             plainPassword:'Demo@2026', first_name:'Peter',  last_name:'Nkutu',     account_type:'external', effective_type:'external' },
  { email:'kssali@student.mak.ac.ug',    plainPassword:'Demo@2026', first_name:'Kevin',  last_name:'Ssali',     account_type:'external', effective_type:'external' },
  { email:'bakello@student.mak.ac.ug',   plainPassword:'Demo@2026', first_name:'Brenda', last_name:'Akello',    account_type:'external', effective_type:'external' },
  { email:'imucunguzi@student.ucu.ac.ug',plainPassword:'Demo@2026', first_name:'Ivan',   last_name:'Mucunguzi', account_type:'external', effective_type:'external' },
  { email:'snabirye@student.must.ac.ug', plainPassword:'Demo@2026', first_name:'Stella', last_name:'Nabirye',   account_type:'external', effective_type:'external' },
  { email:'roulanyah@student.gulu.ac.ug',plainPassword:'Demo@2026', first_name:'Ronald', last_name:'Oulanyah',  account_type:'external', effective_type:'external' },
];

// ─── Pinned & extra applications ──────────────────────────────────────────────
const PINNED_APPS = [
  { id:1,  job_id:1,  abbr:'ATC',  title:'Senior Air Traffic Controller',       dept:'Air Traffic Mgmt',  date:'Jun 3, 2026',  status:'Shortlisted',  completion:100, candidate_name:'John Bukenya',    candidate_email:'jbukenya@gmail.com',           cgpa:null, university:null },
  { id:2,  job_id:4,  abbr:'FIN',  title:'Finance Officer (Revenue Assurance)', dept:'Finance & Admin',   date:'May 28, 2026', status:'Under Review', completion:85,  candidate_name:'Mary Auma',       candidate_email:'mauma@gmail.com',              cgpa:null, university:null },
  { id:3,  job_id:3,  abbr:'SYS',  title:'Systems Administrator',               dept:'ICT & Systems',     date:'May 15, 2026', status:'Pending',      completion:60,  candidate_name:'Peter Nkutu',     candidate_email:'pnkutu@gmail.com',             cgpa:null, university:null },
  { id:4,  job_id:6,  abbr:'ATT',  title:'ATC Trainee (Graduate Entry)',        dept:'Air Traffic Mgmt',  date:'Jun 1, 2026',  status:'Shortlisted',  completion:95,  candidate_name:'Kevin Ssali',     candidate_email:'kssali@student.mak.ac.ug',     cgpa:4.7,  university:'Makerere University' },
  { id:5,  job_id:6,  abbr:'ATT',  title:'ATC Trainee (Graduate Entry)',        dept:'Air Traffic Mgmt',  date:'Jun 2, 2026',  status:'Under Review', completion:90,  candidate_name:'Brenda Akello',   candidate_email:'bakello@student.mak.ac.ug',    cgpa:4.3,  university:'Makerere University' },
  { id:6,  job_id:6,  abbr:'ATT',  title:'ATC Trainee (Graduate Entry)',        dept:'Air Traffic Mgmt',  date:'Jun 3, 2026',  status:'Pending',      completion:80,  candidate_name:'Ivan Mucunguzi',  candidate_email:'imucunguzi@student.ucu.ac.ug', cgpa:3.9,  university:'Uganda Christian University' },
  { id:7,  job_id:6,  abbr:'ATT',  title:'ATC Trainee (Graduate Entry)',        dept:'Air Traffic Mgmt',  date:'Jun 5, 2026',  status:'Pending',      completion:70,  candidate_name:'Stella Nabirye',  candidate_email:'snabirye@student.must.ac.ug',  cgpa:3.6,  university:'Mbarara University' },
  { id:8,  job_id:6,  abbr:'ATT',  title:'ATC Trainee (Graduate Entry)',        dept:'Air Traffic Mgmt',  date:'Jun 7, 2026',  status:'Declined',     completion:55,  candidate_name:'Ronald Oulanyah', candidate_email:'roulanyah@student.gulu.ac.ug', cgpa:2.8,  university:'Gulu University' },
  { id:20, job_id:3,  abbr:'SYS',  title:'Systems Administrator',               dept:'ICT & Systems',     date:'May 20, 2026', status:'Under Review', completion:90,  candidate_name:'John Bukenya',    candidate_email:'jbukenya@gmail.com',           cgpa:null, university:null },
  { id:21, job_id:13, abbr:'NET',  title:'Network Engineer',                    dept:'ICT & Systems',     date:'Jun 10, 2026', status:'Pending',      completion:65,  candidate_name:'John Bukenya',    candidate_email:'jbukenya@gmail.com',           cgpa:null, university:null },
];

// ─── CV profiles ──────────────────────────────────────────────────────────────
const CV_PROFILES = [
  {
    user_email:'jbukenya@gmail.com',
    personal_data: JSON.stringify({ firstName:'John',lastName:'Bukenya',otherName:'',dob:'1990-03-15',gender:'Male',nationality:'Ugandan',nin:'CM79000315BUKJN',phone:'+256 701 234 567',email:'jbukenya@gmail.com',address:'Plot 45, Kisaasi, Kampala' }),
    highest_level:'Degree',
    qualifications: JSON.stringify([{ level:'Degree',course:'BSc Electrical Engineering',institution:'Makerere University',year:'2013' },{ level:'A-Level',course:'PCM',institution:'Namilyango College',year:'2009',school:'Namilyango College' }]),
    skills: JSON.stringify(['Air Traffic Control','Radar Systems','Radio Communication','ICAO Procedures','Emergency Handling','Team Leadership']),
    experience: JSON.stringify([{ title:'Air Traffic Control Officer',organisation:'Uganda Civil Aviation Authority',start:'2014-01-01',end:'2024-12-31',description:'Managed en-route and approach traffic at Entebbe ACC.' }]),
    referees: JSON.stringify([{ name:'Col. Peter Wamala',title:'Director, Air Traffic Management',organisation:'UCAA',phone:'+256 414 352 000',email:'pwamala@caa.go.ug' },{ name:'Dr. Rose Nalwoga',title:'Head of Department',organisation:'Makerere University',phone:'+256 772 900 100',email:'r.nalwoga@mak.ac.ug' }]),
    next_of_kin: JSON.stringify({ name:'Margaret Bukenya',relationship:'Spouse',phone:'+256 772 111 222' }),
  },
  {
    user_email:'mauma@gmail.com',
    personal_data: JSON.stringify({ firstName:'Mary',lastName:'Auma',otherName:'',dob:'1992-07-22',gender:'Female',nationality:'Ugandan',nin:'CF92000722AUMAM',phone:'+256 772 345 678',email:'mauma@gmail.com',address:'Plot 12B, Ntinda, Kampala' }),
    highest_level:'Degree',
    qualifications: JSON.stringify([{ level:'Degree',course:'Bachelor of Commerce (Accounting)',institution:'Kyambogo University',year:'2015' },{ level:'A-Level',course:'ECA',institution:"St Mary's College Namagunga",year:'2011' }]),
    skills: JSON.stringify(['Financial Analysis','Revenue Assurance','IFRS Reporting','MS Excel','QuickBooks','Budget Planning','Internal Audit']),
    experience: JSON.stringify([{ title:'Finance Analyst',organisation:'Stanbic Bank Uganda',start:'2016-03-01',end:'2024-01-31',description:'Led revenue assurance audits and produced monthly financial reports.' }]),
    referees: JSON.stringify([{ name:'Mr. Charles Kiggundu',title:'Chief Finance Officer',organisation:'Stanbic Bank Uganda',phone:'+256 312 224 600',email:'c.kiggundu@stanbicbank.ug' },{ name:'Dr. Beatrice Nampijja',title:'Dean, School of Business',organisation:'Kyambogo University',phone:'+256 414 287 100',email:'b.nampijja@kyu.ac.ug' }]),
    next_of_kin: JSON.stringify({ name:'Robert Auma',relationship:'Brother',phone:'+256 701 999 888' }),
  },
];

// ─── Bulk application generator ───────────────────────────────────────────────
function generateBulkApps() {
  const M   = ["Samuel","Robert","John","James","David","Peter","Emmanuel","Joseph","Charles","Michael","Daniel","George","Richard","Patrick","Stephen","Francis","Andrew","William","Christopher","Moses","Paul","Mark","Henry","Joshua","Benjamin","Isaac","Lawrence","Gerald","Ronald","Ivan","Martin","Simon","Philip","Anthony","Alfred","Raymond","Vincent","Godfrey","Herbert","Dickson","Rogers","Apollo","Caleb","Joel","Amos","Pius","Enoch","Gilbert","Nelson","Dixon","Rashid","Hassan","Ibrahim","Yusuf","Abbas","Karim","Bosco","Innocent","Ambrose","Cyprian","Fabian","Leonard","Fred","Alex","Brian","Denis","Eric","Frank","Geoffrey","Herman","Julius","Kenneth","Levi","Maurice","Nathan","Oscar","Prosper","Quentin","Ronnie","Tadeo","Umar","Victor","Walter","Xavier","Yoweri","Zaid","Arnold","Benedict","Conrad","Duncan","Elijah","Felix","Gregory","Humphrey","Idris","Jerome","Kevin","Luca","Matthew","Nicholas","Oliver"];
  const F   = ["Mary","Sarah","Grace","Florence","Patricia","Christine","Agnes","Anita","Esther","Brenda","Gloria","Stella","Harriet","Doreen","Irene","Joan","Judith","Sharon","Caroline","Victoria","Juliet","Beatrice","Rebecca","Susan","Dorothy","Lydia","Miriam","Winfred","Diana","Josephine","Hellen","Anne","Margaret","Catherine","Ruth","Phiona","Olive","Flavia","Jacqueline","Rosemary","Evelyn","Lillian","Robinah","Winnie","Zainab","Aisha","Fatuma","Halima","Immaculate","Dorcas","Barbra","Mercy","Patience","Perpetua","Scholastica","Veronicah","Assumpta","Cissy","Ritah","Norah","Daphne","Alice","Betty","Claire","Deborah","Edith","Fiona","Gladys","Hope","Irma","Jane","Karen","Linda","Monica","Nancy","Olivia","Phoebe","Rita","Sandra","Tina","Uzma","Vanessa","Winifred","Yvonne","Zelda","Amina","Hadija","Khadija","Mariam","Nadia","Rahma","Sumayya","Taslima","Ukhty","Yasmin"];
  const SN  = ["Mukasa","Ssali","Nkutu","Bukenya","Okello","Opio","Atim","Achola","Namukasa","Nakamya","Namutebi","Kiggundu","Mugisha","Ssebayiga","Wanyama","Nakazibwe","Nassali","Abalo","Nakiganda","Kizito","Musoke","Lutalo","Ssekamwa","Nsubuga","Balaba","Muwanga","Katumba","Nkwanzi","Acen","Apiyo","Nansubuga","Nabirye","Nakigozi","Nanteza","Nambi","Nabukenya","Nalwoga","Nabwire","Nabukalu","Oulanyah","Kagolo","Lunkuse","Sekajja","Muliisa","Mulindwa","Mutyaba","Mubiru","Ddungu","Kyeyune","Kiyingi","Kibuuka","Kasozi","Katende","Kamya","Kabuga","Kayiwa","Kalanzi","Kalule","Nyanzi","Nyakato","Tumwebaze","Tuhaise","Tugume","Tusiime","Tweheyo","Twesigye","Ahimbisibwe","Akankwasa","Akello","Amuge","Okwir","Odongo","Obua","Ochola","Opiro","Olweny","Omara","Ojok","Ogwal","Ouma","Owino","Onyango","Drago","Amony","Chebet","Sang","Rutto","Were","Wekesa","Simiyu","Namiiro","Naluwooza","Namubiru","Nakitto","Nakintu","Nakayiza","Nakkazi","Nakawunde","Ssemwanga","Ssempijja","Ssenoga","Ssengonzi","Ssentamu","Luyima","Lukyamuzi","Lubega","Lukwago","Lutwama","Mugerwa","Muyingo","Mukaaya","Mukalazi","Nambooze","Nankya","Nansamba","Nantumbwe","Babirye","Birungi","Byarugaba","Katureebe","Kabaale","Kabahenda","Kabugo","Rwamirama","Rwabwogo","Tibaijuka","Ayot","Apio","Aparo","Okumu","Oboth","Odomel","Okwonga","Ayiku","Bbaale","Bbosa","Bbira","Ggayi","Kizza","Kitaka","Waiswa","Walusimbi","Walakira","Serunkuma","Ssebuggwawo","Nsereko","Ntege","Mutebi","Mutebe","Mwanje","Mwesige","Mwesigwa","Kyambadde","Kyaligonza","Sentamu","Semakula","Sempa","Ssemmanda","Zziwa","Kavuma","Kawooya","Kawuki","Kawuma","Kisenyi","Kiwanuka","Kiwanda","Magezi","Magembe","Matagi","Matovu","Matsiko","Muhumuza","Muhwezi","Natukunda","Ndawula","Nuwagaba","Nuwamanya","Rukewe","Rukundo","Rushegyera","Rwakakamba"];
  const UNIS = ["Makerere University","Kyambogo University","Mbarara University of Science & Technology","Uganda Christian University","Gulu University","Busitema University","Kampala International University","Uganda Management Institute","Kabale University","Nkumba University","Islamic University in Uganda","Uganda Martyrs University","Mountains of the Moon University","Civil Aviation Training College (CATC)"];
  const DOM  = ["gmail.com","gmail.com","gmail.com","gmail.com","gmail.com","yahoo.com","outlook.com","hotmail.com","gmail.com","gmail.com"];
  const DATES= ["Jan 6, 2026","Jan 13, 2026","Jan 19, 2026","Jan 27, 2026","Feb 3, 2026","Feb 10, 2026","Feb 17, 2026","Feb 24, 2026","Mar 3, 2026","Mar 10, 2026","Mar 17, 2026","Mar 24, 2026","Apr 1, 2026","Apr 8, 2026","Apr 14, 2026","Apr 21, 2026","May 5, 2026","May 12, 2026","May 19, 2026","May 26, 2026","Jun 2, 2026","Jun 6, 2026","Jun 9, 2026","Jun 12, 2026","Jun 16, 2026","Jun 19, 2026","Jun 23, 2026"];
  const ST_POOL = [...Array(38).fill('Pending'),...Array(24).fill('Under Review'),...Array(16).fill('Shortlisted'),...Array(9).fill('Interview'),...Array(5).fill('Offered'),...Array(8).fill('Declined')];
  const SPECS = [
    { id:1,  abbr:'ATC',  title:'Senior Air Traffic Controller',                        dept:'Air Traffic Mgmt',  n:80,  isCgpa:false },
    { id:2,  abbr:'ASI',  title:'Principal Safety Inspector (Airworthiness)',           dept:'Aviation Safety',   n:65,  isCgpa:false },
    { id:3,  abbr:'SYS',  title:'Systems Administrator',                                dept:'ICT & Systems',     n:88,  isCgpa:false },
    { id:4,  abbr:'FIN',  title:'Finance Officer (Revenue Assurance)',                  dept:'Finance & Admin',   n:83,  isCgpa:false },
    { id:5,  abbr:'LEG',  title:'Legal Counsel (Aviation Regulations)',                 dept:'Legal',             n:42,  isCgpa:false },
    { id:6,  abbr:'ATT',  title:'ATC Trainee (Graduate Entry)',                         dept:'Air Traffic Mgmt',  n:142, isCgpa:true  },
    { id:7,  abbr:'INT',  title:'Internal — Manager, Aerodrome Operations',             dept:'Operations',        n:21,  isCgpa:false },
    { id:8,  abbr:'ACO',  title:'Approach Control Officer',                             dept:'Air Traffic Mgmt',  n:69,  isCgpa:false },
    { id:9,  abbr:'FOI',  title:'Flight Operations Inspector',                          dept:'Aviation Safety',   n:58,  isCgpa:false },
    { id:10, abbr:'DGI',  title:'Dangerous Goods Inspector',                            dept:'Aviation Safety',   n:46,  isCgpa:false },
    { id:11, abbr:'ASec', title:'Aviation Security Inspector',                          dept:'Aviation Safety',   n:52,  isCgpa:false },
    { id:12, abbr:'PRO',  title:'Procurement Officer',                                  dept:'Finance & Admin',   n:70,  isCgpa:false },
    { id:13, abbr:'NET',  title:'Network Engineer',                                     dept:'ICT & Systems',     n:73,  isCgpa:false },
    { id:14, abbr:'AIS',  title:'Internal — Principal, Aeronautical Information Services', dept:'Operations',    n:18,  isCgpa:false },
  ];

  const apps = [];
  let s = 0x4A3B2C1D;
  for (const spec of SPECS) {
    const usedEmails = new Set();
    for (let k = 0; k < spec.n; k++) {
      s = lcg(s + k * 17);
      const female = (s % 3) === 0;
      const pool = female ? F : M;
      s = lcg(s); const fn = pool[s % pool.length];
      s = lcg(s); const ln = SN[s % SN.length];
      s = lcg(s); const st = ST_POOL[s % ST_POOL.length];
      s = lcg(s);
      const comp = st === 'Pending' ? 45+(s%56) : st === 'Under Review' ? 68+(s%33) : st === 'Declined' ? 30+(s%65) : 87+(s%14);
      s = lcg(s); const dt  = DATES[s % DATES.length];
      s = lcg(s); const dom = DOM[s % DOM.length];
      const base = `${fn.toLowerCase().replace(/[^a-z]/g,'')}${ln.toLowerCase().replace(/[^a-z]/g,'')}`;
      let em = `${base}@${dom}`;
      if (usedEmails.has(em)) em = `${base}${k}@${dom}`;
      usedEmails.add(em);
      const app = { job_id:spec.id, abbr:spec.abbr, title:spec.title, dept:spec.dept, date:dt, status:st, completion:comp, candidate_name:`${fn} ${ln}`, candidate_email:em, cgpa:null, university:null };
      if (spec.isCgpa) {
        s = lcg(s); app.cgpa = parseFloat(Math.max(2.0, Math.min(5.0, 2.0+(s%31)/10)).toFixed(1));
        s = lcg(s); app.university = UNIS[s % UNIS.length];
      }
      apps.push(app);
    }
  }
  return apps;
}

// ─── Analytics generator ──────────────────────────────────────────────────────
function generateAnalytics() {
  const now = Date.now(), DAY = 86400000;
  const events = [];
  let s = 77391;
  const rng = () => { s = lcg(s); return s / 0x100000000; };
  const jobPool    = [{ id:1,title:'Senior Air Traffic Controller' },{ id:8,title:'Approach Control Officer' },{ id:2,title:'Principal Safety Inspector (Airworthiness)' },{ id:3,title:'Systems Administrator' },{ id:4,title:'Finance Officer (Revenue Assurance)' },{ id:6,title:'ATC Trainee (Graduate Entry)' }];
  const searchPool = ['air traffic','safety','ICT jobs','finance','entebbe','procurement','network','ATC trainee'];
  for (let d = 29; d >= 0; d--) {
    const dayBase = now - d * DAY;
    for (let i = 0; i < 18+Math.round(rng()*20); i++) events.push({ event_type:'page_view', job_id:null, job_title:null, query:null, ts:dayBase+Math.round(rng()*(DAY-1)) });
    for (let i = 0; i < 10+Math.round(rng()*16); i++) { const j=jobPool[Math.floor(rng()*jobPool.length)]; events.push({ event_type:'job_view', job_id:j.id, job_title:j.title, query:null, ts:dayBase+Math.round(rng()*(DAY-1)) }); }
    for (let i = 0; i < 2+Math.round(rng()*5);  i++) { const j=jobPool[Math.floor(rng()*jobPool.length)]; events.push({ event_type:'apply_click', job_id:j.id, job_title:j.title, query:null, ts:dayBase+Math.round(rng()*(DAY-1)) }); }
    for (let i = 0; i < 1+Math.round(rng()*3);  i++) events.push({ event_type:'search', job_id:null, job_title:null, query:searchPool[Math.floor(rng()*searchPool.length)], ts:dayBase+Math.round(rng()*(DAY-1)) });
    if (rng() > 0.4) { const j=jobPool[Math.floor(rng()*jobPool.length)]; events.push({ event_type:'save_job', job_id:j.id, job_title:j.title, query:null, ts:dayBase+Math.round(rng()*(DAY-1)) }); }
  }
  return events;
}

// ─── Main ─────────────────────────────────────────────────────────────────────
async function main() {
  console.log('Generating SQL dump...');
  const lines = [];

  lines.push('-- ================================================================');
  lines.push('-- CAA Recruitment Portal — Full Data Dump');
  lines.push('-- Generated: ' + new Date().toISOString());
  lines.push('-- Import via: phpMyAdmin → your database → Import tab');
  lines.push('-- ================================================================');
  lines.push('');
  lines.push('SET FOREIGN_KEY_CHECKS = 0;');
  lines.push('SET NAMES utf8mb4;');
  lines.push('');

  // Settings
  lines.push('-- Settings');
  lines.push(`INSERT INTO settings (org_name,email_sender_name,min_age_threshold,allow_external_internal_jobs,session_timeout_minutes,closing_soon_days,max_applications_per_candidate,notif_template_shortlist,notif_template_decline,notif_template_interview,notif_template_offer) VALUES ('Uganda Civil Aviation Authority','CAA HR Team',21,0,30,7,5,'Dear {name}, we are pleased to inform you that your application for {role} has been shortlisted. Our team will contact you with further instructions shortly.','Dear {name}, thank you for applying for {role}. After careful review, we regret to inform you that your application has not been successful at this stage.','Dear {name}, congratulations! Your application for {role} has progressed to the interview stage. Our HR team will contact you to confirm the date and time.','Dear {name}, we are delighted to offer you the position of {role}. Please review the attached offer letter and respond within five (5) working days.');`);
  lines.push('');

  // Staff
  lines.push('-- Staff (15 records)');
  for (const s of STAFF) {
    lines.push(`INSERT INTO staff (employee_number,first_name,last_name,dept,position,email,joined_date,status) VALUES (${mysqlStr(s.employee_number)},${mysqlStr(s.first_name)},${mysqlStr(s.last_name)},${mysqlStr(s.dept)},${mysqlStr(s.position)},${mysqlStr(s.email)},${mysqlStr(s.joined_date)},${mysqlStr(s.status)});`);
  }
  lines.push('');

  // Users — hash passwords
  console.log('Hashing passwords (this takes ~30 seconds)...');
  lines.push('-- Users (admin + demo candidates)');
  const SALT = 12;
  for (const u of [...ADMIN_USERS, ...CANDIDATE_USERS]) {
    process.stdout.write(`  Hashing ${u.email}...`);
    const hash = await bcrypt.hash(u.plainPassword, SALT);
    const role = u.admin_role ? mysqlStr(u.admin_role) : 'NULL';
    lines.push(`INSERT INTO users (email,password_hash,first_name,last_name,account_type,admin_role,effective_type) VALUES (${mysqlStr(u.email)},${mysqlStr(hash)},${mysqlStr(u.first_name)},${mysqlStr(u.last_name)},${mysqlStr(u.account_type)},${role},${mysqlStr(u.effective_type)});`);
    console.log(' done');
  }
  lines.push('');

  // Jobs
  lines.push('-- Jobs (14 listings)');
  for (const j of JOBS) {
    lines.push(`INSERT INTO jobs (id,abbr,title,dept,dept_key,location,salary,salary_band,type,closes,closes_at,visibility,min_age,required_experience,required_qualification,description,featured) VALUES (${j.id},${mysqlStr(j.abbr)},${mysqlStr(j.title)},${mysqlStr(j.dept)},${mysqlStr(j.dept_key)},${mysqlStr(j.location)},${mysqlStr(j.salary)},${mysqlStr(j.salary_band)},${mysqlStr(j.type)},${mysqlStr(j.closes)},${mysqlStr(j.closes_at)},${mysqlStr(j.visibility)},${j.min_age},${j.required_experience},${mysqlStr(j.required_qualification)},${mysqlStr(j.description)},${j.featured});`);
  }
  lines.push('');

  // Pinned + extra applications
  lines.push('-- Pinned applications (10 records with explicit IDs)');
  for (const a of PINNED_APPS) {
    lines.push(`INSERT INTO applications (id,job_id,candidate_email,candidate_name,abbr,title,dept,date,applied_at,status,completion,cgpa,university) VALUES (${a.id},${a.job_id},${mysqlStr(a.candidate_email)},${mysqlStr(a.candidate_name)},${mysqlStr(a.abbr)},${mysqlStr(a.title)},${mysqlStr(a.dept)},${mysqlStr(a.date)},${mysqlStr(toMysqlDate(a.date))},${mysqlStr(a.status)},${a.completion},${mysqlNum(a.cgpa)},${mysqlStr(a.university)});`);
  }
  lines.push('');
  lines.push('ALTER TABLE applications AUTO_INCREMENT = 100;');
  lines.push('');

  // Bulk applications
  console.log('Generating bulk applications...');
  const bulkApps = generateBulkApps();
  lines.push(`-- Bulk applications (${bulkApps.length} records, IDs start at 100)`);
  for (const a of bulkApps) {
    lines.push(`INSERT INTO applications (job_id,candidate_email,candidate_name,abbr,title,dept,date,applied_at,status,completion,cgpa,university) VALUES (${a.job_id},${mysqlStr(a.candidate_email)},${mysqlStr(a.candidate_name)},${mysqlStr(a.abbr)},${mysqlStr(a.title)},${mysqlStr(a.dept)},${mysqlStr(a.date)},${mysqlStr(toMysqlDate(a.date))},${mysqlStr(a.status)},${a.completion},${mysqlNum(a.cgpa)},${mysqlStr(a.university)});`);
  }
  lines.push('');

  // CV profiles
  lines.push('-- CV profiles (2 demo records)');
  for (const cv of CV_PROFILES) {
    lines.push(`INSERT INTO cv_profiles (user_email,personal_data,highest_level,qualifications,skills,experience,referees,next_of_kin,photo_url) VALUES (${mysqlStr(cv.user_email)},${mysqlStr(cv.personal_data)},${mysqlStr(cv.highest_level)},${mysqlStr(cv.qualifications)},${mysqlStr(cv.skills)},${mysqlStr(cv.experience)},${mysqlStr(cv.referees)},${mysqlStr(cv.next_of_kin)},NULL);`);
  }
  lines.push('');

  // Analytics events
  console.log('Generating analytics events...');
  const analytics = generateAnalytics();
  lines.push(`-- Analytics events (${analytics.length} records)`);
  for (const e of analytics) {
    lines.push(`INSERT INTO analytics_events (event_type,job_id,job_title,query,created_at) VALUES (${mysqlStr(e.event_type)},${mysqlNum(e.job_id)},${mysqlStr(e.job_title)},${mysqlStr(e.query)},${mysqlStr(toMysqlDateFromTs(e.ts))});`);
  }
  lines.push('');

  lines.push('SET FOREIGN_KEY_CHECKS = 1;');
  lines.push('');
  lines.push('-- ================================================================');
  lines.push('-- Import complete.');
  lines.push('-- Admin:     admin@caa.go.ug        / Admin@2026');
  lines.push('-- HR Dir:    hrdirector@caa.go.ug   / HrDir@2026');
  lines.push('-- Recruiter: recruit@caa.go.ug       / Recruit@2026');
  lines.push('-- Candidate: jbukenya@gmail.com      / Demo@2026');
  lines.push('-- ================================================================');

  const output = lines.join('\n');
  fs.writeFileSync('caa-seed.sql', output, 'utf8');

  const sizeMB = (Buffer.byteLength(output, 'utf8') / 1024 / 1024).toFixed(2);
  console.log(`\nDone! caa-seed.sql written (${sizeMB} MB, ${bulkApps.length + PINNED_APPS.length} applications, ${analytics.length} analytics events)`);
  console.log('Import it via phpMyAdmin → select your database → Import tab → choose file.');
}

main().catch(err => { console.error(err); process.exit(1); });
