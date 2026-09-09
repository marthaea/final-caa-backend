package ug.go.caa.recruitment.feature.identity.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ug.go.caa.recruitment.feature.identity.application.PermissionService;
import ug.go.caa.recruitment.shared.security.AuthenticatedActor;
import ug.go.caa.recruitment.shared.web.ApiResponse;

@RestController
@RequestMapping("/api/permissions")
public class PermissionsController {

    private final PermissionService permissions;

    public PermissionsController(PermissionService permissions) {
        this.permissions = permissions;
    }

    @GetMapping("/roles/defaults")
    ApiResponse<Map<String, Object>> defaults() {
        return ApiResponse.success(permissions.defaults());
    }

    @GetMapping
    ApiResponse<List<Map<String, Object>>> findAll(@AuthenticationPrincipal Jwt jwt) {
        List<Map<String, Object>> result = permissions.findAll(AuthenticatedActor.from(jwt));
        return ApiResponse.list(result, result.size());
    }

    @PutMapping
    ApiResponse<Map<String, Object>> save(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody PermissionRequest request
    ) {
        return ApiResponse.success(permissions.save(
                AuthenticatedActor.from(jwt), request.email(), request.role(), request.values()));
    }

    record PermissionRequest(
            @NotBlank @Email String email,
            @NotBlank String role,
            Boolean canViewApplications,
            Boolean canShortlist,
            Boolean canScreenInterns,
            Boolean canSendNotifications,
            Boolean canManageJobs,
            Boolean canManageCriteria,
            Boolean canViewStaff,
            Boolean canExport,
            Boolean canViewAudit,
            Boolean canManageSettings,
            Boolean canGrantPermissions,
            Boolean canReviewJob,
            Boolean canApproveJob,
            Boolean canManageDepartments,
            Boolean canManageAdmins,
            Boolean canAssignRights,
            Boolean canScheduleAssessment,
            Boolean canRecordAssessment
    ) {
        Map<String, Boolean> values() {
            Map<String, Boolean> values = new LinkedHashMap<>();
            values.put("canViewApplications", Boolean.TRUE.equals(canViewApplications));
            values.put("canShortlist", Boolean.TRUE.equals(canShortlist));
            values.put("canScreenInterns", Boolean.TRUE.equals(canScreenInterns));
            values.put("canSendNotifications", Boolean.TRUE.equals(canSendNotifications));
            values.put("canManageJobs", Boolean.TRUE.equals(canManageJobs));
            values.put("canManageCriteria", Boolean.TRUE.equals(canManageCriteria));
            values.put("canViewStaff", Boolean.TRUE.equals(canViewStaff));
            values.put("canExport", Boolean.TRUE.equals(canExport));
            values.put("canViewAudit", Boolean.TRUE.equals(canViewAudit));
            values.put("canManageSettings", Boolean.TRUE.equals(canManageSettings));
            values.put("canGrantPermissions", Boolean.TRUE.equals(canGrantPermissions));
            values.put("canReviewJob", Boolean.TRUE.equals(canReviewJob));
            values.put("canApproveJob", Boolean.TRUE.equals(canApproveJob));
            values.put("canManageDepartments", Boolean.TRUE.equals(canManageDepartments));
            values.put("canManageAdmins", Boolean.TRUE.equals(canManageAdmins));
            values.put("canAssignRights", Boolean.TRUE.equals(canAssignRights));
            values.put("canScheduleAssessment", Boolean.TRUE.equals(canScheduleAssessment));
            values.put("canRecordAssessment", Boolean.TRUE.equals(canRecordAssessment));
            return values;
        }
    }
}
