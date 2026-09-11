package ug.go.caa.recruitment.feature.identity.web;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ug.go.caa.recruitment.feature.identity.application.StaffService;
import ug.go.caa.recruitment.feature.identity.application.StaffService.CreateStaffCommand;
import ug.go.caa.recruitment.feature.identity.application.StaffService.StaffResponse;
import ug.go.caa.recruitment.shared.security.AuthenticatedActor;
import ug.go.caa.recruitment.shared.web.ApiResponse;

@RestController
@RequestMapping("/api/staff")
public class StaffController {

    private final StaffService staff;

    public StaffController(StaffService staff) {
        this.staff = staff;
    }

    @GetMapping
    ApiResponse<List<StaffResponse>> findAll(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String search
    ) {
        List<StaffResponse> result = staff.findAll(AuthenticatedActor.from(jwt), search);
        return ApiResponse.list(result, result.size());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<StaffResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody StaffRequest request
    ) {
        return ApiResponse.success(staff.create(
                AuthenticatedActor.from(jwt),
                new CreateStaffCommand(
                        request.employeeNumber(), request.firstName(), request.lastName(),
                        request.dept(), request.position(), request.email(), request.joined(),
                        request.status())));
    }

    @GetMapping("/verify/{employeeNumber}")
    Map<String, Boolean> verify(@PathVariable String employeeNumber) {
        return Map.of("exists", staff.exists(employeeNumber));
    }

    record StaffRequest(
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
}
