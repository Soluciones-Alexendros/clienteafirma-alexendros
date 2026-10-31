package es.gob.afirma.core.misc;

import java.io.File;
import java.io.IOException;

/**
 * Comprueba que una entrada de ZIP no escape del directorio de destino (Zip Slip).
 */
public final class ZipSecurePaths {

	private ZipSecurePaths() {
		// No instanciable
	}

	/**
	 * @param destinationDir Directorio de extracci&oacute;n permitido.
	 * @param entryTarget Fichero resultante de unir destino + nombre de entrada.
	 * @throws IOException Si la ruta can&oacute;nica queda fuera del destino.
	 */
	public static void requireInsideDestination(final File destinationDir, final File entryTarget) throws IOException {
		final File dest = destinationDir.getCanonicalFile();
		final File target = entryTarget.getCanonicalFile();
		final String destPath = dest.getPath();
		final String targetPath = target.getPath();
		final String prefix = destPath.endsWith(File.separator) ? destPath : destPath + File.separator;
		if (!targetPath.equals(destPath) && !targetPath.startsWith(prefix)) {
			throw new IOException(
					"El archivo comprimido apunta fuera de la carpeta de destino. No se ha extraído nada de esa entrada."
			);
		}
	}
}
