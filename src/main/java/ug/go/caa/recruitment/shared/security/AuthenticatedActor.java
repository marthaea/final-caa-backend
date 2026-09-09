package ug.go.caa.recruitment.shared.security;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import ug.go.caa.recruitment.shared.web.ApiException;

public record AuthenticatedActor(
        long id,
        String email,
        String accountType,
        String effectiveType,
        String adminRole,
        String firstName,
        String lastName
) {
    public static AuthenticatedActor from(Jwt jwt) {
        Number id = jwt.getClaim("id");
        if (id == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Unauthorised");
        }
        return new AuthenticatedActor(
                id.longValue(),
                jwt.getClaimAsString("email"),
                jwt.getClaimAsString("accountType"),
                jwt.getClaimAsString("effectiveType"),
                jwt.getClaimAsString("adminRole"),
                jwt.getClaimAsString("firstName"),
                jwt.getClaimAsString("lastName"));
    }

    public String displayName() {
        return firstName + " " + lastName;
    }

    public boolean isAdmin() {
        return "admin".equals(accountType);
    }
}
