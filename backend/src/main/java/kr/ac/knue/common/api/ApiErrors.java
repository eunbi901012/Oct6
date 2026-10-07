package kr.ac.knue.common.api;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Translates failures to stable envelopes without leaking credentials, SQL or driver diagnostics. */
@RestControllerAdvice
public class ApiErrors {
    /** Preserves application-selected status and safe field information. */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> api(ApiException exception) {
        return error(exception.status(), exception.getMessage(), exception.field());
    }

    /** Rejects malformed JSON or unsupported request content without parser details. */
    @ExceptionHandler({HttpMessageNotReadableException.class, HttpMediaTypeNotSupportedException.class})
    public ResponseEntity<Map<String, Object>> invalid(Exception exception) {
        return error(400, "Invalid request field or JSON type", "request");
    }

    /** Reports route type mismatches against the actual path field. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> invalidPath(MethodArgumentTypeMismatchException exception) {
        return error(400, "Invalid path parameter type", exception.getName());
    }

    /** Converts failed references or identifiers without exposing database constraints. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> integrity(DataIntegrityViolationException exception) {
        return error(400, "Conflicting identifier or invalid reference", "reference");
    }

    /** Hides unexpected implementation details behind a server-failure envelope. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> unexpected(Exception exception) {
        return error(500, "Request could not be completed", null);
    }

    /** Creates a fresh envelope with mandatory metadata and optional safe field errors. */
    public static ResponseEntity<Map<String, Object>> error(int status, String message, String field) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", false);
        response.put("meta", Responses.meta());
        response.put("message", message);
        response.put("fieldErrors", field == null ? List.of() : List.of(Map.of("field", field, "message", message)));
        return ResponseEntity.status(status).body(response);
    }
}
