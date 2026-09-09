package ug.go.caa.recruitment.shared.persistence;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import ug.go.caa.recruitment.shared.security.AuthenticatedActor;

@Component
public class AuditWriter {

    private final JdbcClient jdbc;

    public AuditWriter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void write(AuthenticatedActor actor, String action, String target) {
        jdbc.sql("""
                INSERT INTO audit_log (actor_user_id, actor, role, action, target)
                VALUES (:actorId, :actor, :role, :action, :target)
                """)
                .param("actorId", actor.id())
                .param("actor", actor.displayName())
                .param("role", actor.adminRole() == null ? actor.accountType() : actor.adminRole())
                .param("action", action)
                .param("target", target)
                .update();
    }
}
