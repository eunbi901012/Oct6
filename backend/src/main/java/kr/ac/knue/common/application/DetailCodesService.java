package kr.ac.knue.common.application;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kr.ac.knue.common.domain.CurrentPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Persists local code fields and JSON mappings without external calls or guessed period effects. */
@Service
public class DetailCodesService {
    private final ManagementService management;

    /** Binds local code persistence. */
    public DetailCodesService(ManagementService management) {
        this.management = management;
    }

    /** Filters through an unambiguous group identifier when explicitly supplied. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(String groupId) {
        Map<String, Object> filters = new LinkedHashMap<>();
        if (groupId != null) {
            filters.put("code_group_id", management.groupKey(groupId));
        }
        return management.list(Resource.CODES, filters);
    }

    /** Persists explicit code fields and references. */
    @Transactional
    public Map<String, Object> create(Map<String, Object> values, CurrentPrincipal actor) {
        return management.create(Resource.CODES, values, actor);
    }

    /** Changes explicit fields while preserving identity and child references. */
    @Transactional
    public Map<String, Object> update(Long id, Map<String, Object> values, CurrentPrincipal actor) {
        return management.update(Resource.CODES, id, values, actor);
    }
}
