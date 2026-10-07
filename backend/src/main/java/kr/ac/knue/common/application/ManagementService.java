package kr.ac.knue.common.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kr.ac.knue.common.adapter.mybatis.ManagementMapper;
import kr.ac.knue.common.api.ApiException;
import kr.ac.knue.common.api.Input;
import kr.ac.knue.common.domain.ChangeTrace;
import kr.ac.knue.common.domain.CurrentPrincipal;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owns blocking local persistence and secret-free change context; source writes remain approval-gated. */
@Service
public class ManagementService {
    private final ManagementMapper mapper;
    private final ApplicationEventPublisher events;
    private final ObjectMapper json;

    /** Binds persistence, conceptual change events and stored JSON normalization. */
    public ManagementService(ManagementMapper mapper, ApplicationEventPublisher events, ObjectMapper json) {
        this.mapper = mapper;
        this.events = events;
        this.json = json;
    }

    /** Reads trusted-column filters without changing mapper null-presence semantics. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(Resource resource, Map<String, Object> filters) {
        return mapper.list(resource, filters).stream().map(this::normalize).toList();
    }

    /** Resolves an existing target; absent identifiers are contract 400 field errors, never undeclared 404. */
    @Transactional(readOnly = true)
    public Map<String, Object> get(Resource resource, Object id) {
        if (resource == Resource.ROLES && !Input.ROLE_CODES.contains(String.valueOf(id))) {
            throw new ApiException(400, "Expected R01 through R09", "roleCode");
        }
        Map<String, Object> row = mapper.get(resource, id);
        if (row == null) {
            throw new ApiException(400, "Requested record does not exist", resource.getKey());
        }
        return normalize(row);
    }

    /** Inserts validated local fields and emits explicit-field change context without credentials. */
    @Transactional
    public Map<String, Object> create(Resource resource, Map<String, Object> values, CurrentPrincipal actor) {
        rejectSource(resource);
        var row = normalize(mapper.insert(resource, values));
        trace(resource, row.get(resource.getKey()), Map.of(), row, values, actor);
        return row;
    }

    /** Updates explicit fields atomically, preserving source ownership and server-managed metadata. */
    @Transactional
    public Map<String, Object> update(Resource resource, Object id, Map<String, Object> values,
            CurrentPrincipal actor) {
        rejectSource(resource);
        var before = get(resource, id);
        if (values.isEmpty()) {
            return before;
        }
        if (mapper.update(resource, id, values) != 1) {
            throw new ApiException(400, "Requested record does not exist", resource.getKey());
        }
        var after = get(resource, id);
        trace(resource, id, before, after, values, actor);
        return after;
    }

    /** Resolves one business group identifier; absence and ambiguity do not select an arbitrary row. */
    @Transactional(readOnly = true)
    public Long groupKey(String groupId) {
        var matches = list(Resource.GROUPS, Map.of("group_id", groupId));
        if (matches.isEmpty()) {
            throw new ApiException(400, "Requested code group does not exist", "groupId");
        }
        if (matches.size() != 1) {
            throw new ApiException(400, "Pending approval: OQ-DATA-003 ambiguous groupId", "groupId");
        }
        return ((Number) matches.get(0).get("code_group_id")).longValue();
    }

    private Map<String, Object> normalize(Map<String, Object> source) {
        Map<String, Object> row = new LinkedHashMap<>(source);
        Object attributes = row.get("additional_attributes");
        if (attributes instanceof String text) {
            try {
                row.put("additional_attributes", json.readTree(text));
            } catch (JsonProcessingException exception) {
                throw new IllegalStateException("Invalid stored JSON", exception);
            }
        }
        row.remove("password");
        row.remove("password_hash");
        row.remove("session_id");
        return row;
    }

    private void trace(Resource resource, Object id, Map<String, Object> before, Map<String, Object> after,
            Map<String, Object> changed, CurrentPrincipal actor) {
        Map<String, Object> beforeValues = new LinkedHashMap<>();
        Map<String, Object> afterValues = new LinkedHashMap<>();
        changed.keySet().stream()
            .filter(key -> !key.equals("password") && !key.equals("password_hash") && !key.equals("session_id"))
            .forEach(key -> {
                beforeValues.put(key, before.get(key));
                afterValues.put(key, after.get(key));
            });
        events.publishEvent(new ChangeTrace(resource.getTable(), id, beforeValues, afterValues,
            actor.accountId(), OffsetDateTime.now(), null));
    }

    private void rejectSource(Resource resource) {
        if (resource == Resource.ORGANIZATIONS || resource == Resource.POSITIONS || resource == Resource.PERSONNEL) {
            throw ApiException.pending("OQ-001: source write boundary");
        }
    }
}
