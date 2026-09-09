package ug.go.caa.recruitment.seed;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ug.go.caa.recruitment.feature.identity.application.RolePermissions;
import ug.go.caa.recruitment.seed.SeedCatalog.Admin;
import ug.go.caa.recruitment.seed.SeedCatalog.ApplicationSeed;
import ug.go.caa.recruitment.seed.SeedCatalog.CandidateSeed;
import ug.go.caa.recruitment.seed.SeedCatalog.Department;
import ug.go.caa.recruitment.seed.SeedCatalog.JobSeed;
import ug.go.caa.recruitment.seed.SeedCatalog.StaffMember;

@Service
public class DatabaseSeeder {

    private static final Logger log = LoggerFactory.getLogger(DatabaseSeeder.class);
    private static final String STAFF_PASSWORD = "Staff@2026";
    private static final String CANDIDATE_PASSWORD = "Candidate@2026";
    private static final long VOLUME_LCG_SEED = 0x4A3B2C1DL;

    private final JdbcClient jdbc;
    private final PasswordEncoder passwordEncoder;
    private final SeedProperties properties;

    public DatabaseSeeder(
            JdbcClient jdbc,
            PasswordEncoder passwordEncoder,
            SeedProperties properties
    ) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Transactional
    public void run() {
        if (properties.includeCore()) {
            seedDepartments();
            seedSettings();
            logRoleDefaults();
            if (properties.demo()) {
                seedAdmins();
                seedStaff();
            } else {
                log.warn("Skipping admin/staff passworded accounts (set app.seed.demo=true / SEED_DEMO=true)");
            }
            seedJobs();
        }
        if (properties.includeDemo()) {
            requireDemoFlag("demo candidates/applications");
            seedDemoCandidatesAndCvs();
            seedPinnedApplications();
            seedDemoAnalytics();
        }
        if (properties.includeVolume()) {
            requireDemoFlag("volume applications");
            seedVolumeApplications();
        }
        log.info("Seed completed for mode={}", properties.mode());
    }

    private void requireDemoFlag(String label) {
        if (!properties.demo()) {
            throw new IllegalStateException(
                    "Refusing to seed " + label + " without app.seed.demo=true (SEED_DEMO=true)");
        }
    }

    private void seedDepartments() {
        for (Department department : SeedCatalog.DEPARTMENTS) {
            Optional<Long> existing = jdbc.sql("SELECT id FROM departments WHERE code = :code")
                    .param("code", department.code())
                    .query(Long.class)
                    .optional();
            if (existing.isPresent()) {
                jdbc.sql("UPDATE departments SET name = :name, updated_at = now() WHERE id = :id")
                        .param("name", department.name())
                        .param("id", existing.get())
                        .update();
            } else {
                jdbc.sql("INSERT INTO departments (name, code) VALUES (:name, :code)")
                        .param("name", department.name())
                        .param("code", department.code())
                        .update();
            }
        }
        log.info("Departments upserted: {}", SeedCatalog.DEPARTMENTS.size());
    }

    private void seedSettings() {
        Long id = jdbc.sql("SELECT id FROM settings ORDER BY id LIMIT 1")
                .query(Long.class)
                .optional()
                .orElseGet(() -> jdbc.sql("INSERT INTO settings DEFAULT VALUES RETURNING id")
                        .query(Long.class)
                        .single());
        jdbc.sql("""
                UPDATE settings SET
                  org_name = :orgName,
                  email_sender_name = :sender,
                  min_age_threshold = :minAge,
                  allow_external_internal_jobs = :externalJobs,
                  session_timeout_minutes = :timeout,
                  closing_soon_days = :closingDays,
                  max_applications_per_candidate = :maxApps,
                  notif_template_shortlist = :shortlist,
                  notif_template_decline = :decline,
                  notif_template_interview = :interview,
                  notif_template_offer = :offer,
                  updated_at = now()
                WHERE id = :id
                """)
                .param("orgName", "Uganda Civil Aviation Authority")
                .param("sender", "CAA HR Team")
                .param("minAge", 21)
                .param("externalJobs", false)
                .param("timeout", 30)
                .param("closingDays", 7)
                .param("maxApps", 5)
                .param("shortlist",
                        "Dear {name}, we are pleased to inform you that your application for {role} has been shortlisted. Our team will contact you with further instructions shortly.")
                .param("decline",
                        "Dear {name}, thank you for applying for {role}. After careful review, we regret to inform you that your application has not been successful at this stage.")
                .param("interview",
                        "Dear {name}, congratulations! Your application for {role} has progressed to the interview stage. Our HR team will contact you to confirm the date and time.")
                .param("offer",
                        "Dear {name}, we are delighted to offer you the position of {role}. Please review the attached offer letter and respond within five (5) working days.")
                .param("id", id)
                .update();
        log.info("Portal settings upserted (id={})", id);
    }

