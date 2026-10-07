package kr.ac.knue.common.api;

/** Carries contract status and safe field errors, never internal credentials or database diagnostics. */
public class ApiException extends RuntimeException {
    private final int status;
    private final String field;

    /** Creates an application failure with an optional operation field. */
    public ApiException(int status, String message, String field) {
        super(message);
        this.status = status;
        this.field = field;
    }

    /** Returns the chosen contract HTTP status. */
    public int status() {
        return status;
    }

    /** Identifies the failing input boundary, if known. */
    public String field() {
        return field;
    }

    /** Blocks unresolved business policy instead of guessing a storage or authorization rule. */
    public static ApiException pending(String question) {
        return new ApiException(400, "Pending approval: " + question, "policy");
    }
}
