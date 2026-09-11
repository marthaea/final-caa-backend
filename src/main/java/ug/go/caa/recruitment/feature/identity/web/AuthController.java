package ug.go.caa.recruitment.feature.identity.web;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ug.go.caa.recruitment.feature.identity.application.IdentityService;
import ug.go.caa.recruitment.feature.identity.application.IdentityService.AuthUser;
import ug.go.caa.recruitment.feature.identity.application.IdentityService.RegisterCommand;
import ug.go.caa.recruitment.feature.identity.application.IdentityService.RefreshResult;
import ug.go.caa.recruitment.shared.security.WebSecurityProperties;
import ug.go.caa.recruitment.shared.web.ApiException;
import ug.go.caa.recruitment.shared.web.ApiResponse;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final String REFRESH_COOKIE = "caa_refresh";

    private final IdentityService identity;
    private final WebSecurityProperties security;

    public AuthController(IdentityService identity, WebSecurityProperties security) {
        this.identity = identity;
        this.security = security;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<RegisterResponse> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletResponse response
    ) {
        AuthUser user = identity.register(new RegisterCommand(
                request.email(), request.password(), request.firstName(),
                request.lastName(), request.accountType(), request.employeeNumber()));
        setRefreshCookie(response, identity.refreshTokenFor(user.id()));
        return ApiResponse.success(new RegisterResponse(
                user.id(), user.email(), user.firstName(), user.lastName(),
                user.accountType(), user.effectiveType(), user.emailVerified(), user.token()));
    }

    @PostMapping("/login")
    ApiResponse<AuthUser> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response
    ) {
        AuthUser user = identity.login(request.email(), request.password());
        setRefreshCookie(response, identity.refreshTokenFor(user.id()));
        return ApiResponse.success(user);
    }

    @PostMapping("/refresh-token")
    ApiResponse<Map<String, String>> refresh(
            @org.springframework.web.bind.annotation.CookieValue(
                    name = REFRESH_COOKIE,
                    required = false
            ) String refreshToken,
            HttpServletResponse response
    ) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "No refresh token");
        }
        try {
            RefreshResult result = identity.refresh(refreshToken);
            setRefreshCookie(response, result.refreshToken());
            return ApiResponse.success(Map.of("token", result.accessToken()));
        } catch (ApiException exception) {
            clearRefreshCookie(response);
            throw exception;
        }
    }

    @GetMapping("/me")
    ApiResponse<IdentityService.MeResponse> me(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(identity.me(jwt));
    }

    @PutMapping("/profile")
    ApiResponse<IdentityService.ProfileResponse> profile(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ProfileRequest request
    ) {
        Number userId = jwt.getClaim("id");
        if (userId == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Unauthorised");
        }
        return ApiResponse.success(identity.updateProfile(
                userId.longValue(), request.firstName(), request.lastName(), request.email()));
    }

    @GetMapping("/verify-email")
    ApiResponse<Map<String, Object>> verifyEmail(@RequestParam(required = false) String token) {
        return ApiResponse.success(identity.verifyEmail(token));
    }

    @PostMapping("/resend-verification")
    ApiResponse<Map<String, String>> resendVerification(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(Map.of(
                "message",
                identity.resendVerification(requiredUserId(jwt))));
    }

    @PostMapping("/forgot-password")
    ApiResponse<Map<String, String>> forgotPassword(@Valid @RequestBody EmailRequest request) {
        identity.forgotPassword(request.email());
        return ApiResponse.success(Map.of(
                "message",
                IdentityService.NEUTRAL_RESET_MESSAGE));
    }

    @PostMapping("/reset-password")
    ApiResponse<Map<String, String>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        identity.resetPassword(request.token(), request.password());
        return ApiResponse.success(Map.of(
                "message",
                "Password reset successful. You can now log in with your new password."));
    }

    @PostMapping("/logout")
    ApiResponse<Map<String, String>> logout(
            @AuthenticationPrincipal Jwt jwt,
            HttpServletResponse response
    ) {
        identity.logout(requiredUserId(jwt));
        clearRefreshCookie(response);
        return ApiResponse.success(Map.of("message", "Logged out"));
    }

    private long requiredUserId(Jwt jwt) {
        Number userId = jwt.getClaim("id");
        if (userId == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Unauthorised");
        }
        return userId.longValue();
    }

    private void setRefreshCookie(HttpServletResponse response, String token) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE, token)
                .httpOnly(true)
                .secure(security.secureCookie())
                .sameSite(security.sameSite())
                .path("/")
                .maxAge(security.refreshTokenTtl())
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE, "")
                .httpOnly(true)
                .secure(security.secureCookie())
                .sameSite(security.sameSite())
                .path("/")
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    record RegisterRequest(
            @NotBlank @Email(message = "Valid email required") String email,
            @NotBlank(message = "Password must be at least 8 characters")
            @Size(min = 8, message = "Password must be at least 8 characters")
            @Pattern(regexp = ".*\\d.*", message = "Password must contain at least one number")
            String password,
            @NotBlank(message = "First name required")
            @Size(max = 100, message = "First name must not exceed 100 characters")
            String firstName,
            @NotBlank(message = "Last name required")
            @Size(max = 100, message = "Last name must not exceed 100 characters")
            String lastName,
            @NotBlank(message = "accountType must be external or internal")
            @Pattern(regexp = "external|internal", message = "accountType must be external or internal")
            String accountType,
            String employeeNumber
    ) {
    }

    record LoginRequest(
            @NotBlank @Email(message = "Valid email required") String email,
            @NotBlank(message = "Password required") String password
    ) {
    }

    record ProfileRequest(
            @Size(min = 1, max = 100, message = "First name must be 1–100 characters")
            String firstName,
            @Size(min = 1, max = 100, message = "Last name must be 1–100 characters")
            String lastName,
            @Email(message = "Valid email required") String email
    ) {
    }

    record EmailRequest(@NotBlank @Email(message = "Valid email required") String email) {
    }

    record ResetPasswordRequest(
            @NotBlank(message = "Invalid reset token")
            @Pattern(regexp = "[0-9a-fA-F]{64}", message = "Invalid reset token")
            String token,
            @NotBlank(message = "Password must be at least 8 characters")
            @Size(min = 8, message = "Password must be at least 8 characters")
            @Pattern(regexp = ".*\\d.*", message = "Password must contain at least one number")
            String password
    ) {
    }

    record RegisterResponse(
            long id,
            String email,
            String firstName,
            String lastName,
            String accountType,
            String effectiveType,
            boolean emailVerified,
            String token
    ) {
    }
}