    private void logRoleDefaults() {
        log.info(
                "RBAC role defaults are code-backed in RolePermissions ({} roles, {} keys); no DB table seed required",
                RolePermissions.ROLES.size(),
                RolePermissions.KEYS.size());
    }

    private void seedAdmins() {
        for (Admin admin : SeedCatalog.ADMINS) {
            upsertUser(
                    admin.email(),
                    admin.firstName(),
                    admin.lastName(),
                    "admin",
                    admin.role(),
                    null,
                    "admin",
                    admin.password());
        }
        log.info("Admin users upserted: {}", SeedCatalog.ADMINS.size());
    }

    private void seedStaff() {
        String staffHash = passwordEncoder.encode(STAFF_PASSWORD);
        for (int i = 0; i < SeedCatalog.STAFF.size(); i++) {
            StaffMember member = SeedCatalog.STAFF.get(i);
            String dept = SeedCatalog.STAFF_DEPTS.get(i % SeedCatalog.STAFF_DEPTS.size());
            String position = SeedCatalog.STAFF_POSITIONS.get(i % SeedCatalog.STAFF_POSITIONS.size());
            String email = (member.firstName() + member.lastName()).toLowerCase(Locale.ROOT) + "@caa.go.ug";
            LocalDate joined = LocalDate.of(
                    2014 + (i % 9),
                    (i % 12) + 1,
                    (i % 28) + 1);
            long userId = upsertUser(
                    email,
                    member.firstName(),
                    member.lastName(),
                    "internal",
                    null,
                    member.employeeNumber(),
                    "internal",
                    null,
                    staffHash);
            Optional<Long> staffId = jdbc.sql(
                            "SELECT id FROM staff WHERE employee_number = :employeeNumber")
                    .param("employeeNumber", member.employeeNumber())
                    .query(Long.class)
                    .optional();
            if (staffId.isPresent()) {
                jdbc.sql("""
                        UPDATE staff SET
                          user_id = :userId,
                          first_name = :firstName,
                          last_name = :lastName,
                          dept = :dept,
                          position = :position,
                          email = :email,
                          joined_date = :joined,
                          status = 'Active',
                          updated_at = now()
                        WHERE id = :id
                        """)
                        .param("userId", userId)
                        .param("firstName", member.firstName())
                        .param("lastName", member.lastName())
                        .param("dept", dept)
                        .param("position", position)
                        .param("email", email)
                        .param("joined", joined)
                        .param("id", staffId.get())
                        .update();
            } else {
                jdbc.sql("""
                        INSERT INTO staff (
                          user_id, employee_number, first_name, last_name,
                          dept, position, email, joined_date, status
                        ) VALUES (
                          :userId, :employeeNumber, :firstName, :lastName,
                          :dept, :position, :email, :joined, 'Active'
                        )
                        """)
                        .param("userId", userId)
                        .param("employeeNumber", member.employeeNumber())
                        .param("firstName", member.firstName())
                        .param("lastName", member.lastName())
                        .param("dept", dept)
                        .param("position", position)
                        .param("email", email)
                        .param("joined", joined)
                        .update();
            }
        }
        log.info("Staff directory upserted: {}", SeedCatalog.STAFF.size());
    }

