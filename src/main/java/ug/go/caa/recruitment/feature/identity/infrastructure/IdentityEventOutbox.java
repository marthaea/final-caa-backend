package ug.go.caa.recruitment.feature.identity.infrastructure;

import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import ug.go.caa.recruitment.feature.identity.application.IdentityService.IdentityEventPublisher;

@Component
public class IdentityEventOutbox implements IdentityEventPublisher {

    private final JdbcClient jdbc;

    public IdentityEventOutbox(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void emailVerificationRequested(long userId, String email, String firstName, String token) {
        enqueue(
                userId,
                "identity.email-verification-requested",
                "jsonb_build_object('userId', :userId, 'email', :email, "
                        + "'firstName', :firstName, 'token', :token)",
                email, firstName, null, token);
    }

    @Override
    public void welcomeRequested(long userId, String email, String firstName, String lastName) {
        enqueue(
                userId,
                "identity.welcome-requested",
                "jsonb_build_object('userId', :userId, 'email', :email, "
                        + "'firstName', :firstName, 'lastName', :lastName)",
                email, firstName, lastName, null);
    }

    @Override
    public void passwordResetRequested(long userId, String email, String firstName, String token) {
        enqueue(
                userId,
                "identity.password-reset-requested",
                "jsonb_build_object('userId', :userId, 'email', :email, "
                        + "'firstName', :firstName, 'token', :token)",
                email, firstName, null, token);
    }

    @Override
    public void loginRecorded(long userId, String email, String actor, String role) {
        audit(userId, actor, role, "USER_LOGGED_IN", email);
    }

    @Override
    public void passwordResetRecorded(long userId, String email, String actor, String role) {
        audit(userId, actor, role, "PASSWORD_RESET", email);
    }

    private void enqueue(
            long userId,
            String eventType,
            String payloadExpression,
            String email,
            String firstName,
            String lastName,
            String token
    ) {
        jdbc.sql("""
                INSERT INTO outbox_events (
                    event_id, aggregate_type, aggregate_id, event_type, payload
                ) VALUES (
                    :eventId, 'user', :aggregateId, :eventType, %s
                )
                """.formatted(payloadExpression))
                .param("eventId", UUID.randomUUID())
                .param("aggregateId", Long.toString(userId))
                .param("eventType", eventType)
                .param("userId", userId)
                .param("email", email)
                .param("firstName", firstName)
                .param("lastName", lastName)
                .param("token", token)
                .update();
    }

    private void audit(long userId, String actor, String role, String action, String target) {
        jdbc.sql("""
                INSERT INTO audit_log (actor_user_id, actor, role, action, target)
                VALUES (:userId, :actor, :role, :action, :target)
                """)
                .param("userId", userId)
                .param("actor", actor)
                .param("role", role)
                .param("action", action)
                .param("target", target)
                .update();
    }
}
