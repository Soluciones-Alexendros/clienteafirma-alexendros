package es.gob.afirma.core.misc;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Zip Slip (#61): entradas fuera del destino deben fallar con mensaje claro.
 */
public class ZipSecurePathsTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	@Test
	public void rejectsPathOutsideDestination() throws Exception {
		final File dest = this.tmp.newFolder("dest");
		final File escape = new File(dest, "../evil.txt");
		try {
			ZipSecurePaths.requireInsideDestination(dest, escape);
			Assert.fail("Debía rechazar la ruta fuera del destino");
		}
		catch (final IOException e) {
			Assert.assertTrue(e.getMessage().contains("fuera de la carpeta de destino"));
			Assert.assertFalse(new File(this.tmp.getRoot(), "evil.txt").exists());
		}
	}

	@Test
	public void acceptsNormalEntry() throws Exception {
		final File dest = this.tmp.newFolder("dest");
		final File ok = new File(dest, "ok.txt");
		ZipSecurePaths.requireInsideDestination(dest, ok);
		try (FileOutputStream fos = new FileOutputStream(ok)) {
			fos.write("Ejemplo S.L.".getBytes(StandardCharsets.UTF_8));
		}
		Assert.assertTrue(ok.isFile());
	}

	@Test
	public void zipSlipEntryDoesNotCreateFile() throws Exception {
		final File dest = this.tmp.newFolder("out");
		final File zip = this.tmp.newFile("slip.zip");
		try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zip))) {
			zos.putNextEntry(new ZipEntry("../evil.txt"));
			zos.write("x".getBytes(StandardCharsets.UTF_8));
			zos.closeEntry();
		}
		boolean failed = false;
		try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(new java.io.FileInputStream(zip))) {
			ZipEntry e;
			while ((e = zis.getNextEntry()) != null) {
				final File out = new File(dest, e.getName());
				ZipSecurePaths.requireInsideDestination(dest, out);
			}
		}
		catch (final IOException ex) {
			failed = true;
			Assert.assertTrue(ex.getMessage().contains("fuera de la carpeta de destino"));
		}
		Assert.assertTrue(failed);
		Assert.assertFalse(new File(this.tmp.getRoot(), "evil.txt").exists());
	}
}
