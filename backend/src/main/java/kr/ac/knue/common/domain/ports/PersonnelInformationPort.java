package kr.ac.knue.common.domain.ports;

import java.util.List;
import java.util.Map;

/** Read-only source boundary; local management must not mutate KORUS personnel or positions. */
public interface PersonnelInformationPort {
    /** Returns source personnel for explicitly supplied trusted filters. */
    List<Map<String, Object>> personnel(Map<String, Object> filters);

    /** Returns source position mappings for one personnel identity without local writes. */
    List<Map<String, Object>> positions(Long personnelId);
}
