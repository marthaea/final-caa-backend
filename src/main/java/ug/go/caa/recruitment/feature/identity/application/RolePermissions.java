package ug.go.caa.recruitment.feature.identity.application;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RolePermissions {

    public static final List<String> ROLES = List.of(
            "super", "hr", "recruiter", "auditor", "hr_officer", "it_admin", "dhra", "hod");

    public static final List<String> KEYS = List.of(
            "canViewApplications", "canShortlist", "canScreenInterns",
            "canSendNotifications", "canManageJobs", "canManageCriteria",
            "canViewStaff", "canExport", "canViewAudit", "canManageSettings",
            "canGrantPermissions", "canReviewJob", "canApproveJob",
            "canManageDepartments", "canManageAdmins", "canAssignRights",
            "canScheduleAssessment", "canRecordAssessment");

    public static final Map<String, Map<String, Boolean>> DEFAULTS = defaults();

    private RolePermissions() {
    }

    private static Map<String, Map<String, Boolean>> defaults() {
        Map<String, Map<String, Boolean>> roles = new LinkedHashMap<>();
        roles.put("super", permissions(KEYS.toArray(String[]::new)));
        roles.put("hr", permissions(
                "canViewApplications", "canShortlist", "canScreenInterns",
                "canSendNotifications", "canManageJobs", "canManageCriteria",
                "canViewStaff", "canExport", "canScheduleAssessment"));
        roles.put("recruiter", permissions(
                "canViewApplications", "canShortlist", "canManageCriteria"));
        roles.put("auditor", permissions("canExport", "canViewAudit"));
        roles.put("hr_officer", permissions(
                "canViewApplications", "canShortlist", "canManageJobs",
                "canManageCriteria", "canSendNotifications", "canScheduleAssessment"));
        roles.put("it_admin", permissions("canAssignRights"));
        roles.put("dhra", permissions(
                "canViewApplications", "canShortlist", "canApproveJob",
                "canExport", "canViewAudit", "canScheduleAssessment", "canRecordAssessment"));
        roles.put("hod", permissions(
                "canViewApplications", "canShortlist", "canReviewJob", "canRecordAssessment"));
        return Map.copyOf(roles);
    }

    private static Map<String, Boolean> permissions(String... allowed) {
        Map<String, Boolean> values = new LinkedHashMap<>();
        KEYS.forEach(key -> values.put(key, false));
        for (String key : allowed) {
            values.put(key, true);
        }
        return Map.copyOf(values);
    }
}
