package ug.go.caa.recruitment.feature.identity.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ug.go.caa.recruitment.feature.identity.infrastructure.IdentityRepository;
import ug.go.caa.recruitment.feature.identity.infrastructure.IdentityRepository.RefreshSession;
import ug.go.caa.recruitment.feature.identity.infrastructure.IdentityRepository.UserAccount;
import ug.go.caa.recruitment.shared.security.TokenService;
import ug.go.caa.recruitment.shared.security.TokenService.UserTokenDetails;
import ug.go.caa.recruitment.shared.web.ApiException;

@Service
public class IdentityService {

    public static final String NEUTRAL_RESET_MESSAGE =
            "If that email is registered, a password reset link has been sent";

    private final IdentityRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokens;
    private final IdentityEventPublisher events;
    private final SecureRandom random = new SecureRandom();
    private final Clock clock;

    @Autowired
    public IdentityService(
            IdentityRepository repository,
            PasswordEncoder passwordEncoder,
            TokenService tokens,
            IdentityEventPublisher events
    ) {
        this(repository, passwordEncoder, tokens, events, Clock.systemUTC());
    }

    IdentityService(
            IdentityRepository repository,
            PasswordEncoder passwordEncoder,
            TokenService tokens,
            IdentityEventPublisher events,
            Clock clock
    ) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public AuthUser register(RegisterCommand command) {
        String email = normalizeEmail(command.email());
        if (repository.findUserByEmail(email).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "Email already registered");
        }
        if ("internal".equals(command.accountType())
                && (command.employeeNumber() == null || !repository.staffExists(command.employeeNumber().trim()))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Employee number not found");
        }

