# Triage Semgrep / Code scanning — clienteafirma-alexendros

**Fecha:** 2026-10-25 · **Ámbito:** alertas abiertas en `master` (Semgrep OSS + CodeQL).  
**Propósito:** Clasificar hallazgos sin remediar el inventario completo en este ciclo.  
**Conteo contrastado:** ~303 abiertas (Semgrep OSS ~200 + CodeQL ~103).

## Resumen ejecutivo

| Familia | Conteo (orden) | Veredicto | Acción en este ciclo |
|---------|----------------|-----------|----------------------|
| `command-injection-process-builder` | 25 | Riesgo local de escritorio / instalador | Documentado; sin parche |
| SSRF (`csharp...ssrf`) | 3 | Hallazgo real de diseño en módulo legado | Documentado; fuera de ciclo |
| `use-of-sha1` | ~9 Semgrep | Compatibilidad de formatos / digests legacy | Riesgo aceptado (SEC-2026-008) |
| `weak-random` | 4 | Hallazgo real, acotado a CA local | Documentado; sin parche aquí |
| Tags mutables de Actions | ~50 Semgrep | Deuda CI (parte ya pinneada en SEC-2026-005) | Deuda |
| Resto (HTTP en claro, reflection, XSS docs, etc.) | resto | Deuda / ruido upstream | Deuda |

No se cierran alertas en la pestaña Security de GitHub en este trabajo: el triage vive en este documento.

## Command-injection ×25

**Regla:** `java.lang.security.audit.command-injection-process-builder.command-injection-process-builder`

**Dónde (agrupado):**

| Módulo / clase | Rol |
|----------------|-----|
| `afirma-simple` · `DesktopUtil`, `MainMenu`, `PluginsManagementHandler`, `LookAndFeelManager` | Abrir URLs, menús, plugins |
| `afirma-simple` · `RestoreConfig{MacOSX,Linux,Windows,Firefox}` | Restaurar perfil / certutil / Firefox |
| `afirma-simple` · `ShellScript`, `AppleScript` (macOS) | Scripts de instalación |
| `afirma-ui-simple-configurator` · `Configurator{MacOSX,Linux,Firefox*}` , `WindowsCmdExecutor` | Instalación CA / certutil |
| `afirma-ui-core-jse*` · `AWTUIManager`, `CertificateUtils` | Abrir visor de certificados |
| `afirma-simple-plugin-hash` · `HashPlugin` | Lanzar helper |
| `afirma-keystores-mozilla` · `ShellScript` | NSS / Mozilla |

**Veredicto:** `ProcessBuilder` / `Runtime.exec` del cliente de escritorio e instalador (certutil, Firefox, `osascript`, `tasklist`, `ps`). No es RCE web: el atacante ya controlaría la máquina o el contenido del perfil local. Mitigaciones futuras: validar rutas absolutas permitidas y evitar concatenar entradas de usuario en la línea de comando. **No se parchea aquí.**

## SSRF ×3

**Regla:** `csharp.lang.security.ssrf.web-request.ssrf`

**Dónde:** [`afirma-windows-store/App1/Utils/AfirmaMetroUtils.cs`](../../afirma-windows-store/App1/Utils/AfirmaMetroUtils.cs) — `WebRequest.Create(servlet)` en `SendDataServlet`, `getDataServlet`, `SendErrorServlet`.

**Veredicto:** Hallazgo **real de diseño**. La URL del servlet llega del protocolo/flujo Metro Store. El módulo Windows Store es legado respecto al cliente Java actual. Remediación futura: allowlist de hosts/esquemas HTTPS antes de `WebRequest.Create`. **Fuera de este ciclo.**

## SHA-1 (`use-of-sha1`)

**Dónde (muestra):**

- Protocolo: `ProtocolInvocationLauncherSign*.java`
- Servidor trifásico / batch: `SignatureService`, `JSONSingleSign*`, `TempStoreFileSystem`
- XMLDSig / keystores: `AOXMLDSigSigner`, `AOKeyStoreManager`, `AggregatedKeyStoreManager`
- OCSP helper: `OcspHelper`
- UI legacy: `MainOptionsPane`
- Configurador: digest del CA local (`CertUtil` / OIW SHA-1 en construcción de certificado)

**Veredicto:** Mezcla de (a) interoperabilidad con firmas y perfiles históricos y (b) digests internos. Ya existe política de rechazo de SHA-1 en firma con excepción ODF (`SEC-2026-008`). Eliminar SHA-1 del código rompería validación y generación compatibles. **Riesgo aceptado de compatibilidad** mientras la política de rechazo en firmas nuevas se mantenga.

## `weak-random` ×4

**Regla:** `java.lang.security.audit.crypto.weak-random.weak-random`

**Dónde:**

- `afirma-ui-simple-configurator/.../CertUtil.java` (serie del certificado CA local)
- `afirma-simple/.../restoreconfig/CertUtil.java` (mismo patrón)

**Veredicto:** Hallazgo **real**. Se usa `new Random().nextInt()` como número de serie de un CA autofirmado de instalación local, no como material criptográfico de firma ciudadana. Remedio futuro: `SecureRandom` para la serie. **No entra en este PR.**

## Resto (deuda)

- **GitHub Actions mutable tags** (~50): ruido Semgrep sobre `uses: actions/foo@vN`. Parte del árbol ya lleva pin por SHA (`SEC-2026-005`); completar el pin en workflows restantes es deuda CI.
- **HTTP en claro / integrity / reflection / permissive CORS / XXE residual / path traversal**: herencia upstream o sinks ya parcialmente mitigados (`SecureXmlBuilder`). Revisar por oleadas futuras.
- **CodeQL en PR #10 (histórico):** el check rojo «CodeQL» del PR #10 eran 4 alertas en `docs/portal/*` y `validate-portal.yml`, ficheros **no presentes en `master`**. No afectan el inventario de `master`.

## Criterio de salida de este documento

Este triage cumple el objetivo de inventario y priorización. No exige 0 alertas de code scanning. El cierre de críticas de Dependabot (BouncyCastle) es un PR aparte (`fix/deps-bouncycastle`).
