package kr.ac.knue.common.application;

import java.util.List;
import java.util.Map;
import kr.ac.knue.common.domain.CurrentPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owns local group edits without changing source organizations or inferred external mapping policy. */
@Service
public class CodeGroupsService {
    private final ManagementService management;

    /** Binds local group persistence. */
    public CodeGroupsService(ManagementService management) {
        this.management = management;
    }

    /** Reads stored groups without default page limits. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> list() {
        return management.list(Resource.GROUPS, Map.of());
    }

    /** Persists explicit group fields without altering code references. */
    @Transactional
    public Map<String, Object> create(Map<String, Object> values, CurrentPrincipal actor) {
        return management.create(Resource.GROUPS, values, actor);
    }

    /** Resolves an unambiguous business identifier while preserving its numeric key. */
    @Transactional
    public Map<String, Object> update(String groupId, Map<String, Object> values, CurrentPrincipal actor) {
        return management.update(Resource.GROUPS, management.groupKey(groupId), values, actor);
    }
}
