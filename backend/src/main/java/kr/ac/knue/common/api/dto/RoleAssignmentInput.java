package kr.ac.knue.common.api.dto;

import java.time.LocalDate;
import java.util.Map;

/** Exact RoleAssignmentInput shape. Approval is explicit, never copied from the actor. */
public record RoleAssignmentInput(String role_code, Long approver_id, LocalDate valid_start,
        LocalDate valid_end, String assignment_source) {
    /** Constructs an explicit assignment from validated values without actor-derived approval defaults. */
    public static RoleAssignmentInput from(Map<String, Object> values) {
        return new RoleAssignmentInput((String) values.get("role_code"), (Long) values.get("approver_id"),
            (LocalDate) values.get("valid_start"), (LocalDate) values.get("valid_end"),
            (String) values.get("assignment_source"));
    }
}
