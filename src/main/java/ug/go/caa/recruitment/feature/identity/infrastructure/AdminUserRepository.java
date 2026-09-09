package ug.go.caa.recruitment.feature.identity.infrastructure;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class AdminUserRepository {

    private final JdbcClient jdbc;

    public AdminUserRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<AdminUser> findAll() {
        return jdbc.sql("""
                SELECT id, email, first_name, last_name, admin_role, is_active
                FROM users WHERE account_type = 'admin'
                ORDER BY first_name, last_name
                """)
                .query(AdminUser.class)
                .list();
    }

    public boolean emailExists(String email) {
        return jdbc.sql("SELECT EXISTS(SELECT 1 FROM users WHERE lower(email) = lower(:email))")
                .param("email", email)
                .query(Boolean.class)
                .single();
    }

    public AdminUser create(
            String email,
            String passwordHash,
            String firstName,
            String lastName,
            String adminRole
    ) {
        long id = jdbc.sql("""
                INSERT INTO users (
                    email, password_hash, first_name, last_name, account_type,
                    admin_role, effective_type, email_verified
                ) VALUES (
                    lower(:email), :passwordHash, :firstName, :lastName, 'admin',
                    :adminRole, 'admin', true
                ) RETURNING id
                """)
                .params(Map.of(
                        "email", email,
                        "passwordHash", passwordHash,
                        "firstName", firstName,
                        "lastName", lastName,
                        "adminRole", adminRole))
                .query(Long.class)
                .single();
        return findById(id).orElseThrow();
    }

    public Optional<AdminUser> findById(long id) {
        return jdbc.sql("""
                SELECT id, email, first_name, last_name, admin_role, is_active
                FROM users WHERE id = :id
                """)
                .param("id", id)
                .query(AdminUser.class)
                .optional();
    }

    public void recordCreation(
            long actorId,
            String actor,
            String actorRole,
            String firstName,
            String lastName,
            String email,
            String adminRole
    ) {
        jdbc.sql("""
                INSERT INTO audit_log (actor_user_id, actor, role, action, target)
                VALUES (:actorId, :actor, :actorRole, 'Created admin account', :target)
                """)
                .param("actorId", actorId)
                .param("actor", actor)
                .param("actorRole", actorRole)
                .param("target", firstName + " " + lastName + " (" + email + ", " + adminRole + ")")
                .update();
    }

    public record AdminUser(
            long id,
            String email,
            String firstName,
            String lastName,
            String adminRole,
            boolean isActive
    ) {
    }
}