    private void seedJobs() {
        Long createdBy = jdbc.sql("""
                SELECT id FROM users
                WHERE lower(email) = lower('admin@caa.go.ug')
                LIMIT 1
                """).query(Long.class).optional().orElse(null);
        Map<String, Long> departments = departmentIdsByCode();
        int index = 0;
        for (JobSeed job : SeedCatalog.JOBS) {
            LocalDate closesAt = SeedCatalog.visibleClosesAt(job.originalClosesAt(), index++);
            String closes = SeedCatalog.CLOSE_DISPLAY.format(closesAt);
            Long departmentId = departments.get(job.deptKey());
            Optional<Long> existing = jdbc.sql("SELECT id FROM jobs WHERE job_ref = :jobRef")
                    .param("jobRef", job.jobRef())
                    .query(Long.class)
                    .optional();
            if (existing.isPresent()) {
                jdbc.sql("""
                        UPDATE jobs SET
                          abbr = :abbr, title = :title, dept = :dept, dept_key = :deptKey,
                          location = :location, salary = :salary, salary_band = :salaryBand,
                          type = :type, closes = :closes, closes_at = :closesAt,
                          visibility = :visibility, min_age = :minAge,
                          required_experience = :experience,
                          required_qualification = :qualification,
                          description = :description, featured = :featured,
                          status = 'published', department_id = :departmentId,
                          created_by = COALESCE(created_by, :createdBy),
                          updated_at = now()
                        WHERE id = :id
                        """)
                        .param("abbr", job.abbr())
                        .param("title", job.title())
                        .param("dept", job.dept())
                        .param("deptKey", job.deptKey())
                        .param("location", job.location())
                        .param("salary", job.salary())
                        .param("salaryBand", job.salaryBand())
                        .param("type", job.type())
                        .param("closes", closes)
                        .param("closesAt", closesAt)
                        .param("visibility", job.visibility())
                        .param("minAge", job.minAge())
                        .param("experience", job.requiredExperience())
                        .param("qualification", job.requiredQualification())
                        .param("description", job.description())
                        .param("featured", job.featured())
                        .param("departmentId", departmentId)
                        .param("createdBy", createdBy)
                        .param("id", existing.get())
                        .update();
            } else {
                jdbc.sql("""
                        INSERT INTO jobs (
                          abbr, title, dept, dept_key, location, salary, salary_band, type,
                          closes, closes_at, visibility, min_age, required_experience,
                          required_qualification, description, featured, created_by,
                          status, department_id, job_ref
                        ) VALUES (
                          :abbr, :title, :dept, :deptKey, :location, :salary, :salaryBand, :type,
                          :closes, :closesAt, :visibility, :minAge, :experience,
                          :qualification, :description, :featured, :createdBy,
                          'published', :departmentId, :jobRef
                        )
                        """)
                        .param("abbr", job.abbr())
                        .param("title", job.title())
                        .param("dept", job.dept())
                        .param("deptKey", job.deptKey())
                        .param("location", job.location())
                        .param("salary", job.salary())
                        .param("salaryBand", job.salaryBand())
                        .param("type", job.type())
                        .param("closes", closes)
                        .param("closesAt", closesAt)
                        .param("visibility", job.visibility())
                        .param("minAge", job.minAge())
                        .param("experience", job.requiredExperience())
                        .param("qualification", job.requiredQualification())
                        .param("description", job.description())
                        .param("featured", job.featured())
                        .param("createdBy", createdBy)
                        .param("departmentId", departmentId)
                        .param("jobRef", job.jobRef())
                        .update();
            }
        }
        log.info(
                "Jobs upserted: {} (closesAt = original+6 months, floored to remain publicly visible)",
                SeedCatalog.JOBS.size());
    }

