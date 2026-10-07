package kr.ac.knue.common.api;

import java.util.Map;
import kr.ac.knue.common.adapter.mybatis.ManagementMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Exposes public database/migration readiness without business data or authorization details. */
@RestController
public class HealthController {
    private final ManagementMapper mapper;

    /** Binds the read-only migration readiness probe. */
    public HealthController(ManagementMapper mapper) {
        this.mapper = mapper;
    }

    /** Reports readiness only after the persistence probe succeeds; never writes business records. */
    @GetMapping("/api/health")
    public Map<String, Object> health() {
        if (mapper.health() != 1) {
            throw new ApiException(500, "Database health check failed", null);
        }
        return Responses.success(Map.of("healthy", true));
    }
}
