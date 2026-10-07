package kr.ac.knue.common.adapter.mybatis;

import java.util.List;
import java.util.Map;
import kr.ac.knue.common.application.Resource;
import org.apache.ibatis.annotations.Param;

/** Uses closed resource identifiers and trusted validated columns; HTTP input never supplies SQL identifiers. */
public interface ManagementMapper {
    /** Reads entity-safe projections through explicit equality filters, preserving SQL null keys. */
    List<Map<String, Object>> list(@Param("resource") Resource resource,
        @Param("filters") Map<String, Object> filters);

    /** Reads one numeric/business key without credentials or session columns in the projection. */
    Map<String, Object> get(@Param("resource") Resource resource, @Param("id") Object id);

    /** Inserts trusted editable fields and returns database-managed identity/timestamps. */
    Map<String, Object> insert(@Param("resource") Resource resource,
        @Param("values") Map<String, Object> values);

    /** Changes only explicit fields and server updated_at, returning the affected-row count. */
    int update(@Param("resource") Resource resource, @Param("id") Object id,
        @Param("values") Map<String, Object> values);

    /** Collects persisted account, role and organization subjects without choosing conflict precedence. */
    List<Map<String, Object>> applicablePermissions(@Param("accountId") Long accountId);

    /** Performs a read-only readiness check against completed database migrations. */
    int health();
}