    private void seedDemoCandidatesAndCvs() {
        for (CandidateSeed candidate : demoCandidates()) {
            long userId = upsertUser(
                    candidate.email(),
                    candidate.firstName(),
                    candidate.lastName(),
                    candidate.accountType(),
                    null,
                    null,
                    candidate.accountType(),
                    candidate.password());
            upsertCv(userId, candidate);
        }
        // Lightweight candidates used only by pinned applications
        for (ApplicationSeed application : pinnedApplications()) {
            if (findUserId(application.email()).isEmpty()) {
                upsertUser(
                        application.email(),
                        application.firstName(),
                        application.lastName(),
                        "external",
                        null,
                        null,
                        "external",
                        CANDIDATE_PASSWORD);
            }
        }
        log.info("Demo candidates + CV profiles upserted");
    }

    private void seedPinnedApplications() {
        Map<String, Long> jobs = jobIdsByAbbr();
        for (ApplicationSeed application : pinnedApplications()) {
            Long jobId = jobs.get(application.jobAbbr());
            if (jobId == null) {
                throw new IllegalStateException("Missing seeded job " + application.jobAbbr());
            }
            upsertApplication(jobId, application);
        }
        log.info("Pinned applications upserted: {}", pinnedApplications().size());
    }

    private void seedDemoAnalytics() {
        Integer existing = jdbc.sql("""
                SELECT count(*) FROM analytics_events
                WHERE session_id LIKE 'seed-demo-%'
                """).query(Integer.class).single();
        if (existing != null && existing > 0) {
            log.info("Demo analytics already present ({}); skipping", existing);
            return;
        }
        Map<String, Long> jobs = jobIdsByAbbr();
        String[] eventTypes = {"page_view", "job_view", "apply_click", "search", "save_job"};
        String[] jobAbbrs = {"ATC", "ASI", "SYS", "FIN", "ATT", "ACO"};
        int written = 0;
        for (int day = 0; day < 30; day++) {
            for (int n = 0; n < 8; n++) {
                String type = eventTypes[(day + n) % eventTypes.length];
                String abbr = jobAbbrs[(day + n) % jobAbbrs.length];
                Long jobId = "search".equals(type) || "page_view".equals(type) ? null : jobs.get(abbr);
                String title = jobId == null ? null : titleForAbbr(abbr);
                String query = "search".equals(type)
                        ? SeedCatalog.SEARCH_QUERIES.get((day + n) % SeedCatalog.SEARCH_QUERIES.size())
                        : null;
                OffsetDateTime at = OffsetDateTime.now(ZoneOffset.UTC).minusDays(day).minusHours(n);
                jdbc.sql("""
                        INSERT INTO analytics_events (
                          event_type, job_id, job_title, query, session_id, created_at
                        ) VALUES (
                          :type, :jobId, :title, :query, :sessionId, :createdAt
                        )
                        """)
                        .param("type", type)
                        .param("jobId", jobId)
                        .param("title", title)
                        .param("query", query)
                        .param("sessionId", "seed-demo-" + day + "-" + n)
                        .param("createdAt", at)
                        .update();
                written++;
            }
        }
        log.info("Demo analytics events inserted: {}", written);
    }

