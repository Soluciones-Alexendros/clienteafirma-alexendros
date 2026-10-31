package es.gob.afirma.core.misc;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

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

	/**
	 * Resuelve con seguridad la ruta final de una entrada de ZIP dentro del directorio
	 * de destino, evitando Zip Slip (incluyendo rutas absolutas y enlaces simb&oacute;licos).
	 * @param destDir Directorio de extracci&oacute;n permitido.
	 * @param entryName Nombre de la entrada del ZIP.
	 * @return Fichero resultante, garantizado dentro de {@code destDir}.
	 * @throws IOException Si la ruta queda fuera del destino.
	 */
	public static File safeResolve(final File destDir, final String entryName) throws IOException {
		final Path dir = destDir.toPath();
		final Path base = Files.exists(dir) ? dir.toRealPath().normalize()
											: dir.toAbsolutePath().normalize();
		final Path target = base.resolve(entryName).normalize();
		if (!target.startsWith(base)) {
			throw new IOException("Entrada de zip fuera del directorio destino: " + entryName); //$NON-NLS-1$
		}
		// Comprobaci&oacute;n adicional contra enlaces simb&oacute;licos del padre real.
		if (Files.exists(target.getParent())) {
			final Path realParent = target.getParent().toRealPath();
			if (!realParent.startsWith(base)) {
				throw new IOException("Entrada de zip fuera del directorio destino: " + entryName); //$NON-NLS-1$
			}
		}
		return target.toFile();
	}
}
