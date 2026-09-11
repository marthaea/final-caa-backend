package ug.go.caa.recruitment.feature.identity.infrastructure;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class StaffRepository {

    private final JdbcClient jdbc;

    public StaffRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<StaffMember> findAll(String search) {
        if (search == null || search.isBlank()) {
            return jdbc.sql("SELECT * FROM staff ORDER BY last_name ASC")
                    .query(StaffMember.class)
                    .list();
        }
        return jdbc.sql("""
                SELECT * FROM staff
                WHERE first_name ILIKE :search OR last_name ILIKE :search
                    OR email ILIKE :search OR dept ILIKE :search
                ORDER BY last_name ASC
                """)
                .param("search", "%" + search + "%")
                .query(StaffMember.class)
                .list();
    }

    public boolean exists(String employeeNumber) {
        return jdbc.sql("SELECT EXISTS(SELECT 1 FROM staff WHERE employee_number = :employeeNumber)")
                .param("employeeNumber", employeeNumber)
                .query(Boolean.class)
                .single();
    }

    public StaffMember create(CreateStaff member) {
        long id = jdbc.sql("""
                INSERT INTO staff (
                    employee_number, first_name, last_name, dept, position,
                    email, joined_date, status
                ) VALUES (
                    :employeeNumber, :firstName, :lastName, :dept, :position,
                    :email, :joinedDate, :status
                ) RETURNING id
                """)
                .params(Map.of(
                        "employeeNumber", member.employeeNumber(),
                        "firstName", member.firstName(),
                        "lastName", member.lastName(),
                        "status", member.status()))
                .param("dept", member.dept())
                .param("position", member.position())
                .param("email", member.email())
                .param("joinedDate", member.joinedDate())
                .query(Long.class)
                .single();
        return jdbc.sql("SELECT * FROM staff WHERE id = :id")
                .param("id", id)
                .query(StaffMember.class)
                .single();
    }

    public void recordCreation(
            long actorId,
            String actor,
            String role,
            String name,
            String employeeNumber
    ) {
        jdbc.sql("""
                INSERT INTO audit_log (actor_user_id, actor, role, action, target)
                VALUES (:actorId, :actor, :role, 'Added staff record', :target)
                """)
                .param("actorId", actorId)
                .param("actor", actor)
                .param("role", role)
                .param("target", name + " (" + employeeNumber + ")")
                .update();
    }

    public record StaffMember(
            long id,
            Long userId,
            String employeeNumber,
            String firstName,
            String lastName,
            String dept,
            String position,
            String email,
            LocalDate joinedDate,
            String status,
            java.time.Instant createdAt,
            java.time.Instant updatedAt
    ) {
    }

    public record CreateStaff(
            String employeeNumber,
            String firstName,
            String lastName,
            String dept,
            String position,
            String email,
            LocalDate joinedDate,
            String status
    ) {
    }
}
