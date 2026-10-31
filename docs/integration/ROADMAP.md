# Hoja de ruta de integración — Autofirma comunitaria

### Propósito de este documento

- **Objetivos:** Prioridades del programa (P0–P3) con estado actual y enlaces a informes técnicos.
- **Estructura:** Matriz de prioridades → detalle P1–P3 → riesgos → criterios de éxito.
- **Contenido a integrar según contexto:** Español claro; tecnicismos entre paréntesis. El tablero operativo diario está en [TASKS.md](../TASKS.md).

**Fecha de refresco:** 2026-10-01 · **Versión:** 2.1  
**Canónico:** [Soluciones-Alexendros/clienteafirma-alexendros](https://github.com/Soluciones-Alexendros/clienteafirma-alexendros), rama `master`.

---

## Matriz de prioridades

| Prioridad | Ítem | Estado | Notas |
|-----------|------|--------|-------|
| P0 | Alineación del repositorio (contrato, CI mínima) | Hecho | PR #1 |
| P1-BC | Migración SpongyCastle → BouncyCastle 1.86 | Hecho en el fork | En `master` del monorepo (PR #3 a 1.78.1; bump CVE a 1.86). PR CTT [#573](https://github.com/ctt-gob-es/clienteafirma/pull/573) cerrada sin merge; seguimiento [#572](https://github.com/ctt-gob-es/clienteafirma/issues/572) |
| P1-TEST | Batería de validación (harness) en módulos cripto | Hecho | 12 tests nuevos; ver [TEST-PORTING-MAP.md](TEST-PORTING-MAP.md) |
| P1-TLS | Análisis de solape TLS con upstream | Hecho (análisis) | Preferencia opt-in; default sin endurecer. Código en integración (`prefs/strict-ssl`) |
| P0-SEC | Retirar PFX y contraseña de firma del índice | Hecho en esta rama | `make security-material`. Aviso de revocación redactado y no enviado |
| P2-A11Y | Nombres accesibles de certificado, PIN y confirmar | Hecho en `master` | Commit `8420011`. Sesión Orca pendiente |
| P2-CI | Reactor completo y tests de seguridad en CI | Pendiente | Hoy `master` exige `quality`, `test`, `build`, `smoke`. El reactor `-Psonar` no es check |
| P2-RELEASE | Tag `v1.9.1-community.N`, SBOM y atestación | Bloqueado | No se publica hasta cerrar PAdES, XML fail-closed y Actions por SHA |
| P3-LINUX | Flatpak / AppImage | Experimental | No bloquea el núcleo |
| P3-TRIPHASE | Servidor trifásico (firma en tres pasos con servidor) | Experimental | Informe F8; no mezclar con Jakarta en esta línea |

---

## P1-BC — BouncyCastle (hecho en el fork)

**Qué es:** SpongyCastle era una copia antigua de la biblioteca criptográfica BouncyCastle. El fork usa BouncyCastle 1.86 moderno (`jdk18on`) para firmar con los mismos formatos.

**Entregables:** [BC-DIFF-ANALYSIS.md](BC-DIFF-ANALYSIS.md), [BC-NOISE-REPORT.md](BC-NOISE-REPORT.md), [BC-STRATEGY.md](BC-STRATEGY.md) (estrategia B aplicada: diff limpio).

**Hechos clave:**

- ~95 archivos tocados; diff limpio frente a upstream del orden de ~1000 líneas funcionales.
- 13 módulos cripto afectados.
- Construcción y vectores F2 (firma de prueba CAdES/XAdES/PAdES/FacturaE/cofirma/contrafirma) en verde en el monorepo.
- Corrección: el “ruido” de espacios en el diff bruto era ~92 %; la cifra útil es la del diff limpio.

**Upstream:** la PR #573 al CTT no se fusionó. El trabajo vive en este monorepo; la issue #572 sigue el hilo.

---

## P1-TEST — Harness de validación (hecho)

Cuatro clases de test nuevas (12 casos) en CAdES, CMS, PAdES y XAdES. No se portó `TestPAdESModificationDetection` hacia abajo para evitar un ciclo Maven (ya existe en `afirma-crypto-validation`).

Detalle: [TEST-PORTING-MAP.md](TEST-PORTING-MAP.md).

**Hueco conocido:** la suite completa de PDF tiene 4 errores previos en `TestPadesBaseline` (no introducidos por BC).

---

## P1-TLS — Solape TLS (análisis hecho; código en integración)

- Decisión de producto: **no** activar comprobaciones TLS estrictas por defecto (las sedes a veces usan cadenas que fallarían).
- `strictSslChecks` activo por defecto (#92); desactivar en Preferencias o `-DstrictSslChecks=false` si la sede lo necesita.
- Análisis: [TLS-OVERLAP-ANALYSIS.md](TLS-OVERLAP-ANALYSIS.md).

---

## P2-A11Y — Accesibilidad (en integración)

- Nombres accesibles para lista de certificados, campo PIN y diálogo de confirmar (lectores de pantalla).
- Coordinar con trabajo upstream cuando exista; sesión real con Orca documentada en F7.

---

## P2-CI — Pipeline (pendiente)

Estado actual del monorepo: jobs `quality` / `test` / `community` / `build` / `smoke` + `build-baseline` (JAR + F2).

Objetivo futuro: ejecutar también la batería de validación cripto en CI de forma sistemática (sin alargar en exceso el tiempo de cola).

---

## P3 — Empaquetado y trifásico (no bloqueantes)

- Linux moderno (Flatpak/AppImage): experimental.
- Servidor trifásico: compatible a nivel de informe/WAR; validación en sandbox diferida.

---

## Riesgos

| Riesgo | Probabilidad | Impacto | Mitigación |
|--------|--------------|---------|------------|
| Incompatibilidad API BouncyCastle | Media | Alta | Vectores F2 + tests de validación |
| Regresión de tests | Baja | Media | `make validate` + F2 |
| Conflicto al reintegrar en CTT | Alta | Media | Diff limpio; issue #572 |
| Cambiar TLS por defecto | Baja | Alta | Solo opt-in |
| Retraso de empaquetado | Alta | Baja | P3; no bloquea núcleo |

---

## Próximos pasos

Orden de la rama de seguridad. Un ítem no se salta porque el anterior esté narrado: hace falta el gate en verde.

| Orden | ID | Qué hay que hacer | Cierre medible |
|---:|---|---|---|
| 1 | SEC-2026-002 | Corregir los OID inválidos `1.56.23.1` y `1.56.23.2` del fixture `TestPadesBaseline` y dejar un test que exija su rechazo | La clase pasa en JDK 8 |
| 2 | SEC-2026-003 | Hacer fail-closed `SecureXmlBuilder` y `SecureXmlTransformer` y migrar las factorías directas que quedan | Un fallo al fijar una feature aborta el parseo |
| 3 | SEC-2026-004 | Suite XXE de ocho casos (fichero, HTTP, DTD, XInclude, billion laughs, XSLT, XSD, XML válido) | Sin lectura ni conexión |
| 4 | SEC-2026-005 | Fijar por SHA las 9 Actions mutables de `ci.yml` | `check-actions-pinning.sh` sale 0 |
| 5 | SEC-2026-006 | CodeQL y Semgrep con SARIF archivado | Cero alertas altas sin triage |
| 6 | SEC-2026-007 | Dependency Review en el PR. SBOM y CVE gate cuando exista `NVD_API_KEY` | El delta de dependencias bloquea altas y críticas nuevas |
| 7 | SEC-2026-008 | Política de generación: SHA-1 rechazado salvo la excepción documentada de ODF | Test negativo de generación |
| 8 | SEC-2026-012 | Categoría obligatoria en `@Ignore` nuevos | Cero skips opacos en el diff |
| 9 | SEC-2026-010 | Doble build del JAR en worktrees limpios | Mismo SHA-256, o la primera entrada ZIP distinta publicada |
| 10 | SEC-2026-011 | Tag `v1.9.1-community.1` firmado, SBOM, `SHA256SUMS` y atestación | `gh attestation verify` contra este repositorio |

No se abre release, no se reescribe el historial y no se envía el aviso de la serie `4AFA8450` hasta un sí distinto. El reactor `-Psonar` y los quince POM fuera de perfil (applets, webstart, miniapplet) quedan inventariados; no bloquean el núcleo, pero tampoco se dan por compilados.

## Criterios de éxito P1 (cumplidos en el fork)

- [x] Diff limpio BC en el monorepo
- [x] Dependencias de test en módulos cripto
- [x] 12/12 tests nuevos en verde
- [x] JAR de producto + F2 sin regresión de formatos
- [x] Decisión TLS documentada (default no estricto)

---

## Verificación de pipeline (histórico 2026-09-26 + monorepo #5)

En la rama limpia BC y después en el monorepo absorbido: build Maven, JAR `autofirma.jar`, tests nuevos y F2 6/6 formatos en verde. Tras el PR #5, CI del canónico pasó `quality`, `test`, `community`, `build`, `smoke`, `build-linux-jdk8`, `f2-vectors`, `actionlint`.
