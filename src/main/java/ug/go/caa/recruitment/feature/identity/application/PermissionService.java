package ug.go.caa.recruitment.feature.identity.application;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ug.go.caa.recruitment.feature.identity.infrastructure.PermissionRepository;
import ug.go.caa.recruitment.feature.identity.infrastructure.PermissionRepository.PermissionOverride;
import ug.go.caa.recruitment.shared.security.AuthenticatedActor;

@Service
public class PermissionService {

    private final PermissionRepository repository;
    private final AuthorizationService authorization;

    public PermissionService(PermissionRepository repository, AuthorizationService authorization) {
        this.repository = repository;
        this.authorization = authorization;
    }

    public Map<String, Object> defaults() {
        return Map.of("roles", RolePermissions.ROLES, "defaults", RolePermissions.DEFAULTS);
    }

    public List<Map<String, Object>> findAll(AuthenticatedActor actor) {
        authorization.requirePermission(actor, "canGrantPermissions");
        return repository.findAll().stream().map(this::response).toList();
    }

    @Transactional
    public Map<String, Object> save(
            AuthenticatedActor actor,
            String email,
            String role,
            Map<String, Boolean> values
    ) {
        authorization.requirePermission(actor, "canGrantPermissions");
        PermissionOverride saved = repository.save(email, role, values);
        repository.recordUpdate(actor.id(), actor.displayName(), actor.adminRole(), email, role);
        return response(saved);
    }

    private Map<String, Object> response(PermissionOverride override) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("email", override.email());
        response.put("role", override.role());
        response.putAll(override.values());
        return response;
    }
}