        String verificationToken = randomToken();
        long id = repository.createUser(
                email,
                passwordEncoder.encode(command.password()),
                command.firstName().trim(),
                command.lastName().trim(),
                command.accountType(),
                "internal".equals(command.accountType()) ? command.employeeNumber().trim() : null,
                verificationToken);
        UserAccount user = requireUser(id);
        events.emailVerificationRequested(user.id(), user.email(), user.firstName(), verificationToken);
        events.welcomeRequested(user.id(), user.email(), user.firstName(), user.lastName());
        return response(user, tokens.accessToken(tokenDetails(user)));
    }

    public AuthUser login(String email, String password) {
        UserAccount user = repository.findUserByEmail(normalizeEmail(email))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
        if (!passwordEncoder.matches(password, user.passwordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
        ensureActive(user);
        events.loginRecorded(user.id(), user.email(), user.displayName(), user.role());
        return response(user, tokens.accessToken(tokenDetails(user)));
    }

    @Transactional
    public RefreshResult refresh(String token) {
        Jwt jwt;
        try {
            jwt = tokens.decodeRefreshToken(token);
        } catch (JwtException | IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid or expired refresh token");
        }
        Number idClaim = jwt.getClaim("id");
        Number versionClaim = jwt.getClaim("tv");
        Number sessionClaim = jwt.getClaim("sid");
        if (idClaim == null || sessionClaim == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid or expired refresh token");
        }
        UserAccount user = repository.findUserById(idClaim.longValue())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "User not found"));
        ensureActive(user);
        int tokenVersion = versionClaim == null ? 0 : versionClaim.intValue();
        if (tokenVersion != user.tokenVersion()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Session expired. Please log in again.");
        }
        RefreshSession session = repository.lockActiveRefreshSession(
                        sessionClaim.longValue(), clock.instant())
                .filter(existing -> existing.userId() == idClaim.longValue())
                .filter(existing -> MessageDigest.isEqual(
                        existing.tokenHash().getBytes(StandardCharsets.UTF_8),
                        sha256(token).getBytes(StandardCharsets.UTF_8)))
                .orElseThrow(() -> new ApiException(
                        HttpStatus.UNAUTHORIZED,
                        "Invalid or expired refresh token"));
        String newRefreshToken = issueRefreshToken(user);
        Jwt replacement = tokens.decodeRefreshToken(newRefreshToken);
        Number replacementSession = replacement.getClaim("sid");
        repository.rotateRefreshSession(session.id(), replacementSession.longValue(), clock.instant());
        return new RefreshResult(
                tokens.accessToken(tokenDetails(user)),
                newRefreshToken);
    }

    public MeResponse me(Jwt jwt) {
        return new MeResponse(
                requiredLongClaim(jwt, "id"),
                jwt.getClaimAsString("email"),
                jwt.getClaimAsString("firstName"),
                jwt.getClaimAsString("lastName"),
                jwt.getClaimAsString("accountType"),
                jwt.getClaimAsString("effectiveType"),
                jwt.getClaimAsString("adminRole"));
    }

    @Transactional
    public ProfileResponse updateProfile(long userId, String firstName, String lastName, String email) {
        UserAccount before = requireUser(userId);
        String normalizedEmail = email == null ? null : normalizeEmail(email);
        if (normalizedEmail != null) {
            repository.findUserByEmail(normalizedEmail)
                    .filter(existing -> existing.id() != userId)
                    .ifPresent(existing -> {
                        throw new ApiException(HttpStatus.CONFLICT, "Email already taken");
                    });
        }
        boolean emailChanged = normalizedEmail != null
                && !normalizedEmail.equalsIgnoreCase(before.email());
        if (emailChanged) {
            String verificationToken = randomToken();
            repository.updateProfileWithNewEmail(
                    userId, trimToNull(firstName), trimToNull(lastName), normalizedEmail, verificationToken);
            UserAccount user = requireUser(userId);
            events.emailVerificationRequested(user.id(), user.email(), user.firstName(), verificationToken);
            return new ProfileResponse(
                    user.id(), user.email(), user.firstName(), user.lastName(), user.accountType());
        }
        repository.updateProfile(userId, trimToNull(firstName), trimToNull(lastName), normalizedEmail);
        UserAccount user = requireUser(userId);
        return new ProfileResponse(
                user.id(), user.email(), user.firstName(), user.lastName(), user.accountType());
    }

    @Transactional
    public Map<String, Object> verifyEmail(String token) {
        if (token == null || !token.matches("[0-9a-fA-F]{64}")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid verification link");
        }
        UserAccount user = repository.findByVerificationToken(token)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "This verification link is invalid or has already been used"));
        repository.markEmailVerified(user.id());
        return Map.of("message", "Email verified", "email", user.email());
    }

    @Transactional
    public String resendVerification(long userId) {
        UserAccount user = requireUser(userId);
        if (user.emailVerified()) {
            return "Email already verified";
        }
        String token = user.verifyToken();
        if (token == null) {
            token = randomToken();
            repository.setVerificationToken(user.id(), token);
        }
        events.emailVerificationRequested(user.id(), user.email(), user.firstName(), token);
        return "Verification email sent";
    }

    @Transactional
    public void forgotPassword(String email) {
        // Only registered, active users receive a reset email. Unknown addresses
        // are ignored; the API still returns NEUTRAL_RESET_MESSAGE (no enumeration).
        repository.findUserByEmail(normalizeEmail(email))
                .filter(UserAccount::isActive)
                .ifPresent(user -> {
                    String token = randomToken();
                    repository.setPasswordReset(
                            user.id(),
                            sha256(token),
                            clock.instant().plus(1, ChronoUnit.HOURS));
                    events.passwordResetRequested(user.id(), user.email(), user.firstName(), token);
                });
    }

    @Transactional
    public void resetPassword(String token, String password) {
        UserAccount user = repository.findByValidResetToken(sha256(token), clock.instant())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "This reset link is invalid or has expired. Please request a new one."));
        repository.resetPassword(user.id(), passwordEncoder.encode(password));
        repository.revokeRefreshSessions(user.id(), clock.instant());
        events.passwordResetRecorded(user.id(), user.email(), user.displayName(), user.role());
    }

    @Transactional
    public void logout(long userId) {
        repository.incrementTokenVersion(userId);
        repository.revokeRefreshSessions(userId, clock.instant());
    }

    @Transactional
    public String refreshTokenFor(long userId) {
        return issueRefreshToken(requireUser(userId));
    }

    private String issueRefreshToken(UserAccount user) {
        long sessionId = repository.createRefreshSession(
                user.id(), user.tokenVersion(), tokens.refreshTokenExpiresAt());
        String token = tokens.refreshToken(tokenDetails(user), sessionId);
        repository.setRefreshSessionHash(sessionId, sha256(token));
        return token;
    }

    private UserAccount requireUser(long id) {
        return repository.findUserById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private void ensureActive(UserAccount user) {
        if (!user.isActive()) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "This account has been deactivated. Contact support.");
        }
    }

    private AuthUser response(UserAccount user, String accessToken) {
        return new AuthUser(
                user.id(), user.email(), user.firstName(), user.lastName(),
                user.accountType(), user.effectiveType(), user.adminRole(),
                user.employeeNumber(), user.emailVerified(), accessToken);
    }

    private UserTokenDetails tokenDetails(UserAccount user) {
        return new UserTokenDetails(
                user.id(), user.email(), user.firstName(), user.lastName(),
                user.accountType(), user.adminRole(), user.employeeNumber(),
                user.effectiveType(), user.tokenVersion());
    }

    private long requiredLongClaim(Jwt jwt, String name) {
        Number claim = jwt.getClaim(name);
        if (claim == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Unauthorised");
        }
        return claim.longValue();
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String trimToNull(String value) {
        return value == null ? null : value.trim();
    }

    public record RegisterCommand(
            String email,
            String password,
            String firstName,
            String lastName,
            String accountType,
            String employeeNumber
    ) {
    }

    public record AuthUser(
            long id,
            String email,
            String firstName,
            String lastName,
            String accountType,
            String effectiveType,
            String adminRole,
            String employeeNumber,
            boolean emailVerified,
            String token
    ) {
    }

    public record RefreshResult(String accessToken, String refreshToken) {
    }

    public record MeResponse(
            long id,
            String email,
            String firstName,
            String lastName,
            String accountType,
            String effectiveType,
            String adminRole
    ) {
    }

    public record ProfileResponse(
            long id,
            String email,
            String firstName,
            String lastName,
            String accountType
    ) {
    }

    public interface IdentityEventPublisher {
        void emailVerificationRequested(long userId, String email, String firstName, String token);
        void welcomeRequested(long userId, String email, String firstName, String lastName);
        void passwordResetRequested(long userId, String email, String firstName, String token);
        void loginRecorded(long userId, String email, String actor, String role);
        void passwordResetRecorded(long userId, String email, String actor, String role);
    }
}
