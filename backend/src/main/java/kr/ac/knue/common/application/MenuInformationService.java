package kr.ac.knue.common.application;

import java.util.List;
import java.util.Map;
import kr.ac.knue.common.domain.CurrentPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Edits execution information only, preserving hierarchy and permission references. */
@Service
public class MenuInformationService {
    private final ManagementService management;

    /** Binds local menu persistence. */
    public MenuInformationService(ManagementService management) {
        this.management = management;
    }

    /** Reads execution information without triggering navigation or authentication. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> list() {
        return management.list(Resource.MENUS, Map.of());
    }

    /** Stores explicit execution fields without creating screens or granting permissions. */
    @Transactional
    public Map<String, Object> create(Map<String, Object> values, CurrentPrincipal actor) {
        return management.create(Resource.MENUS, values, actor);
    }

    /** Updates selected execution fields while preserving numeric identity. */
    @Transactional
    public Map<String, Object> update(Long id, Map<String, Object> values, CurrentPrincipal actor) {
        return management.update(Resource.MENUS, id, values, actor);
    }
}
