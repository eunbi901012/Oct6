package kr.ac.knue.common.api;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Presence-preserving JSON DTO boundary: no Jackson scalar coercion or guessed business defaults. */
public final class Input {
    public static final Set<String> ROLE_CODES = Set.of("R01", "R02", "R03", "R04", "R05",
        "R06", "R07", "R08", "R09");

    private Input() {
    }

    /** Validates declared editable fields, exact JSON types and explicitly required presence. */
    public static Map<String, Object> object(JsonNode body, String specification, String... required) {
        if (body == null || !body.isObject()) {
            throw invalid("request", "Expected JSON object");
        }
        Map<String, String> types = new LinkedHashMap<>();
        for (String token : specification.split(" ")) {
            if (!token.isEmpty()) {
                String[] parts = token.split(":");
                types.put(parts[0], parts[1]);
            }
        }
        Map<String, Object> values = new LinkedHashMap<>();
        body.fields().forEachRemaining(field -> {
            String name = field.getKey();
            String type = types.get(name);
            JsonNode node = field.getValue();
            if (type == null) {
                throw invalid(name, "Field is not editable in this operation");
            }
            if (node.isNull()) {
                throw invalid(name, "Null is not permitted by this input contract");
            }
            Object value;
            switch (type) {
                case "string", "role", "date" -> {
                    if (!node.isTextual()) {
                        throw invalid(name, "Expected string");
                    }
                    value = node.textValue();
                    if (type.equals("role") && !ROLE_CODES.contains(value)) {
                        throw invalid(name, "Expected R01 through R09");
                    }
                    if (type.equals("date")) {
                        try {
                            value = LocalDate.parse(node.textValue());
                        } catch (RuntimeException ex) {
                            throw invalid(name, "Expected ISO date");
                        }
                    }
                }
                case "long", "int" -> {
                    if (!node.isIntegralNumber() || !node.canConvertToLong()
                            || (type.equals("int") && !node.canConvertToInt())) {
                        throw invalid(name, "Expected integer");
                    }
                    if (type.equals("int")) {
                        value = node.intValue();
                    } else {
                        value = node.longValue();
                    }
                }
                case "boolean" -> {
                    if (!node.isBoolean()) {
                        throw invalid(name, "Expected boolean");
                    }
                    value = node.booleanValue();
                }
                case "object" -> {
                    if (!node.isObject()) {
                        throw invalid(name, "Expected object");
                    }
                    value = node.toString();
                }
                case "array" -> {
                    if (!node.isArray()) {
                        throw invalid(name, "Expected array");
                    }
                    value = node;
                }
                default -> throw new IllegalStateException("Unsupported DTO descriptor");
            }
            values.put(name, value);
        });
        for (String name : required) {
            if (!values.containsKey(name)) {
                throw invalid(name, "Required field is missing");
            }
        }
        return values;
    }

    /** Validates declared query fields and explicit page coordinates without guessed defaults. */
    public static Map<String, Object> query(Map<String, String> raw, String specification) {
        Map<String, String> types = new LinkedHashMap<>();
        for (String token : (specification + " page:int size:int").split(" ")) {
            if (!token.isBlank()) {
                String[] parts = token.split(":");
                types.put(parts[0], parts[1]);
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        raw.forEach((key, text) -> {
            String type = types.get(key);
            if (type == null) {
                throw invalid(key, "Unknown query parameter");
            }
            try {
                Object value = text;
                if (type.equals("long")) {
                    value = Long.valueOf(text);
                } else if (type.equals("int")) {
                    value = Integer.valueOf(text);
                } else if (type.equals("role") && !ROLE_CODES.contains(text)) {
                    throw invalid(key, "Expected R01 through R09");
                }
                result.put(key, value);
            } catch (NumberFormatException exception) {
                throw invalid(key, "Expected integer");
            }
        });
        if (result.containsKey("page") && (Integer) result.get("page") < 0) {
            throw invalid("page", "Page must be nonnegative");
        }
        if (result.containsKey("size") && (Integer) result.get("size") < 1) {
            throw invalid("size", "Size must be positive");
        }
        return result;
    }

    private static ApiException invalid(String field, String message) {
        return new ApiException(400, message, field);
    }
}
