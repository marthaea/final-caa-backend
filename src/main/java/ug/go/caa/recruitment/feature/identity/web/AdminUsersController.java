package ug.go.caa.recruitment.feature.identity.web;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ug.go.caa.recruitment.feature.identity.application.AdminUserService;
import ug.go.caa.recruitment.feature.identity.application.AdminUserService.CreateAdmin;
import ug.go.caa.recruitment.feature.identity.infrastructure.AdminUserRepository.AdminUser;
import ug.go.caa.recruitment.shared.security.AuthenticatedActor;
import ug.go.caa.recruitment.shared.web.ApiResponse;

@RestController
@RequestMapping("/api/users/admin")
public class AdminUsersController {

    private final AdminUserService admins;

    public AdminUsersController(AdminUserService admins) {
        this.admins = admins;
    }

    @GetMapping
    ApiResponse<List<AdminUser>> findAll(@AuthenticationPrincipal Jwt jwt) {
        List<AdminUser> result = admins.findAll(AuthenticatedActor.from(jwt));
        return ApiResponse.list(result, result.size());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<AdminUser> create(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody AdminRequest request
    ) {
        return ApiResponse.success(admins.create(
                AuthenticatedActor.from(jwt),
                new CreateAdmin(
                        request.email(), request.password(), request.firstName(),
                        request.lastName(), request.adminRole())));
    }

    @PutMapping("/{id}/password")
    ApiResponse<Void> changePassword(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long id,
            @RequestBody PasswordRequest request
    ) {
        admins.changePassword(AuthenticatedActor.from(jwt), id, request.password());
        return ApiResponse.success(null);
    }

    record AdminRequest(
            String email,
            String password,
            String firstName,
            String lastName,
            String adminRole
    ) {
    }

    record PasswordRequest(String password) {
    }
}
