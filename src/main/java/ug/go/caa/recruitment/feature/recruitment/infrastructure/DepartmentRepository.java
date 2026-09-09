package ug.go.caa.recruitment.feature.recruitment.infrastructure;

import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class DepartmentRepository {

    private final JdbcClient jdbc;

    public DepartmentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<Department> findAll() {
        return jdbc.sql("SELECT id, name, code, head_user_id FROM departments ORDER BY name ASC")
                .query(Department.class)
                .list();
    }

    public Optional<Department> findById(long id) {
        return jdbc.sql("SELECT id, name, code, head_user_id FROM departments WHERE id = :id")
                .param("id", id)
                .query(Department.class)
                .optional();
    }

    public boolean codeExists(String code) {
        return jdbc.sql("SELECT EXISTS(SELECT 1 FROM departments WHERE code = :code)")
                .param("code", code)
                .query(Boolean.class)
                .single();
    }

    public Department create(String name, String code) {
        long id = jdbc.sql("""
                INSERT INTO departments (name, code) VALUES (:name, :code) RETURNING id
                """)
                .param("name", name)
                .param("code", code)
                .query(Long.class)
                .single();
        return findById(id).orElseThrow();
    }

    public boolean isHod(long userId) {
        return jdbc.sql("""
                SELECT EXISTS(
                    SELECT 1 FROM users
                    WHERE id = :id AND account_type = 'admin' AND admin_role = 'hod'
                )
                """)
                .param("id", userId)
                .query(Boolean.class)
                .single();
    }

    public Department assignHead(long id, Long headUserId) {
        jdbc.sql("UPDATE departments SET head_user_id = :head, updated_at = now() WHERE id = :id")
                .param("head", headUserId)
                .param("id", id)
                .update();
        return findById(id).orElseThrow();
    }

    public record Department(long id, String name, String code, Long headUserId) {
    }
}
