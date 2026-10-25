package es.gob.afirma.core.misc;

import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.SAXException;

/**
 * Constructor de objetos para la carga de documentos XML.
 * Fail-closed: si no se pueden establecer las características de seguridad críticas,
 * se aborta la creación del parser.
 * Para atributos no soportados por la implementación, se loggea pero no falla.
 */
public class SecureXmlBuilder {

	private static DocumentBuilderFactory SECURE_BUILDER_FACTORY = null;

    private static SAXParserFactory SAX_FACTORY = null;

	/**
	 * Obtiene un generador de árboles DOM con el que crear o cargar un XML.
	 * Fail-closed para características de seguridad críticas.
	 * @return Generador de árboles DOM.
	 * @throws ParserConfigurationException Cuando ocurre un error durante la creación.
	 * @throws IllegalStateException Si no se pueden establecer las características de seguridad críticas.
	 */
	public static DocumentBuilder getSecureDocumentBuilder() throws ParserConfigurationException {
		if (SECURE_BUILDER_FACTORY == null) {
			SECURE_BUILDER_FACTORY = DocumentBuilderFactory.newInstance();
			
			// Establecer FEATURE_SECURE_PROCESSING - fail-closed
			try {
				SECURE_BUILDER_FACTORY.setFeature(SecureXmlConstants.FEATURE_SECURE_PROCESSING, Boolean.TRUE.booleanValue());
			}
			catch (final Exception e) {
				Logger.getLogger("es.gob.afirma").log(Level.SEVERE, "No se ha podido establecer el procesado seguro en la factoria XML: " + e); //$NON-NLS-1$ //$NON-NLS-2$
				throw new IllegalStateException("No se pudo establecer FEATURE_SECURE_PROCESSING en DocumentBuilderFactory", e);
			}

			// Los siguientes atributos deberían establecerse automáticamente la implementación de
			// la biblioteca al habilitar la característica anterior. Por si acaso, los establecemos
			// expresamente. No son críticos si fallan (algunas implementaciones no los soportan).
			final String[] securityProperties = new String[] {
					SecureXmlConstants.ACCESS_EXTERNAL_DTD,
					SecureXmlConstants.ACCESS_EXTERNAL_SCHEMA,
					SecureXmlConstants.ACCESS_EXTERNAL_STYLESHEET
			};
			for (final String securityProperty : securityProperties) {
				try {
					SECURE_BUILDER_FACTORY.setAttribute(securityProperty, ""); //$NON-NLS-1$
				}
				catch (final Exception e) {
					// No son críticos, solo loggeamos
					Logger.getLogger("es.gob.afirma").log(Level.FINE, "No se ha podido establecer una propiedad de seguridad '" + securityProperty + "' en la factoria XML"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				}
			}

			// Prohibimos la declaración DOCTYPE para prevenir ataques de expansión
			// de entidades internas (billion laughs / XML bomb)
			try {
				SECURE_BUILDER_FACTORY.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true); //$NON-NLS-1$
			}
			catch (final Exception e) {
				Logger.getLogger("es.gob.afirma").log(Level.SEVERE, "No se ha podido prohibir la declaración DOCTYPE en la factoria XML: " + e); //$NON-NLS-1$ //$NON-NLS-2$
				throw new IllegalStateException("No se pudo prohibir DOCTYPE en DocumentBuilderFactory", e);
			}

			// Desactivamos entidades externas también en DOM (además de SAX)
			try {
				SECURE_BUILDER_FACTORY.setFeature("http://xml.org/sax/features/external-general-entities", false); //$NON-NLS-1$
				SECURE_BUILDER_FACTORY.setFeature("http://xml.org/sax/features/external-parameter-entities", false); //$NON-NLS-1$
			}
			catch (final Exception e) {
				Logger.getLogger("es.gob.afirma").log(Level.SEVERE, "No se han podido desactivar entidades externas en DocumentBuilderFactory: " + e); //$NON-NLS-1$ //$NON-NLS-2$
				throw new IllegalStateException("No se pudieron desactivar entidades externas en DocumentBuilderFactory", e);
			}

			SECURE_BUILDER_FACTORY.setValidating(false);
			SECURE_BUILDER_FACTORY.setNamespaceAware(true);
			SECURE_BUILDER_FACTORY.setXIncludeAware(false);
			SECURE_BUILDER_FACTORY.setExpandEntityReferences(false);
		}
		return SECURE_BUILDER_FACTORY.newDocumentBuilder();
	}

	/**
     * Construye un parser SAX seguro que no accede a recursos externos.
     * Fail-closed: si no se pueden establecer las características de seguridad,
     * se lanza una excepción.
     * @return Factoría segura.
	 * @throws SAXException Cuando ocurre un error de SAX.
	 * @throws ParserConfigurationException Cuando no se puede crear el parser.
     */
	public static SAXParser getSecureSAXParser() throws ParserConfigurationException, SAXException {
		if (SAX_FACTORY == null) {
			SAX_FACTORY = SAXParserFactory.newInstance();
			try {
				SAX_FACTORY.setFeature(SecureXmlConstants.FEATURE_SECURE_PROCESSING, Boolean.TRUE.booleanValue());
			}
			catch (final Exception e) {
				Logger.getLogger("es.gob.afirma").log(Level.SEVERE, "No se ha podido establecer una característica de seguridad en la factoria XML: " + e); //$NON-NLS-1$
				throw new IllegalStateException("No se pudo establecer FEATURE_SECURE_PROCESSING en SAXParserFactory", e);
			}

			// Desactivamos las características que permiten la carga de elementos externos
			try {
				SAX_FACTORY.setFeature("http://xml.org/sax/features/external-general-entities", false); //$NON-NLS-1$
				SAX_FACTORY.setFeature("http://xml.org/sax/features/external-parameter-entities", false); //$NON-NLS-1$
			}
			catch (final Exception e) {
				Logger.getLogger("es.gob.afirma").log(Level.SEVERE, "No se ha podido establecer una característica de seguridad en la factoria SAX XML: " + e); //$NON-NLS-1$
				throw new IllegalStateException("No se pudieron desactivar entidades externas en SAXParserFactory", e);
			}

			// Prohibimos la declaración DOCTYPE en SAX también
			try {
				SAX_FACTORY.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true); //$NON-NLS-1$
			}
			catch (final Exception e) {
				Logger.getLogger("es.gob.afirma").log(Level.SEVERE, "No se ha podido prohibir la declaración DOCTYPE en la factoria SAX XML: " + e); //$NON-NLS-1$
				throw new IllegalStateException("No se pudo prohibir DOCTYPE en SAXParserFactory", e);
			}

			SAX_FACTORY.setValidating(false);
			SAX_FACTORY.setNamespaceAware(true);
		}
		return SAX_FACTORY.newSAXParser();
	}
}
