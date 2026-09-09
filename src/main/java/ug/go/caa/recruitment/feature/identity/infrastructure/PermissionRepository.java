package ug.go.caa.recruitment.feature.identity.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import ug.go.caa.recruitment.feature.identity.application.RolePermissions;

@Repository
public class PermissionRepository {

    private static final Map<String, String> COLUMNS = columns();

    private final JdbcClient jdbc;

    public PermissionRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<PermissionOverride> findByEmail(String email) {
        return jdbc.sql("SELECT * FROM permission_overrides WHERE lower(email) = lower(:email) LIMIT 1")
                .param("email", email)
                .query(this::map)
                .optional();
    }

    public List<PermissionOverride> findAll() {
        return jdbc.sql("SELECT * FROM permission_overrides ORDER BY email")
                .query(this::map)
                .list();
    }

    public PermissionOverride save(String email, String role, Map<String, Boolean> values) {
        JdbcClient.StatementSpec statement = jdbc.sql("""
                INSERT INTO permission_overrides (
                    email, role, can_view_applications, can_shortlist, can_screen_interns,
                    can_send_notifications, can_manage_jobs, can_manage_criteria,
                    can_view_staff, can_export, can_view_audit, can_manage_settings,
                    can_grant_permissions, can_review_job, can_approve_job,
                    can_manage_departments, can_manage_admins, can_assign_rights,
                    can_schedule_assessment, can_record_assessment
                ) VALUES (
                    lower(:email), :role, :canViewApplications, :canShortlist, :canScreenInterns,
                    :canSendNotifications, :canManageJobs, :canManageCriteria,
                    :canViewStaff, :canExport, :canViewAudit, :canManageSettings,
                    :canGrantPermissions, :canReviewJob, :canApproveJob,
                    :canManageDepartments, :canManageAdmins, :canAssignRights,
                    :canScheduleAssessment, :canRecordAssessment
                )
                ON CONFLICT (lower(email)) WHERE email IS NOT NULL DO UPDATE SET
                    role = excluded.role,
                    can_view_applications = excluded.can_view_applications,
                    can_shortlist = excluded.can_shortlist,
                    can_screen_interns = excluded.can_screen_interns,
                    can_send_notifications = excluded.can_send_notifications,
                    can_manage_jobs = excluded.can_manage_jobs,
                    can_manage_criteria = excluded.can_manage_criteria,
                    can_view_staff = excluded.can_view_staff,
                    can_export = excluded.can_export,
                    can_view_audit = excluded.can_view_audit,
                    can_manage_settings = excluded.can_manage_settings,
                    can_grant_permissions = excluded.can_grant_permissions,
                    can_review_job = excluded.can_review_job,
                    can_approve_job = excluded.can_approve_job,
                    can_manage_departments = excluded.can_manage_departments,
                    can_manage_admins = excluded.can_manage_admins,
                    can_assign_rights = excluded.can_assign_rights,
                    can_schedule_assessment = excluded.can_schedule_assessment,
                    can_record_assessment = excluded.can_record_assessment,
                    updated_at = now()
                """)
                .param("email", email)
                .param("role", role);
        for (String key : RolePermissions.KEYS) {
            statement = statement.param(key, Boolean.TRUE.equals(values.get(key)));
        }
        statement.update();
        return findByEmail(email).orElseThrow();
    }

    public void recordUpdate(long actorId, String actor, String actorRole, String email, String role) {
        jdbc.sql("""
                INSERT INTO audit_log (actor_user_id, actor, role, action, target)
                VALUES (:actorId, :actor, :actorRole, 'Updated permissions', :target)
                """)
                .param("actorId", actorId)
                .param("actor", actor)
                .param("actorRole", actorRole)
                .param("target", email + " (" + role + ")")
                .update();
    }

    private PermissionOverride map(ResultSet result, int rowNumber) throws SQLException {
        Map<String, Boolean> values = new LinkedHashMap<>();
        COLUMNS.forEach((key, column) -> {
            try {
                values.put(key, result.getBoolean(column));
            } catch (SQLException exception) {
                throw new PermissionMappingException(exception);
            }
        });
        return new PermissionOverride(result.getString("email"), result.getString("role"), Map.copyOf(values));
    }

    private static Map<String, String> columns() {
        Map<String, String> columns = new LinkedHashMap<>();
        for (String key : RolePermissions.KEYS) {
            columns.put(key, key.replaceAll("([A-Z])", "_$1").toLowerCase());
        }
        return Map.copyOf(columns);
    }

    public record PermissionOverride(String email, String role, Map<String, Boolean> values) {
    }

    private static final class PermissionMappingException extends RuntimeException {
        private PermissionMappingException(SQLException cause) {
            super(cause);
        }
    }
}
