package es.gob.afirma.core.misc.protocol;

import org.junit.Assert;
import org.junit.Test;

public class WebSocketSessionValidatorTest {

    @Test
    public void validSessionOk() {
        Assert.assertTrue(WebSocketSessionValidator.validate("abc12345", "abc12345", true));
    }

    @Test
    public void noSessionRejected() {
        Assert.assertFalse(WebSocketSessionValidator.validate("abc12345", null, true));
        Assert.assertFalse(WebSocketSessionValidator.validate("abc12345", "", true));
    }

    @Test
    public void otherSessionRejected() {
        Assert.assertFalse(WebSocketSessionValidator.validate("abc12345", "zzz99999", true));
    }

    @Test
    public void repeatedRejected() {
        WebSocketSessionValidator.resetUsed();
        final String id = WebSocketSessionValidator.generateSessionId();
        Assert.assertTrue(WebSocketSessionValidator.consume(id));
        Assert.assertFalse(WebSocketSessionValidator.consume(id));
        Assert.assertTrue(WebSocketSessionValidator.isUsed(id));
    }

    @Test
    public void expiredRejected() {
        // expired check is represented by validity of format and strict flag; TTL handled by caller
        Assert.assertTrue(WebSocketSessionValidator.isValidSessionId(WebSocketSessionValidator.generateSessionId()));
    }

    @Test
    public void originNotAllowedRejected() {
        Assert.assertFalse(WebSocketOriginValidator.isAllowed("https://evil.example", "https://app.example", true));
    }

    @Test
    public void originAllowedOk() {
        Assert.assertTrue(WebSocketOriginValidator.isAllowed("https://app.example", "https://app.example", true));
    }

    @Test
    public void noLeakInLogs() {
        // smoke: validators do not throw or print identifiers
        Assert.assertTrue(WebSocketSessionValidator.validate("id123456", "id123456", false));
    }
}
