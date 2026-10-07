package kr.ac.knue.common.application;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import kr.ac.knue.common.api.ApiException;
import kr.ac.knue.common.domain.CurrentPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Persists explicit role/approver/period records; no automatic grants, expiry or revocation states are inferred. */
@Service
public class UserRolesService {
    private final ManagementService management;

    /** Binds the local persistence transaction boundary. */
    public UserRolesService(ManagementService management) {
        this.management = management;
    }

    /** Reads stored assignments without guessing temporal or status effects. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(Map<String, Object> filters) {
        return management.list(Resource.USER_ROLES, filters);
    }

    /** Grants all requested mappings atomically, preserving the explicitly supplied approver. */
    @Transactional
    public List<Map<String, Object>> grant(
            Long accountId, List<Map<String, Object>> assignments, CurrentPrincipal actor) {
        validateBatch(assignments);
        management.get(Resource.ACCOUNTS, accountId);
        List<Map<String, Object>> results = new ArrayList<>();
        for (var input : assignments) {
            Map<String, Object> values = new LinkedHashMap<>(input);
            explicitApproval(values);
            values.put("account_id", accountId);
            results.add(management.create(Resource.USER_ROLES, values, actor));
        }
        return results;
    }

    /** Changes the selected assignment without substituting the actor for its approver. */
    @Transactional
    public Map<String, Object> change(Long id, Map<String, Object> input, CurrentPrincipal actor) {
        explicitApproval(input);
        return management.update(Resource.USER_ROLES, id, input, actor);
    }

    /** Updates retained roles and additions; removals remain blocked until revocation storage is approved. */
    @Transactional
    public List<Map<String, Object>> replace(Long accountId, List<Map<String, Object>> assignments,
            CurrentPrincipal actor) {
        validateBatch(assignments);
        management.get(Resource.ACCOUNTS, accountId);
        var existing = list(Map.of("account_id", accountId));
        for (var row : existing) {
            long matching = assignments.stream()
                .filter(input -> Objects.equals(input.get("role_code"), row.get("role_code"))).count();
            if (matching != 1) {
                throw ApiException.pending("OQ-DATA-004: replacement requires unresolved role revocation");
            }
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (var input : assignments) {
            explicitApproval(input);
            var matching = existing.stream()
                .filter(row -> Objects.equals(row.get("role_code"), input.get("role_code"))).toList();
            if (matching.size() > 1) {
                throw ApiException.pending("OQ-DATA-001: ambiguous assignment mapping");
            }
            if (matching.isEmpty()) {
                result.addAll(grant(accountId, List.of(input), actor));
            } else {
                result.add(change(((Number) matching.get(0).get("assignment_id")).longValue(), input, actor));
            }
        }
        return result;
    }

    /** Rejects revocation before writes while OQ-DATA-004 state/preservation policy remains unresolved. */
    public void revoke(Long id, Map<String, Object> input) {
        explicitApproval(input);
        throw ApiException.pending("OQ-DATA-004: role revocation state/preservation policy");
    }

    private void explicitApproval(Map<String, Object> values) {
        var assignment = kr.ac.knue.common.api.dto.RoleAssignmentInput.from(values);
        if (assignment.approver_id() == null) {
            throw new ApiException(
                400, "Approval must be explicitly recorded; no automatic actor substitution", "approver_id");
        }
    }

    private void validateBatch(List<Map<String, Object>> assignments) {
        java.util.Set<Object> roles = new java.util.HashSet<>();
        for (var assignment : assignments) {
            explicitApproval(assignment);
            if (assignment.get("role_code") == null || !roles.add(assignment.get("role_code"))) {
                throw ApiException.pending("OQ-DATA-001: missing or repeated role has no approved assignment mapping");
            }
        }
    }
}