    private void seedVolumeApplications() {
        Map<String, Long> jobs = jobIdsByAbbr();
        String volumeHash = passwordEncoder.encode(CANDIDATE_PASSWORD);
        long state = VOLUME_LCG_SEED;
        int created = 0;
        for (int jobIndex = 0; jobIndex < SeedCatalog.JOBS.size(); jobIndex++) {
            JobSeed job = SeedCatalog.JOBS.get(jobIndex);
            Long jobId = jobs.get(job.abbr());
            int count = SeedCatalog.VOLUME_COUNTS[jobIndex];
            for (int n = 0; n < count; n++) {
                state = nextLcg(state);
                String email = "vol.candidate." + job.abbr().toLowerCase(Locale.ROOT)
                        + "." + (n + 1) + "@seed.caa.go.ug";
                if (applicationExists(jobId, email)) {
                    continue;
                }
                String first = "Vol" + job.abbr() + (n + 1);
                String last = "Candidate";
                long userId = upsertUser(
                        email, first, last, "external", null, null, "external", null, volumeHash);
                state = nextLcg(state);
                String status = SeedCatalog.VOLUME_STATUS_POOL[(int) (Math.floorMod(state, 100))];
                state = nextLcg(state);
                int completion = 40 + (int) Math.floorMod(state, 61);
                Double cgpa = null;
                String university = null;
                if ("ATT".equals(job.abbr())) {
                    state = nextLcg(state);
                    cgpa = 2.0 + (Math.floorMod(state, 31) / 10.0);
                    state = nextLcg(state);
                    university = SeedCatalog.UNIVERSITIES.get(
                            (int) Math.floorMod(state, SeedCatalog.UNIVERSITIES.size()));
                }
                LocalDate applied = LocalDate.now().minusDays(Math.floorMod(state, 40L));
                insertApplication(
                        jobId,
                        userId,
                        email,
                        first + " " + last,
                        job,
                        status,
                        completion,
                        applied,
                        cgpa,
                        university);
                created++;
            }
        }
        log.info("Volume applications inserted (new only): {}", created);
    }

    private long upsertUser(
            String email,
            String firstName,
            String lastName,
            String accountType,
            String adminRole,
            String employeeNumber,
            String effectiveType,
            String plaintextPassword
    ) {
        return upsertUser(
                email,
                firstName,
                lastName,
                accountType,
                adminRole,
                employeeNumber,
                effectiveType,
                plaintextPassword,
                plaintextPassword == null ? null : passwordEncoder.encode(plaintextPassword));
    }

    private long upsertUser(
            String email,
            String firstName,
            String lastName,
            String accountType,
            String adminRole,
            String employeeNumber,
            String effectiveType,
            String plaintextPassword,
            String passwordHash
    ) {
        Optional<Long> existing = findUserId(email);
        if (existing.isPresent()) {
            var update = jdbc.sql("""
                    UPDATE users SET
                      first_name = :firstName,
                      last_name = :lastName,
                      account_type = :accountType,
                      admin_role = :adminRole,
                      employee_number = :employeeNumber,
                      effective_type = :effectiveType,
                      email_verified = true,
                      is_active = true,
                      password_hash = COALESCE(:passwordHash, password_hash),
                      updated_at = now()
                    WHERE id = :id
                    """)
                    .param("firstName", firstName)
                    .param("lastName", lastName)
                    .param("accountType", accountType)
                    .param("adminRole", adminRole)
                    .param("employeeNumber", employeeNumber)
                    .param("effectiveType", effectiveType)
                    .param("passwordHash", passwordHash)
                    .param("id", existing.get());
            update.update();
            return existing.get();
        }
        if (passwordHash == null) {
            if (plaintextPassword == null) {
                throw new IllegalArgumentException("Password required for new user " + email);
            }
            passwordHash = passwordEncoder.encode(plaintextPassword);
        }
        return jdbc.sql("""
                INSERT INTO users (
                  email, password_hash, first_name, last_name, account_type, admin_role,
                  employee_number, effective_type, email_verified, is_active
                ) VALUES (
                  :email, :passwordHash, :firstName, :lastName, :accountType, :adminRole,
                  :employeeNumber, :effectiveType, true, true
                ) RETURNING id
                """)
                .param("email", email)
                .param("passwordHash", passwordHash)
                .param("firstName", firstName)
                .param("lastName", lastName)
                .param("accountType", accountType)
                .param("adminRole", adminRole)
                .param("employeeNumber", employeeNumber)
                .param("effectiveType", effectiveType)
                .query(Long.class)
                .single();
    }

