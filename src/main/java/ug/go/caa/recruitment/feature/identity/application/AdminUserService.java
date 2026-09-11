package ug.go.caa.recruitment.feature.identity.application;

import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ug.go.caa.recruitment.feature.identity.infrastructure.AdminUserRepository;
import ug.go.caa.recruitment.feature.identity.infrastructure.AdminUserRepository.AdminUser;
import ug.go.caa.recruitment.shared.security.AuthenticatedActor;
import ug.go.caa.recruitment.shared.web.ApiException;

@Service
public class AdminUserService {

    private final AdminUserRepository repository;
    private final AuthorizationService authorization;
    private final PasswordEncoder passwordEncoder;

    public AdminUserService(
            AdminUserRepository repository,
            AuthorizationService authorization,
            PasswordEncoder passwordEncoder
    ) {
        this.repository = repository;
        this.authorization = authorization;
        this.passwordEncoder = passwordEncoder;
    }

    public List<AdminUser> findAll(AuthenticatedActor actor) {
        if (!authorization.hasPermission(actor, "canManageAdmins")
                && !authorization.hasPermission(actor, "canAssignRights")) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Permission denied");
        }
        return repository.findAll();
    }

    @Transactional
    public AdminUser create(AuthenticatedActor actor, CreateAdmin command) {
        authorization.requireRole(actor, "super");
        if (isBlank(command.email()) || isBlank(command.password()) || isBlank(command.firstName())
                || isBlank(command.lastName()) || isBlank(command.adminRole())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Email, password, first name, last name, and role are required");
        }
        if (!RolePermissions.ROLES.contains(command.adminRole())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "adminRole must be one of: " + String.join(", ", RolePermissions.ROLES));
        }
        if (command.password().length() < 8) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Password must be at least 8 characters");
        }
        String email = command.email().trim().toLowerCase(Locale.ROOT);
        if (repository.emailExists(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "A user with this email already exists");
        }
        AdminUser created = repository.create(
                email,
                passwordEncoder.encode(command.password()),
                command.firstName().trim(),
                command.lastName().trim(),
                command.adminRole());
        repository.recordCreation(
                actor.id(), actor.displayName(), actor.adminRole(),
                command.firstName(), command.lastName(), email, command.adminRole());
        return created;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record CreateAdmin(
            String email,
            String password,
            String firstName,
            String lastName,
            String adminRole
    ) {
    }
}
