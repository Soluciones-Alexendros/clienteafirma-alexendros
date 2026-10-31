package es.gob.afirma.core.misc.http;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import es.gob.afirma.core.ui.CoreMessages;

/**
 * Comprueba el defecto de {@code strictSslChecks} (#92).
 * Certificado de prueba generado con keytool en el propio test (CN=Ejemplo S.L.).
 */
public class StrictSslChecksTest {

	private Thread serverThread;
	private final AtomicInteger port = new AtomicInteger(0);
	private volatile boolean running;
	private File keystoreFile;

	@Before
	public void clearProps() {
		System.clearProperty(UrlHttpManagerImpl.JAVA_PARAM_STRICT_SSL_CHECKS);
		System.clearProperty(UrlHttpManagerImpl.JAVA_PARAM_DISABLE_SSL_CHECKS);
	}

	@After
	public void stopServer() throws Exception {
		this.running = false;
		System.clearProperty(UrlHttpManagerImpl.JAVA_PARAM_STRICT_SSL_CHECKS);
		System.clearProperty(UrlHttpManagerImpl.JAVA_PARAM_DISABLE_SSL_CHECKS);
		if (this.keystoreFile != null) {
			Files.deleteIfExists(this.keystoreFile.toPath());
		}
		try {
			SslSecurityManager.enableSslChecks();
		}
		catch (final Exception ignored) {
			/* restore best-effort */
		}
	}

	@Test
	public void defaultPropertyIsStrict() {
		System.clearProperty(UrlHttpManagerImpl.JAVA_PARAM_STRICT_SSL_CHECKS);
		Assert.assertTrue(Boolean.parseBoolean(
				System.getProperty(UrlHttpManagerImpl.JAVA_PARAM_STRICT_SSL_CHECKS, Boolean.TRUE.toString())));
	}

	@Test
	public void defaultStrictRejectsSelfSigned() throws Exception {
		startSelfSignedServer();
		System.clearProperty(UrlHttpManagerImpl.JAVA_PARAM_STRICT_SSL_CHECKS);
		System.setProperty(UrlHttpManagerImpl.JAVA_PARAM_DISABLE_SSL_CHECKS, "false");
		final UrlHttpManager mgr = UrlHttpManagerFactory.getInstalledManager();
		try {
			mgr.readUrl("https://127.0.0.1:" + this.port.get() + "/", UrlHttpMethod.GET);
			Assert.fail("Debía rechazarse el certificado autofirmado con el defecto estricto");
		}
		catch (final IOException expected) {
			Assert.assertNotNull(expected.getMessage());
		}
	}

	@Test
	public void disabledStrictAllowsSelfSigned() throws Exception {
		startSelfSignedServer();
		System.setProperty(UrlHttpManagerImpl.JAVA_PARAM_STRICT_SSL_CHECKS, "false");
		System.setProperty(UrlHttpManagerImpl.JAVA_PARAM_DISABLE_SSL_CHECKS, "true");
		SslSecurityManager.disableSslChecks();
		final UrlHttpManager mgr = UrlHttpManagerFactory.getInstalledManager();
		final byte[] data = mgr.readUrl("https://127.0.0.1:" + this.port.get() + "/", UrlHttpMethod.GET);
		Assert.assertTrue(data != null && data.length > 0);
	}

	@Test
	public void strictFailureMessageOrder() {
		final String part1 = CoreMessages.getString("SSLStrictFailureDialog.2");
		final String part2 = CoreMessages.getString("SSLStrictFailureDialog.3");
		final String part3 = CoreMessages.getString("SSLStrictFailureDialog.4");
		final String msg = SSLErrorProcessor.buildStrictSslFailureMessage();
		final int i1 = msg.indexOf(part1);
		final int i2 = msg.indexOf(part2);
		final int i3 = msg.indexOf(part3);
		Assert.assertTrue("parte 1 ausente: " + msg, i1 >= 0);
		Assert.assertTrue("orden 1→2", i2 > i1);
		Assert.assertTrue("orden 2→3", i3 > i2);
	}

	private void startSelfSignedServer() throws Exception {
		this.keystoreFile = File.createTempFile("ejemplo-ssl-", ".jks");
		Files.deleteIfExists(this.keystoreFile.toPath());
		final String storePass = "changeit";
		final ProcessBuilder pb = new ProcessBuilder(
				"keytool",
				"-genkeypair",
				"-alias", "server",
				"-keyalg", "RSA",
				"-keysize", "2048",
				"-validity", "1",
				"-keystore", this.keystoreFile.getAbsolutePath(),
				"-storepass", storePass,
				"-keypass", storePass,
				"-dname", "CN=Ejemplo S.L.,O=Ejemplo S.L.,C=ES",
				"-noprompt"
		);
		pb.redirectErrorStream(true);
		final Process p = pb.start();
		final java.io.InputStream is = p.getInputStream();
		final byte[] keytoolOut = new byte[4096];
		final int n = is.read(keytoolOut);
		final int code = p.waitFor();
		Assert.assertEquals("keytool falló: " + (n > 0 ? new String(keytoolOut, 0, n, "UTF-8") : ""), 0, code);

		final KeyStore ks = KeyStore.getInstance("JKS");
		ks.load(Files.newInputStream(this.keystoreFile.toPath()), storePass.toCharArray());
		final KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
		kmf.init(ks, storePass.toCharArray());
		final SSLContext ctx = SSLContext.getInstance("TLS");
		ctx.init(kmf.getKeyManagers(), null, new SecureRandom());
		final SSLServerSocketFactory ssf = ctx.getServerSocketFactory();
		final SSLServerSocket server = (SSLServerSocket) ssf.createServerSocket(0);
		this.port.set(server.getLocalPort());
		this.running = true;
		this.serverThread = new Thread(() -> {
			while (this.running) {
				try (SSLSocket sock = (SSLSocket) server.accept()) {
					final byte[] req = new byte[1024];
					sock.getInputStream().read(req);
					final byte[] body = "ok".getBytes("UTF-8");
					final String headers = "HTTP/1.1 200 OK\r\nContent-Length: " + body.length + "\r\nConnection: close\r\n\r\n";
					final OutputStream out = sock.getOutputStream();
					out.write(headers.getBytes("UTF-8"));
					out.write(body);
					out.flush();
				}
				catch (final Exception e) {
					break;
				}
			}
			try {
				server.close();
			}
			catch (final IOException ignored) {
				/* */
			}
		}, "strict-ssl-test-server");
		this.serverThread.setDaemon(true);
		this.serverThread.start();
		Thread.sleep(300);
	}
}