    private void upsertCv(long userId, CandidateSeed candidate) {
        Optional<Long> existing = jdbc.sql(
                        "SELECT id FROM cv_profiles WHERE lower(user_email) = lower(:email)")
                .param("email", candidate.email())
                .query(Long.class)
                .optional();
        if (existing.isPresent()) {
            jdbc.sql("""
                    UPDATE cv_profiles SET
                      user_id = :userId,
                      personal_data = CAST(:personal AS jsonb),
                      highest_level = :highestLevel,
                      qualifications = CAST(:qualifications AS jsonb),
                      skills = CAST(:skills AS jsonb),
                      experience = CAST(:experience AS jsonb),
                      referees = CAST(:referees AS jsonb),
                      next_of_kin = CAST(:nextOfKin AS jsonb),
                      updated_at = now()
                    WHERE id = :id
                    """)
                    .param("userId", userId)
                    .param("personal", candidate.personalJson())
                    .param("highestLevel", candidate.highestLevel())
                    .param("qualifications", candidate.qualificationsJson())
                    .param("skills", candidate.skillsJson())
                    .param("experience", candidate.experienceJson())
                    .param("referees", candidate.refereesJson())
                    .param("nextOfKin", candidate.nextOfKinJson())
                    .param("id", existing.get())
                    .update();
            return;
        }
        jdbc.sql("""
                INSERT INTO cv_profiles (
                  user_id, user_email, personal_data, highest_level, qualifications,
                  skills, experience, referees, next_of_kin
                ) VALUES (
                  :userId, :email, CAST(:personal AS jsonb), :highestLevel,
                  CAST(:qualifications AS jsonb), CAST(:skills AS jsonb),
                  CAST(:experience AS jsonb), CAST(:referees AS jsonb),
                  CAST(:nextOfKin AS jsonb)
                )
                """)
                .param("userId", userId)
                .param("email", candidate.email())
                .param("personal", candidate.personalJson())
                .param("highestLevel", candidate.highestLevel())
                .param("qualifications", candidate.qualificationsJson())
                .param("skills", candidate.skillsJson())
                .param("experience", candidate.experienceJson())
                .param("referees", candidate.refereesJson())
                .param("nextOfKin", candidate.nextOfKinJson())
                .update();
    }

    private void upsertApplication(long jobId, ApplicationSeed application) {
        JobSeed job = SeedCatalog.JOBS.stream()
                .filter(item -> item.abbr().equals(application.jobAbbr()))
                .findFirst()
                .orElseThrow();
        Long userId = findUserId(application.email()).orElse(null);
        if (applicationExists(jobId, application.email())) {
            jdbc.sql("""
                    UPDATE applications SET
                      candidate_user_id = COALESCE(:userId, candidate_user_id),
                      candidate_name = :name,
                      abbr = :abbr,
                      title = :title,
                      dept = :dept,
                      date = :date,
                      status = :status,
                      completion = :completion,
                      cgpa = :cgpa,
                      university = :university,
                      applied_at = :appliedAt,
                      updated_at = now()
                    WHERE job_id = :jobId AND lower(candidate_email) = lower(:email)
                    """)
                    .param("userId", userId)
                    .param("name", application.firstName() + " " + application.lastName())
                    .param("abbr", job.abbr())
                    .param("title", job.title())
                    .param("dept", job.dept())
                    .param("date", application.appliedOn().toString())
                    .param("status", application.status())
                    .param("completion", application.completion())
                    .param("cgpa", application.cgpa())
                    .param("university", application.university())
                    .param("appliedAt", application.appliedOn().atStartOfDay().atOffset(ZoneOffset.UTC))
                    .param("jobId", jobId)
                    .param("email", application.email())
                    .update();
            return;
        }
        insertApplication(
                jobId,
                userId,
                application.email(),
                application.firstName() + " " + application.lastName(),
                job,
                application.status(),
                application.completion(),
                application.appliedOn(),
                application.cgpa(),
                application.university());
    }

