package ug.go.caa.recruitment.shared.persistence;

import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class OutboxWriter {

    private final JdbcClient jdbc;
    private final ObjectMapper mapper;

    public OutboxWriter(JdbcClient jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public void write(
            String aggregateType,
            Object aggregateId,
            String eventType,
            Map<String, ?> payload
    ) {
        try {
            jdbc.sql("""
                    INSERT INTO outbox_events (
                        event_id, aggregate_type, aggregate_id, event_type, payload
                    ) VALUES (
                        :eventId, :aggregateType, :aggregateId, :eventType, CAST(:payload AS jsonb)
                    )
                    """)
                    .param("eventId", UUID.randomUUID())
                    .param("aggregateType", aggregateType)
                    .param("aggregateId", String.valueOf(aggregateId))
                    .param("eventType", eventType)
                    .param("payload", mapper.writeValueAsString(payload))
                    .update();
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Outbox payload cannot be serialized", exception);
        }
    }
}
