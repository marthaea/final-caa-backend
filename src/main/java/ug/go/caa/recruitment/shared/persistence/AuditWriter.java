package ug.go.caa.recruitment.shared.persistence;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import ug.go.caa.recruitment.shared.security.AuthenticatedActor;

@Component
public class AuditWriter {

    private final JdbcClient jdbc;

    public AuditWriter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void write(AuthenticatedActor actor, String action, String target) {
        write(actor, action, target, null);
    }

    /** Metadata (e.g. a threshold value, a criteria snapshot, per-candidate
     *  reasons for a shortlisting run) lands in audit_log's jsonb column so
     *  accountability reports can be built from tamper-evident server data
     *  instead of reconstructing "why" from just an action/target string. */
    public void write(AuthenticatedActor actor, String action, String target, JsonNode metadata) {
        jdbc.sql("""
                INSERT INTO audit_log (actor_user_id, actor, role, action, target, metadata)
                VALUES (:actorId, :actor, :role, :action, :target, CAST(:metadata AS jsonb))
                """)
                .param("actorId", actor.id())
                .param("actor", actor.displayName())
                .param("role", actor.adminRole() == null ? actor.accountType() : actor.adminRole())
                .param("action", action)
                .param("target", target)
                .param("metadata", metadata == null ? "{}" : metadata.toString())
                .update();
    }
}
