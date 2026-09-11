package ug.go.caa.recruitment.feature.recruitment.web;

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
import ug.go.caa.recruitment.feature.recruitment.application.DepartmentService;
import ug.go.caa.recruitment.feature.recruitment.infrastructure.DepartmentRepository.Department;
import ug.go.caa.recruitment.shared.security.AuthenticatedActor;
import ug.go.caa.recruitment.shared.web.ApiResponse;

@RestController
@RequestMapping("/api/departments")
public class DepartmentsController {

    private final DepartmentService departments;

    public DepartmentsController(DepartmentService departments) {
        this.departments = departments;
    }

    @GetMapping
    ApiResponse<List<Department>> findAll(@AuthenticationPrincipal Jwt jwt) {
        List<Department> result = departments.findAll(AuthenticatedActor.from(jwt));
        return ApiResponse.list(result, result.size());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<Department> create(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody DepartmentRequest request
    ) {
        return ApiResponse.success(departments.create(
                AuthenticatedActor.from(jwt), request.name(), request.code()));
    }

    @PutMapping("/{id}")
    ApiResponse<Department> assignHead(
            @PathVariable long id,
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody HeadRequest request
    ) {
        return ApiResponse.success(departments.assignHead(
                AuthenticatedActor.from(jwt), id, request.headUserId()));
    }

    record DepartmentRequest(String name, String code) {
    }

    record HeadRequest(Long headUserId) {
    }
}
