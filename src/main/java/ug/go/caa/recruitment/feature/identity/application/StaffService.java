package ug.go.caa.recruitment.feature.identity.application;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ug.go.caa.recruitment.feature.identity.infrastructure.StaffRepository;
import ug.go.caa.recruitment.feature.identity.infrastructure.StaffRepository.CreateStaff;
import ug.go.caa.recruitment.feature.identity.infrastructure.StaffRepository.StaffMember;
import ug.go.caa.recruitment.shared.security.AuthenticatedActor;
import ug.go.caa.recruitment.shared.web.ApiException;

@Service
public class StaffService {

    private final StaffRepository repository;
    private final AuthorizationService authorization;

    public StaffService(StaffRepository repository, AuthorizationService authorization) {
        this.repository = repository;
        this.authorization = authorization;
    }

    public List<StaffResponse> findAll(AuthenticatedActor actor, String search) {
        // canViewStaff alone excludes hr_officer/hr/recruiter/dhra/hod — the
        // roles that run Interview Panel selection and need to browse the
        // staff directory to do it, not just HR admin proper.
        boolean canView = authorization.hasPermission(actor, "canViewStaff")
                || authorization.hasPermission(actor, "canShortlist");
        if (!canView) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Permission denied");
        }
        return repository.findAll(search).stream().map(this::response).toList();
    }

    @Transactional
    public StaffResponse create(AuthenticatedActor actor, CreateStaffCommand command) {
        authorization.requirePermission(actor, "canViewStaff");
        if (isBlank(command.employeeNumber()) || isBlank(command.firstName()) || isBlank(command.lastName())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Employee number, first name, and last name are required");
        }
        String employeeNumber = command.employeeNumber().trim();
        if (repository.exists(employeeNumber)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "A staff record with this employee number already exists");
        }
        StaffMember created = repository.create(new CreateStaff(
                employeeNumber,
                command.firstName().trim(),
                command.lastName().trim(),
                trimToNull(command.dept()),
                trimToNull(command.position()),
                trimToNull(command.email()),
                parseDate(command.joined()),
                isBlank(command.status()) ? "Active" : command.status()));
        repository.recordCreation(
                actor.id(), actor.displayName(), actor.adminRole(),
                created.firstName() + " " + created.lastName(), employeeNumber);
        return response(created);
    }

    public boolean exists(String employeeNumber) {
        return repository.exists(employeeNumber);
    }

    private StaffResponse response(StaffMember member) {
        return new StaffResponse(
                member.id(), member.employeeNumber(), member.firstName(), member.lastName(),
                member.dept(), member.position(), member.email(),
                member.joinedDate() == null ? "null" : member.joinedDate().toString(),
                member.status());
    }

    private LocalDate parseDate(String value) {
        if (isBlank(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid joined date");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String trimToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    public record CreateStaffCommand(
            String employeeNumber,
            String firstName,
            String lastName,
            String dept,
            String position,
            String email,
            String joined,
            String status
    ) {
    }

    public record StaffResponse(
            long id,
            String empNo,
            String firstName,
            String lastName,
            String dept,
            String position,
            String email,
            String joined,
            String status
    ) {
    }
}
