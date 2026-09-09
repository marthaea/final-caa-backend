package ug.go.caa.recruitment.feature.identity.infrastructure;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class IdentityRepository {

    private final JdbcClient jdbc;

    public IdentityRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<UserAccount> findUserByEmail(String email) {
        return jdbc.sql("SELECT * FROM users WHERE lower(email) = lower(:email)")
                .param("email", email)
                .query(UserAccount.class)
                .optional();
    }

    public Optional<UserAccount> findUserById(long id) {
        return jdbc.sql("SELECT * FROM users WHERE id = :id")
                .param("id", id)
                .query(UserAccount.class)
                .optional();
    }

    public boolean staffExists(String employeeNumber) {
        return jdbc.sql("SELECT EXISTS(SELECT 1 FROM staff WHERE employee_number = :employeeNumber)")
                .param("employeeNumber", employeeNumber)
                .query(Boolean.class)
                .single();
    }

    public long createUser(
            String email,
            String passwordHash,
            String firstName,
            String lastName,
            String accountType,
            String employeeNumber,
            String verificationToken
    ) {
        return jdbc.sql("""
                INSERT INTO users (
                    email, password_hash, first_name, last_name, account_type,
                    employee_number, effective_type, email_verified, verify_token
                ) VALUES (
                    lower(:email), :passwordHash, :firstName, :lastName, :accountType,
                    :employeeNumber, :accountType, false, :verificationToken
                )
                RETURNING id
                """)
                .params(Map.of(
                        "email", email,
                        "passwordHash", passwordHash,
                        "firstName", firstName,
                        "lastName", lastName,
                        "accountType", accountType,
                        "verificationToken", verificationToken))
                .param("employeeNumber", employeeNumber)
                .query(Long.class)
                .single();
    }

    public Optional<UserAccount> findByVerificationToken(String token) {
        return jdbc.sql("SELECT * FROM users WHERE verify_token = :token")
                .param("token", token)
                .query(UserAccount.class)
                .optional();
    }

    public void markEmailVerified(long userId) {
        jdbc.sql("UPDATE users SET email_verified = true, verify_token = NULL, updated_at = now() WHERE id = :id")
                .param("id", userId)
                .update();
    }

    public void setVerificationToken(long userId, String token) {
        jdbc.sql("UPDATE users SET verify_token = :token, updated_at = now() WHERE id = :id")
                .param("token", token)
                .param("id", userId)
                .update();
    }

    public void updateProfile(long userId, String firstName, String lastName, String email) {
        jdbc.sql("""
                UPDATE users SET
                    first_name = COALESCE(:firstName, first_name),
                    last_name = COALESCE(:lastName, last_name),
                    email = COALESCE(lower(:email), email),
                    updated_at = now()
                WHERE id = :id
                """)
                .param("firstName", firstName)
                .param("lastName", lastName)
                .param("email", email)
                .param("id", userId)
                .update();
    }

    public void setPasswordReset(long userId, String tokenHash, Instant expiresAt) {
        jdbc.sql("""
                UPDATE users SET reset_token_hash = :tokenHash,
                    reset_token_expires = :expiresAt, updated_at = now()
                WHERE id = :id
                """)
                .param("tokenHash", tokenHash)
                .param("expiresAt", expiresAt.atOffset(ZoneOffset.UTC))
                .param("id", userId)
                .update();
    }

    public Optional<UserAccount> findByValidResetToken(String tokenHash, Instant now) {
        return jdbc.sql("""
                SELECT * FROM users
                WHERE reset_token_hash = :tokenHash AND reset_token_expires > :now
                """)
                .param("tokenHash", tokenHash)
                .param("now", now.atOffset(ZoneOffset.UTC))
                .query(UserAccount.class)
                .optional();
    }

    public void resetPassword(long userId, String passwordHash) {
        jdbc.sql("""
                UPDATE users SET password_hash = :passwordHash,
                    reset_token_hash = NULL, reset_token_expires = NULL,
                    token_version = token_version + 1, updated_at = now()
                WHERE id = :id
                """)
                .param("passwordHash", passwordHash)
                .param("id", userId)
                .update();
    }

    public void incrementTokenVersion(long userId) {
        jdbc.sql("UPDATE users SET token_version = token_version + 1, updated_at = now() WHERE id = :id")
                .param("id", userId)
                .update();
    }

    public long createRefreshSession(long userId, int tokenVersion, Instant expiresAt) {
        return jdbc.sql("""
                INSERT INTO refresh_sessions (user_id, token_hash, token_version, expires_at)
                VALUES (:userId, :pendingHash, :tokenVersion, :expiresAt)
                RETURNING id
                """)
                .param("userId", userId)
                .param("pendingHash", "pending-" + UUID.randomUUID())
                .param("tokenVersion", tokenVersion)
                .param("expiresAt", expiresAt.atOffset(ZoneOffset.UTC))
                .query(Long.class)
                .single();
    }

    public void setRefreshSessionHash(long sessionId, String tokenHash) {
        jdbc.sql("UPDATE refresh_sessions SET token_hash = :tokenHash WHERE id = :id")
                .param("tokenHash", tokenHash)
                .param("id", sessionId)
                .update();
    }

    public Optional<RefreshSession> lockActiveRefreshSession(long sessionId, Instant now) {
        return jdbc.sql("""
                SELECT id, user_id, token_hash, token_version, expires_at
                FROM refresh_sessions
                WHERE id = :id AND revoked_at IS NULL AND expires_at > :now
                FOR UPDATE
                """)
                .param("id", sessionId)
                .param("now", now.atOffset(ZoneOffset.UTC))
                .query(RefreshSession.class)
                .optional();
    }

    public void rotateRefreshSession(long oldSessionId, long newSessionId, Instant now) {
        jdbc.sql("""
                UPDATE refresh_sessions
                SET revoked_at = :now, last_used_at = :now, replaced_by_session_id = :replacement
                WHERE id = :id
                """)
                .param("now", now.atOffset(ZoneOffset.UTC))
                .param("replacement", newSessionId)
                .param("id", oldSessionId)
                .update();
    }

    public void revokeRefreshSessions(long userId, Instant now) {
        jdbc.sql("""
                UPDATE refresh_sessions SET revoked_at = :now
                WHERE user_id = :userId AND revoked_at IS NULL
                """)
                .param("now", now.atOffset(ZoneOffset.UTC))
                .param("userId", userId)
                .update();
    }

    public record UserAccount(
            long id,
            String email,
            String passwordHash,
            String firstName,
            String lastName,
            String accountType,
            String adminRole,
            String employeeNumber,
            String effectiveType,
            boolean emailVerified,
            String verifyToken,
            boolean isActive,
            int tokenVersion,
            String resetTokenHash,
            Instant resetTokenExpires,
            Instant createdAt,
            Instant updatedAt
    ) {
        public String displayName() {
            return firstName + " " + lastName;
        }

        public String role() {
            return adminRole == null ? accountType : adminRole;
        }
    }

    public record RefreshSession(
            long id,
            long userId,
            String tokenHash,
            int tokenVersion,
            Instant expiresAt
    ) {
    }
}
