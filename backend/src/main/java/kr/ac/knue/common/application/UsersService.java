package kr.ac.knue.common.application;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import kr.ac.knue.common.domain.CurrentPrincipal;
import kr.ac.knue.common.domain.ports.PersonnelInformationPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Joins read-only personnel with locally editable accounts and roles; no source mutation is exposed. */
@Service
public class UsersService {
    private final PersonnelInformationPort personnel;
    private final ManagementService management;
    private final UserRolesService roles;

    /** Binds the replaceable personnel port and local transaction services. */
    public UsersService(PersonnelInformationPort personnel, ManagementService management, UserRolesService roles) {
        this.personnel = personnel;
        this.management = management;
        this.roles = roles;
    }

    /** Includes linked, source-only and unlinked local users while honoring explicit search filters. */
    @Transactional(readOnly = true)
    public List<kr.ac.knue.common.api.dto.UserSearchItem> search(Map<String, Object> query) {
        Map<String, Object> sourceFilters = new LinkedHashMap<>();
        Map.of("employeeNumber", "employee_number", "personName", "person_name", "organizationId", "organization_id",
            "jobGrade", "job_grade", "employmentStatus", "employment_status")
            .forEach((wire, column) -> {
                if (query.containsKey(wire)) {
                    sourceFilters.put(column, query.get(wire));
                }
            });
        List<Map<String, Object>> accounts = management.list(Resource.ACCOUNTS, Map.of());
        List<kr.ac.knue.common.api.dto.UserSearchItem> results = new ArrayList<>();
        Set<Object> represented = new HashSet<>();
        for (var snapshot : personnel.personnel(sourceFilters)) {
            Long personnelId = ((Number) snapshot.get("personnel_id")).longValue();
            var connected = accounts.stream()
                .filter(account -> Objects.equals(account.get("personnel_id"), personnelId)).toList();
            if (connected.isEmpty()) {
                add(results, null, snapshot, personnel.positions(personnelId), List.of(), query);
            } else {
                for (var account : connected) {
                    represented.add(account.get("account_id"));
                    var assignments = roles.list(Map.of("account_id", account.get("account_id")));
                    add(results, account, snapshot, personnel.positions(personnelId), assignments, query);
                }
            }
        }
        if (sourceFilters.isEmpty()) {
            for (var account : accounts) {
                if (account.get("personnel_id") == null && !represented.contains(account.get("account_id"))) {
                    add(results, account, null, List.of(),
                        roles.list(Map.of("account_id", account.get("account_id"))), query);
                }
            }
        }
        return results;
    }

    /** Changes only local account use status, without inventing authentication effects. */
    @Transactional
    public Map<String, Object> status(Long id, Map<String, Object> input, CurrentPrincipal actor) {
        return management.update(Resource.ACCOUNTS, id, input, actor);
    }

    /** Delegates atomic role editing with explicit approval and unresolved revocation gates. */
    @Transactional
    public List<Map<String, Object>> roles(Long id, List<Map<String, Object>> input, CurrentPrincipal actor) {
        return roles.replace(id, input, actor);
    }

    private void add(List<kr.ac.knue.common.api.dto.UserSearchItem> result,
            Map<String, Object> account, Map<String, Object> snapshot,
            List<Map<String, Object>> positions, List<Map<String, Object>> assignments, Map<String, Object> query) {
        if (query.containsKey("useStatus")
                && (account == null || !Objects.equals(query.get("useStatus"), account.get("use_status")))) {
            return;
        }
        if (query.containsKey("roleCode") && assignments.stream()
                .noneMatch(row -> Objects.equals(query.get("roleCode"), row.get("role_code")))) {
            return;
        }
        result.add(new kr.ac.knue.common.api.dto.UserSearchItem(account, snapshot, positions, assignments));
    }
}
