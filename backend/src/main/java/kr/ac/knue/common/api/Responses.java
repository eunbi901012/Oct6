package kr.ac.knue.common.api;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kr.ac.knue.common.application.Resource;

/** Builds stable HTTP envelopes without mutating persistence rows or inventing business defaults. */
public final class Responses {
    private Responses() {
    }

    /** Creates request-local metadata. */
    public static Map<String, Object> meta() {
        return new LinkedHashMap<>();
    }

    /** Wraps data, projecting known composite DTOs while preserving arbitrary map content. */
    public static Map<String, Object> success(Object data) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("meta", meta());
        response.put("data", ResponseProjection.composite(data));
        return response;
    }

    /** Wraps an explicit entity schema, preserving required and nullable fields. */
    public static Map<String, Object> success(Object data, Resource resource) {
        return success(ResponseProjection.entity(resource, data));
    }

    /** Projects explicit entity rows before pagination, leaving mapper maps unchanged. */
    public static Map<String, Object> list(List<?> rows, Map<String, Object> query, Resource resource) {
        return list((List<?>) ResponseProjection.entity(resource, rows), query);
    }

    /** Returns explicit pages or the complete collection; no default or maximum size is invented. */
    public static Map<String, Object> list(List<?> rows, Map<String, Object> query) {
        Integer page = (Integer) query.get("page");
        Integer size = (Integer) query.get("size");
        if ((page == null) != (size == null)) {
            throw new ApiException(400, "Supply both page and size", "page");
        }
        Map<String, Object> response;
        if (page == null) {
            response = success(rows);
            Map<String, Object> meta = (Map<String, Object>) response.get("meta");
            meta.put("total", rows.size());
            meta.put("page", 0);
            // Describes the complete returned collection; does not impose a default limit.
            meta.put("size", Math.max(1, rows.size()));
        } else {
            long start = (long) page * size;
            int from = (int) Math.min(start, rows.size());
            int to = (int) Math.min(start + size, rows.size());
            response = success(rows.subList(from, to));
            Map<String, Object> meta = (Map<String, Object>) response.get("meta");
            meta.put("page", page);
            meta.put("size", size);
            meta.put("total", rows.size());
        }
        return response;
    }
}