    private void insertApplication(
            long jobId,
            Long userId,
            String email,
            String name,
            JobSeed job,
            String status,
            int completion,
            LocalDate appliedOn,
            Double cgpa,
            String university
    ) {
        jdbc.sql("""
                INSERT INTO applications (
                  job_id, candidate_user_id, candidate_email, candidate_name,
                  abbr, title, dept, date, status, completion, cgpa, university, applied_at
                ) VALUES (
                  :jobId, :userId, :email, :name,
                  :abbr, :title, :dept, :date, :status, :completion, :cgpa, :university, :appliedAt
                )
                """)
                .param("jobId", jobId)
                .param("userId", userId)
                .param("email", email)
                .param("name", name)
                .param("abbr", job.abbr())
                .param("title", job.title())
                .param("dept", job.dept())
                .param("date", appliedOn.toString())
                .param("status", status)
                .param("completion", completion)
                .param("cgpa", cgpa)
                .param("university", university)
                .param("appliedAt", appliedOn.atStartOfDay().atOffset(ZoneOffset.UTC))
                .update();
    }

    private boolean applicationExists(long jobId, String email) {
        Integer count = jdbc.sql("""
                SELECT count(*) FROM applications
                WHERE job_id = :jobId AND lower(candidate_email) = lower(:email)
                """)
                .param("jobId", jobId)
                .param("email", email)
                .query(Integer.class)
                .single();
        return count != null && count > 0;
    }

    private Optional<Long> findUserId(String email) {
        return jdbc.sql("SELECT id FROM users WHERE lower(email) = lower(:email)")
                .param("email", email)
                .query(Long.class)
                .optional();
    }

    private Map<String, Long> departmentIdsByCode() {
        Map<String, Long> map = new HashMap<>();
        jdbc.sql("SELECT id, code FROM departments")
                .query((row, n) -> {
                    map.put(row.getString("code"), row.getLong("id"));
                    return null;
                })
                .list();
        return map;
    }

    private Map<String, Long> jobIdsByAbbr() {
        Map<String, Long> map = new HashMap<>();
        jdbc.sql("SELECT id, abbr FROM jobs WHERE job_ref LIKE 'caa-seed-%'")
                .query((row, n) -> {
                    map.put(row.getString("abbr"), row.getLong("id"));
                    return null;
                })
                .list();
        return map;
    }

    private String titleForAbbr(String abbr) {
        return SeedCatalog.JOBS.stream()
                .filter(job -> job.abbr().equals(abbr))
                .map(JobSeed::title)
                .findFirst()
                .orElse(abbr);
    }

    private static long nextLcg(long state) {
        return (state * 1664525L + 1013904223L) & 0xffffffffL;
    }

