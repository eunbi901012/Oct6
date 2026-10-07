package kr.ac.knue.common.application;

import java.util.List;
import java.util.Map;
import kr.ac.knue.common.api.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reads source organizations; relationship writes/history remain blocked by OQ-001. */
@Service
public class OrganizationsService {
    private final ManagementService management;

    /** Binds read-only organization persistence. */
    public OrganizationsService(ManagementService management) {
        this.management = management;
    }

    /** Applies supplied identifiers without changing hierarchy or periods. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(String code, Long id) {
        Map<String, Object> filters = new java.util.LinkedHashMap<>();
        if (code != null) {
            filters.put("organization_code", code);
        }
        if (id != null) {
            filters.put("organization_id", id);
        }
        return management.list(Resource.ORGANIZATIONS, filters);
    }

    /** Rejects registration before any source or history write until ownership is approved. */
    public void create(Map<String, Object> values) {
        throw ApiException.pending("OQ-001: organization source/local write boundary");
    }

    /** Rejects relationship changes while source/local ownership remains unresolved. */
    public void update(Long id, Map<String, Object> values) {
        throw ApiException.pending("OQ-001: organization source/local write boundary");
    }
}
