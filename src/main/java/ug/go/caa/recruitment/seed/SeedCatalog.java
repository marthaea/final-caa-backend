package ug.go.caa.recruitment.seed;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

final class SeedCatalog {

    static final DateTimeFormatter CLOSE_DISPLAY =
            DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH);

    private SeedCatalog() {
    }

    record Department(String code, String name) {
    }

    record Admin(
            String email,
            String firstName,
            String lastName,
            String role,
            String password
    ) {
    }

    record StaffMember(
            String employeeNumber,
            String firstName,
            String lastName
    ) {
    }

    record JobSeed(
            String abbr,
            String title,
            String dept,
            String deptKey,
            String location,
            String salary,
            String salaryBand,
            String type,
            LocalDate originalClosesAt,
            String visibility,
            int minAge,
            int requiredExperience,
            String requiredQualification,
            boolean featured,
            String description
    ) {
        String jobRef() {
            return "caa-seed-" + abbr;
        }
    }

    record CandidateSeed(
            String email,
            String firstName,
            String lastName,
            String password,
            String accountType,
            String personalJson,
            String highestLevel,
            String qualificationsJson,
            String skillsJson,
            String experienceJson,
            String refereesJson,
            String nextOfKinJson
    ) {
    }

    record ApplicationSeed(
            String jobAbbr,
            String email,
            String firstName,
            String lastName,
            String status,
            int completion,
            LocalDate appliedOn,
            Double cgpa,
            String university
    ) {
    }

    static final List<Department> DEPARTMENTS = List.of(
            new Department("atm", "Air Traffic Mgmt"),
            new Department("safety", "Aviation Safety"),
            new Department("ict", "ICT & Systems"),
            new Department("finance", "Finance & Admin"),
            new Department("legal", "Legal"),
            new Department("ops", "Operations"),
            new Department("hr", "Human Resources"),
            new Department("procurement", "Procurement"),
            new Department("engineering", "Engineering"),
            new Department("communications", "Communications"));

    static final List<String> STAFF_DEPTS = List.of(
            "Air Traffic Mgmt", "Aviation Safety", "Finance & Admin", "ICT & Systems", "Legal",
            "Operations", "Human Resources", "Procurement", "Engineering", "Communications");

    static final List<String> STAFF_POSITIONS = List.of(
            "Director", "Manager", "Senior Officer", "Officer",
            "Analyst", "Coordinator", "Specialist", "Assistant");

    static final List<StaffMember> STAFF = List.of(
            new StaffMember("CAA-1001", "Sarah", "Namutebi"),
            new StaffMember("CAA-1002", "James", "Okello"),
            new StaffMember("CAA-1003", "Patricia", "Akello"),
            new StaffMember("CAA-1004", "Robert", "Ssebayiga"),
            new StaffMember("CAA-1005", "Grace", "Atim"),
            new StaffMember("CAA-1006", "David", "Mugisha"),
            new StaffMember("CAA-1007", "Florence", "Nansubuga"),
            new StaffMember("CAA-1008", "Charles", "Opio"),
            new StaffMember("CAA-1009", "Anita", "Nakazibwe"),
            new StaffMember("CAA-1010", "Peter", "Wanyama"),
            new StaffMember("CAA-1011", "Christine", "Nassali"),
            new StaffMember("CAA-1012", "Joseph", "Abalo"),
            new StaffMember("CAA-1013", "Esther", "Nakiganda"),
            new StaffMember("CAA-1014", "Moses", "Kiggundu"),
            new StaffMember("CAA-1015", "Agnes", "Achola"));

    static final List<Admin> ADMINS = List.of(
            new Admin("admin@caa.go.ug", "Alex", "Mukasa", "super", "Admin@2026"),
            new Admin("hrdirector@caa.go.ug", "Jane", "Mirembe", "hr", "HrDir@2026"),
            new Admin("recruit@caa.go.ug", "David", "Ssempala", "recruiter", "Recruit@2026"),
            new Admin("auditor@caa.go.ug", "Grace", "Nabirye", "auditor", "Auditor@2026"),
            new Admin("hrofficer@caa.go.ug", "Samuel", "Okello", "hr_officer", "HrOfficer@2026"),
            new Admin("itadmin@caa.go.ug", "Irene", "Nakato", "it_admin", "ItAdmin@2026"),
            new Admin("dhra@caa.go.ug", "Paul", "Kiggundu", "dhra", "Dhra@2026"),
            new Admin("hod@caa.go.ug", "Faith", "Atim", "hod", "Hod@2026"));

    static final List<JobSeed> JOBS = List.of(
            new JobSeed("ATC", "Senior Air Traffic Controller", "Air Traffic Mgmt", "atm",
                    "Entebbe Airport", "UGX 4.2M–5.8M", "UG4", "Full-time",
                    LocalDate.of(2026, 6, 15), "external", 25, 5, "Degree", true,
                    "Direct en-route and approach traffic at Entebbe ACC."),
            new JobSeed("ASI", "Principal Safety Inspector (Airworthiness)", "Aviation Safety", "safety",
                    "Kampala HQ", "UGX 3.8M–5.2M", "UG3", "Full-time",
                    LocalDate.of(2026, 6, 20), "external", 28, 7, "Degree", true, null),
            new JobSeed("SYS", "Systems Administrator", "ICT & Systems", "ict",
                    "Kampala HQ", "UGX 2.6M–3.5M", "UG5", "Full-time",
                    LocalDate.of(2026, 7, 1), "external", 23, 3, "Degree", false, null),
            new JobSeed("FIN", "Finance Officer (Revenue Assurance)", "Finance & Admin", "finance",
                    "Kampala HQ", "UGX 2.8M–3.6M", "UG5", "Contract",
                    LocalDate.of(2026, 6, 30), "external", 25, 4, "Degree", false, null),
            new JobSeed("LEG", "Legal Counsel (Aviation Regulations)", "Legal", "legal",
                    "Kampala HQ", "UGX 3.2M–4.4M", "UG4", "Full-time",
                    LocalDate.of(2026, 7, 10), "external", 27, 5, "Masters", false, null),
            new JobSeed("ATT", "ATC Trainee (Graduate Entry)", "Air Traffic Mgmt", "atm",
                    "Entebbe Airport", "UGX 1.8M–2.4M", "UG7", "Full-time",
                    LocalDate.of(2026, 7, 15), "external", 21, 0, "Degree", false, null),
            new JobSeed("INT", "Internal — Manager, Aerodrome Operations", "Operations", "ops",
                    "Entebbe Airport", "UGX 5.5M–7.0M", "UG2", "Full-time",
                    LocalDate.of(2026, 6, 25), "internal", 30, 8, "Masters", false,
                    "Open to verified UCAA staff only."),
            new JobSeed("ACO", "Approach Control Officer", "Air Traffic Mgmt", "atm",
                    "Entebbe Airport", "UGX 3.5M–4.8M", "UG4", "Full-time",
                    LocalDate.of(2026, 7, 20), "external", 24, 3, "Degree", true, null),
            new JobSeed("FOI", "Flight Operations Inspector", "Aviation Safety", "safety",
                    "Kampala HQ", "UGX 3.2M–4.5M", "UG4", "Full-time",
                    LocalDate.of(2026, 7, 5), "external", 28, 6, "Degree", false, null),
            new JobSeed("DGI", "Dangerous Goods Inspector", "Aviation Safety", "safety",
                    "Entebbe Airport", "UGX 2.8M–3.8M", "UG5", "Full-time",
                    LocalDate.of(2026, 7, 8), "external", 25, 4, "Degree", false, null),
            new JobSeed("ASec", "Aviation Security Inspector", "Aviation Safety", "safety",
                    "Entebbe Airport", "UGX 2.9M–3.9M", "UG5", "Full-time",
                    LocalDate.of(2026, 7, 12), "external", 25, 4, "Degree", false, null),
            new JobSeed("PRO", "Procurement Officer", "Finance & Admin", "finance",
                    "Kampala HQ", "UGX 2.4M–3.2M", "UG6", "Contract",
                    LocalDate.of(2026, 7, 3), "external", 23, 2, "Degree", false, null),
            new JobSeed("NET", "Network Engineer", "ICT & Systems", "ict",
                    "Kampala HQ", "UGX 2.8M–3.7M", "UG5", "Full-time",
                    LocalDate.of(2026, 7, 18), "external", 24, 3, "Degree", false, null),
            new JobSeed("AIS", "Internal — Principal, Aeronautical Information Services", "Operations", "ops",
                    "Entebbe Airport", "UGX 4.5M–6.0M", "UG3", "Full-time",
                    LocalDate.of(2026, 7, 22), "internal", 28, 6, "Degree", false,
                    "Open to verified UCAA staff only."));

    /** Per-job volume counts keyed by seed abbr order (1..14). */
    static final int[] VOLUME_COUNTS = {
            80, 65, 88, 83, 42, 142, 21, 69, 58, 46, 52, 70, 73, 18
    };

    static final List<String> UNIVERSITIES = List.of(
            "Makerere University",
            "Kyambogo University",
            "Mbarara University of Science & Technology",
            "Uganda Christian University",
            "Gulu University",
            "Busitema University",
            "Kampala International University",
            "Uganda Management Institute",
            "Kabale University",
            "Nkumba University",
            "Islamic University in Uganda",
            "Uganda Martyrs University",
            "Mountains of the Moon University",
            "Civil Aviation Training College (CATC)");

    static final List<String> SEARCH_QUERIES = List.of(
            "air traffic", "safety", "ICT jobs", "finance",
            "entebbe", "procurement", "network", "ATC trainee");

    static final String[] VOLUME_STATUS_POOL = buildStatusPool();

    private static String[] buildStatusPool() {
        String[] pool = new String[100];
        int i = 0;
        i = fill(pool, i, 38, "Pending");
        i = fill(pool, i, 24, "Under Review");
        i = fill(pool, i, 16, "Shortlisted");
        i = fill(pool, i, 9, "Interview");
        i = fill(pool, i, 5, "Offered");
        fill(pool, i, 8, "Declined");
        return pool;
    }

    private static int fill(String[] pool, int start, int count, String status) {
        for (int n = 0; n < count; n++) {
            pool[start + n] = status;
        }
        return start + count;
    }

    /**
     * Keep jobs publicly listable: original closesAt + 6 months, floored to
     * at least 60 days from seed runtime when that would still be expired.
     */
    static LocalDate visibleClosesAt(LocalDate original, int jobIndex) {
        LocalDate shifted = original.plusMonths(6);
        LocalDate minimum = LocalDate.now().plusDays(60L + jobIndex * 3L);
        return shifted.isBefore(minimum) ? minimum : shifted;
    }
}
