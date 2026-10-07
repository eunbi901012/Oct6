package kr.ac.knue.common.api;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Validates complete batches before writes, retaining indexed field errors. */
public final class BatchInput {
    private BatchInput() {
    }

    /** Validates every item against the operation's exact editable boundary. */
    public static List<Map<String, Object>> rows(JsonNode array, String field, String specification, boolean nonempty) {
        if (array == null || !array.isArray() || (nonempty && array.isEmpty())) {
            throw new ApiException(400, nonempty ? "Expected nonempty array" : "Expected array", field);
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int index = 0; index < array.size(); index++) {
            try {
                rows.add(Input.object(array.get(index), specification));
            } catch (ApiException exception) {
                throw new ApiException(exception.status(), exception.getMessage(),
                    field + "[" + index + "]." + exception.field());
            }
        }
        return rows;
    }
}
