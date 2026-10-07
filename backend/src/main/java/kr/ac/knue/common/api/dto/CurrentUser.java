package kr.ac.knue.common.api.dto;

import java.util.List;
import java.util.Map;

/** CurrentUser wire shape, with the approved navigation-only additional-properties extension. */
public record CurrentUser(Long accountId, String loginId, List<String> roles,
        List<Map<String, Object>> allowedMenus) {
}
