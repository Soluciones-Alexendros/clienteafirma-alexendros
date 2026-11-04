package es.gob.afirma.core.misc.protocol;

/** Validador del encabezado Origin del websocket local (T8). */
public final class WebSocketOriginValidator {

    private WebSocketOriginValidator() { }

    /** Indica si un origen esta permitido. */
    public static boolean isAllowed(final String origin, final String allowedOrigins, final boolean strict) {
        if (origin == null || origin.trim().isEmpty()) {
            return !strict;
        }
        final String allowed = allowedOrigins == null ? "" : allowedOrigins.trim();
        if (allowed.isEmpty() || "*".equals(allowed)) {
            return true;
        }
        for (final String candidate : allowed.split(",")) {
            final String trimmed = candidate.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.equals(origin)) {
                return true;
            }
            if (trimmed.startsWith("*.") && origin.endsWith(trimmed.substring(1))) {
                return true;
            }
        }
        return !strict;
    }
}
