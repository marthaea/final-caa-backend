package ug.go.caa.recruitment.feature.identity.application;

import java.util.Arrays;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import ug.go.caa.recruitment.feature.identity.infrastructure.PermissionRepository;
import ug.go.caa.recruitment.shared.security.AuthenticatedActor;
import ug.go.caa.recruitment.shared.web.ApiException;

@Service
public class AuthorizationService {

    private final PermissionRepository permissions;

    public AuthorizationService(PermissionRepository permissions) {
        this.permissions = permissions;
    }

    public void requireRole(AuthenticatedActor actor, String... roles) {
        if (!actor.isAdmin() || Arrays.stream(roles).noneMatch(role -> role.equals(actor.adminRole()))) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Forbidden");
        }
    }

    public void requirePermission(AuthenticatedActor actor, String permission) {
        if (!actor.isAdmin()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Forbidden");
        }
        if (!hasPermission(actor, permission)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Permission denied");
        }
    }

    /** Grants access if the actor holds any one of the listed permissions —
     *  for the handful of endpoints two otherwise-disjoint role groups both
     *  legitimately need (e.g. shortlisting staff and auditors both need the
     *  staff directory). Keeps the same Forbidden/Permission-denied split as
     *  requirePermission: a non-admin is rejected before any permission is
     *  even checked. */
    public void requireAnyPermission(AuthenticatedActor actor, String... anyOf) {
        if (!actor.isAdmin()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Forbidden");
        }
        if (Arrays.stream(anyOf).noneMatch(p -> hasPermission(actor, p))) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Permission denied");
        }
    }

    public boolean hasPermission(AuthenticatedActor actor, String permission) {
        if (!actor.isAdmin()) {
            return false;
        }
        Map<String, Boolean> defaults = RolePermissions.DEFAULTS.get(actor.adminRole());
        if (defaults == null) {
            return false;
        }
        return permissions.findByEmail(actor.email())
                .map(override -> Boolean.TRUE.equals(override.values().get(permission)))
                .orElse(Boolean.TRUE.equals(defaults.get(permission)));
    }
}
