package kr.ac.knue.common.api.dto;

import java.util.List;
import java.util.Map;

/** Combines optional account/personnel with source positions and local roles; no source fields become editable. */
public record UserSearchItem(Map<String, Object> account, Map<String, Object> personnel,
        List<Map<String, Object>> positions, List<Map<String, Object>> roles) {
}
