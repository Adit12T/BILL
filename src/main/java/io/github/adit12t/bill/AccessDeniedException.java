package io.github.adit12t.bill;

/** The signed-in user may not see or change what they asked for. */
public class AccessDeniedException extends RuntimeException {
    public AccessDeniedException(String message) {
        super(message);
    }
}
