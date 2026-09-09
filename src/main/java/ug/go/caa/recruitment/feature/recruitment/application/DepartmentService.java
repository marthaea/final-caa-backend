package ug.go.caa.recruitment.feature.recruitment.application;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ug.go.caa.recruitment.feature.identity.application.AuthorizationService;
import ug.go.caa.recruitment.feature.recruitment.infrastructure.DepartmentRepository;
import ug.go.caa.recruitment.feature.recruitment.infrastructure.DepartmentRepository.Department;
import ug.go.caa.recruitment.shared.persistence.AuditWriter;
import ug.go.caa.recruitment.shared.security.AuthenticatedActor;
import ug.go.caa.recruitment.shared.web.ApiException;

@Service
public class DepartmentService {

    private final DepartmentRepository repository;
    private final AuthorizationService authorization;
    private final AuditWriter audit;

    public DepartmentService(
            DepartmentRepository repository,
            AuthorizationService authorization,
            AuditWriter audit
    ) {
        this.repository = repository;
        this.authorization = authorization;
        this.audit = audit;
    }

    public List<Department> findAll(AuthenticatedActor actor) {
        if (!authorization.hasPermission(actor, "canManageJobs")
                && !authorization.hasPermission(actor, "canManageDepartments")) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Permission denied");
        }
        return repository.findAll();
    }

    @Transactional
    public Department create(AuthenticatedActor actor, String name, String code) {
        authorization.requirePermission(actor, "canManageDepartments");
        if (blank(name) || blank(code)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Department name and code are required");
        }
        if (repository.codeExists(code)) {
            throw new ApiException(HttpStatus.CONFLICT, "A department with this code already exists");
        }
        Department department = repository.create(name, code);
        audit.write(actor, "Added department", name + " (" + code + ")");
        return department;
    }

    @Transactional
    public Department assignHead(AuthenticatedActor actor, long id, Long headUserId) {
        authorization.requirePermission(actor, "canManageDepartments");
        Department existing = repository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Department not found"));
        if (headUserId != null && !repository.isHod(headUserId)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "headUserId must be an existing admin user with the Head of Department role");
        }
        Department updated = repository.assignHead(id, headUserId);
        audit.write(actor, "Assigned department head", existing.name() + " (" + existing.code() + ")");
        return updated;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
