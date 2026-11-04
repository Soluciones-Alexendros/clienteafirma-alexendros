package es.gob.afirma.interop.dss;

import java.io.ByteArrayInputStream;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Collections;

import eu.europa.esig.dss.diagnostic.DiagnosticData;
import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.model.InMemoryDocument;
import eu.europa.esig.dss.model.x509.CertificateToken;
import eu.europa.esig.dss.simplereport.SimpleReport;
import eu.europa.esig.dss.spi.validation.CommonCertificateVerifier;
import eu.europa.esig.dss.spi.x509.CommonCertificateSource;
import eu.europa.esig.dss.spi.x509.CommonTrustedCertificateSource;
import eu.europa.esig.dss.validation.SignedDocumentValidator;
import eu.europa.esig.dss.validation.reports.Reports;

/**
 * Valida una firma con EU DSS usando como &uacute;nica ancla de confianza la CA recibida.
 * Sin LOTL, sin LOTE, sin AIA/OCSP/CRL en red y con la pol&iacute;tica por defecto.
 */
public final class DssValidator {

	/** Resultado de una validaci&oacute;n. */
	public record Result(String indication, String subIndication, String signatureFormat, int validSignaturesCount) {
	}

	private DssValidator() {
		// No instanciable
	}

	/**
	 * Valida una firma autocontenida (XAdES enveloped, PAdES, ASiC...).
	 * @param signature Firma reconocible por DSS.
	 * @param caCertificate Certificado de la CA en DER.
	 * @return Resultado con indicaci&oacute;n, subindicaci&oacute;n y formato detectado.
	 * @throws ValidationException Si la firma o la CA no se pueden procesar.
	 */
	public static Result validate(final byte[] signature, final byte[] caCertificate) throws ValidationException {
		return validate(signature, caCertificate, null);
	}

	/**
	 * Valida una firma, opcionalmente con su documento original (firmas detached como CAdES).
	 * @param signature Firma reconocible por DSS.
	 * @param caCertificate Certificado de la CA en DER.
	 * @param detachedContent Documento firmado, o {@code null} si la firma lo incluye.
	 * @return Resultado con indicaci&oacute;n, subindicaci&oacute;n y formato detectado.
	 * @throws ValidationException Si la firma o la CA no se pueden procesar.
	 */
	public static Result validate(final byte[] signature, final byte[] caCertificate, final byte[] detachedContent)
			throws ValidationException {

		if (signature == null || signature.length == 0) {
			throw new ValidationException("firma vacia");
		}
		if (caCertificate == null || caCertificate.length == 0) {
			throw new ValidationException("CA vacia");
		}

		final X509Certificate ca;
		try {
			ca = (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(caCertificate));
		}
		catch (final Exception e) {
			throw new ValidationException("CA no es un certificado X.509 valido", e);
		}

		final DSSDocument document = new InMemoryDocument(signature);

		final SignedDocumentValidator validator;
		try {
			validator = SignedDocumentValidator.fromDocument(document);
		}
		catch (final Exception e) {
			throw new ValidationException("formato de firma no soportado", e);
		}

		final CommonCertificateSource anchors = new CommonCertificateSource();
		anchors.addCertificate(new CertificateToken(ca));
		final CommonTrustedCertificateSource trustAnchors = new CommonTrustedCertificateSource();
		trustAnchors.importAsTrusted(anchors);

		final CommonCertificateVerifier verifier = new CommonCertificateVerifier();
		// Solo esta CA como ancla; ninguna fuente de red.
		verifier.setTrustedCertSources(trustAnchors);
		verifier.setAIASource(null);
		verifier.setOcspSource(null);
		verifier.setCrlSource(null);
		validator.setCertificateVerifier(verifier);
		if (detachedContent != null) {
			// Firma detached (CAdES): DSS necesita el documento original para validarla.
			validator.setDetachedContents(Collections.singletonList(new InMemoryDocument(detachedContent)));
		}

		final Reports reports;
		try {
			reports = validator.validateDocument();
		}
		catch (final Exception e) {
			throw new ValidationException("DSS no pudo validar el documento", e);
		}

		final SimpleReport simpleReport = reports.getSimpleReport();
		final String signatureId = simpleReport.getFirstSignatureId();
		if (signatureId == null) {
			return new Result("INDETERMINATE", "NO_SIGNATURE_FOUND", null, 0);
		}

		final DiagnosticData diagnostic = reports.getDiagnosticData();
		final String format = String.valueOf(diagnostic.getSignatureFormat(signatureId));

		return new Result(
			simpleReport.getIndication(signatureId) == null ? null : simpleReport.getIndication(signatureId).name(),
			simpleReport.getSubIndication(signatureId) == null ? null : simpleReport.getSubIndication(signatureId).name(),
			format,
			simpleReport.getValidSignaturesCount());
	}

	/** Error controlado de validaci&oacute;n. */
	public static class ValidationException extends Exception {
		private static final long serialVersionUID = 1L;

		public ValidationException(final String message) {
			super(message);
		}

		public ValidationException(final String message, final Throwable cause) {
			super(message, cause);
		}
	}

	/** Utilidad para tests: cadena vac&iacute;a. */
	static java.util.List<X509Certificate> emptyChain() {
		return Collections.emptyList();
	}
}
