package es.gob.afirma.test.xmldsig;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.junit.Assert;
import org.junit.Test;
import org.xml.sax.SAXParseException;

import es.gob.afirma.core.misc.SecureXmlBuilder;
import es.gob.afirma.signers.xml.Utils;

/**
 * Regresi&oacute;n XXE: el parser usado por CustomUriDereferencer / Utils
 * debe rechazar DOCTYPE con entidad externa.
 */
public final class TestCustomUriDereferencerXXE {

	private static final String XXE_PAYLOAD = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
			+ "<!DOCTYPE root ["
			+ "  <!ENTITY xxe SYSTEM \"file:///etc/passwd\">"
			+ "]>"
			+ "<root>&xxe;</root>";

	@Test
	public void testSecureXmlBuilderRejectsExternalEntity() throws Exception {
		final SAXParseException exception = Assert.assertThrows(SAXParseException.class, () -> {
			SecureXmlBuilder.getSecureDocumentBuilder()
					.parse(new ByteArrayInputStream(XXE_PAYLOAD.getBytes(StandardCharsets.UTF_8)));
		});
		Assert.assertNotNull(exception.getMessage());
	}

	@Test
	public void testUtilsDocumentBuilderRejectsExternalEntity() throws Exception {
		final SAXParseException exception = Assert.assertThrows(SAXParseException.class, () -> {
			Utils.getNewDocumentBuilder()
					.parse(new ByteArrayInputStream(XXE_PAYLOAD.getBytes(StandardCharsets.UTF_8)));
		});
		Assert.assertNotNull(exception.getMessage());
	}
}
