/* Copyright (C) 2026 [Soluciones Alexendros]
 * Utilidades de validacion de la sesion del websocket local (T8).
 */
package es.gob.afirma.core.misc.protocol;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;

/** Validador de identificadores de sesion del canal local (idsession). */
public final class WebSocketSessionValidator {

    private static final int MIN_LENGTH = 8;
    private static final int MAX_LENGTH = 64;

    private static final Set<String> USED_SESSIONS = ConcurrentHashMap.newKeySet();

    private WebSocketSessionValidator() { }

    /** Genera un identificador de sesion aleatorio con SecureRandom. */
    public static String generateSessionId() {
        final byte[] bytes = new byte[16];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Comprueba que el identificador tenga formato valido. */
    public static boolean isValidSessionId(final String id) {
        if (id == null || id.isEmpty()) {
            return false;
        }
        if (id.length() < MIN_LENGTH || id.length() > MAX_LENGTH) {
            return false;
        }
        for (int i = 0; i < id.length(); i++) {
            final char c = id.charAt(i);
            if (!(Character.isLetterOrDigit(c) || c == '-' || c == '_')) {
                return false;
            }
        }
        return true;
    }

    /** Valida que la sesion recibida coincida con la esperada. */
    public static boolean validate(final String expected, final String received, final boolean strict) {
        if (!strict) {
            return true;
        }
        if (expected == null || expected.isEmpty()) {
            return false;
        }
        if (received == null || received.isEmpty()) {
            return false;
        }
        if (!expected.equals(received)) {
            return false;
        }
        if (!isValidSessionId(received)) {
            return false;
        }
        return true;
    }

    /** Marca un identificador como usado (un solo uso). Devuelve false si ya estaba usado. */
    public static boolean consume(final String id) {
        if (!isValidSessionId(id)) {
            return false;
        }
        return USED_SESSIONS.add(id);
    }

    /** Indica si un identificador ya fue usado. */
    public static boolean isUsed(final String id) {
        return id != null && USED_SESSIONS.contains(id);
    }

    /** Reinicia el registro de sesiones usadas (solo para pruebas). */
    public static void resetUsed() {
        USED_SESSIONS.clear();
    }
}