    private static List<CandidateSeed> demoCandidates() {
        return List.of(
                new CandidateSeed(
                        "jbukenya@gmail.com",
                        "John",
                        "Bukenya",
                        CANDIDATE_PASSWORD,
                        "external",
                        """
                        {"dob":"1990-03-15","gender":"Male","nationality":"Ugandan",
                         "nin":"CM79000315BUKJN","phone":"+256 701 234 567",
                         "address":"Plot 45, Kisaasi, Kampala"}
                        """,
                        "Degree",
                        """
                        [{"level":"Degree","title":"BSc Electrical Engineering","institution":"Makerere University","year":"2013"},
                         {"level":"A-Level","title":"PCM","institution":"Namilyango College","year":"2009"}]
                        """,
                        """
                        ["Air Traffic Control","Radar Systems","Radio Communication",
                         "ICAO Procedures","Emergency Handling","Team Leadership"]
                        """,
                        """
                        [{"title":"Air Traffic Control Officer","organization":"Uganda Civil Aviation Authority",
                          "from":"2014-01-01","to":"2024-12-31",
                          "description":"Managed en-route and approach traffic at Entebbe ACC. Supervised radar and procedural control."}]
                        """,
                        """
                        [{"name":"Col. Peter Wamala","title":"Director, Air Traffic Management","organization":"UCAA",
                          "phone":"+256 414 352 000","email":"pwamala@caa.go.ug"},
                         {"name":"Dr. Rose Nalwoga","title":"Head of Department","organization":"Makerere University",
                          "phone":"+256 772 900 100","email":"r.nalwoga@mak.ac.ug"}]
                        """,
                        """
                        {"name":"Margaret Bukenya","relationship":"Spouse","phone":"+256 772 111 222"}
                        """),
                new CandidateSeed(
                        "mauma@gmail.com",
                        "Mary",
                        "Auma",
                        CANDIDATE_PASSWORD,
                        "external",
                        """
                        {"dob":"1992-07-22","gender":"Female","nationality":"Ugandan",
                         "nin":"CF92000722AUMAM","phone":"+256 772 345 678",
                         "address":"Plot 12B, Ntinda, Kampala"}
                        """,
                        "Degree",
                        """
                        [{"level":"Degree","title":"Bachelor of Commerce (Accounting)","institution":"Kyambogo University","year":"2015"},
                         {"level":"A-Level","title":"ECA","institution":"St Mary's College Namagunga","year":"2011"}]
                        """,
                        """
                        ["Financial Analysis","Revenue Assurance","IFRS Reporting","MS Excel",
                         "QuickBooks","Budget Planning","Internal Audit"]
                        """,
                        """
                        [{"title":"Finance Analyst","organization":"Stanbic Bank Uganda",
                          "from":"2016-03-01","to":"2024-01-31",
                          "description":"Led revenue assurance audits, reconciled accounts, and produced monthly financial and compliance reports."}]
                        """,
                        """
                        [{"name":"Mr. Charles Kiggundu","title":"Chief Finance Officer","organization":"Stanbic Bank Uganda",
                          "phone":"+256 312 224 600","email":"c.kiggundu@stanbicbank.ug"},
                         {"name":"Dr. Beatrice Nampijja","title":"Dean, School of Business","organization":"Kyambogo University",
                          "phone":"+256 414 287 100","email":"b.nampijja@kyu.ac.ug"}]
                        """,
                        """
                        {"name":"Robert Auma","relationship":"Brother","phone":"+256 701 999 888"}
                        """));
    }

    private static List<ApplicationSeed> pinnedApplications() {
        return List.of(
                new ApplicationSeed("ATC", "jbukenya@gmail.com", "John", "Bukenya",
                        "Shortlisted", 100, LocalDate.of(2026, 6, 3), null, null),
                new ApplicationSeed("FIN", "mauma@gmail.com", "Mary", "Auma",
                        "Under Review", 85, LocalDate.of(2026, 5, 28), null, null),
                new ApplicationSeed("SYS", "pnkutu@gmail.com", "Peter", "Nkutu",
                        "Pending", 60, LocalDate.of(2026, 5, 15), null, null),
                new ApplicationSeed("ATT", "kssali@student.mak.ac.ug", "Kevin", "Ssali",
                        "Shortlisted", 95, LocalDate.of(2026, 6, 1), 4.7, "Makerere University"),
                new ApplicationSeed("ATT", "bakello@student.mak.ac.ug", "Brenda", "Akello",
                        "Under Review", 90, LocalDate.of(2026, 6, 2), 4.3, "Makerere University"),
                new ApplicationSeed("ATT", "imucunguzi@student.ucu.ac.ug", "Ivan", "Mucunguzi",
                        "Pending", 80, LocalDate.of(2026, 6, 3), 3.9, "Uganda Christian University"),
                new ApplicationSeed("ATT", "snabirye@student.must.ac.ug", "Stella", "Nabirye",
                        "Pending", 70, LocalDate.of(2026, 6, 5), 3.6, "Mbarara University"),
                new ApplicationSeed("ATT", "roulanyah@student.gulu.ac.ug", "Ronald", "Oulanyah",
                        "Declined", 55, LocalDate.of(2026, 6, 7), 2.8, "Gulu University"),
                new ApplicationSeed("SYS", "jbukenya@gmail.com", "John", "Bukenya",
                        "Under Review", 90, LocalDate.of(2026, 5, 20), null, null),
                new ApplicationSeed("NET", "jbukenya@gmail.com", "John", "Bukenya",
                        "Pending", 65, LocalDate.of(2026, 6, 10), null, null));
    }
}
