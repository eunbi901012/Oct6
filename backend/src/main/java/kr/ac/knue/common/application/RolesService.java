package kr.ac.knue.common.application;

import java.util.List;
import java.util.Map;
import kr.ac.knue.common.domain.CurrentPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Stores R01 through R09 definitions; descriptive scope is not an inferred authorization rule. */
@Service
public class RolesService {
    private final ManagementService management;

    /** Binds local role persistence. */
    public RolesService(ManagementService management) {
        this.management = management;
    }

    /** Reads role definitions without changing assignments. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> list() {
        return management.list(Resource.ROLES, Map.of());
    }

    /** Creates a validated definition without automatic grants. */
    @Transactional
    public Map<String, Object> create(Map<String, Object> values, CurrentPrincipal actor) {
        return management.create(Resource.ROLES, values, actor);
    }

    /** Updates editable information while preserving role code and assignment references. */
    @Transactional
    public Map<String, Object> update(String code, Map<String, Object> values, CurrentPrincipal actor) {
        return management.update(Resource.ROLES, code, values, actor);
    }
}
