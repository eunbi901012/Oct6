package kr.ac.knue.common.adapter.personnel;

import java.util.List;
import java.util.Map;
import kr.ac.knue.common.adapter.mybatis.ManagementMapper;
import kr.ac.knue.common.application.Resource;
import kr.ac.knue.common.domain.ports.PersonnelInformationPort;
import org.springframework.stereotype.Component;

/** DB-only source snapshot adapter: no external connection and no source mutator. */
@Component
public class DatabasePersonnelAdapter implements PersonnelInformationPort {
    private final ManagementMapper mapper;

    /** Binds local source-snapshot reads only. */
    public DatabasePersonnelAdapter(ManagementMapper mapper) {
        this.mapper = mapper;
    }

    /** Reads personnel through trusted columns without external access or source writes. */
    @Override
    public List<Map<String, Object>> personnel(Map<String, Object> filters) {
        return mapper.list(Resource.PERSONNEL, filters);
    }

    /** Reads source positions associated with the supplied personnel identity. */
    @Override
    public List<Map<String, Object>> positions(Long personnelId) {
        return mapper.list(Resource.POSITIONS, Map.of("personnel_id", personnelId));
    }
}
