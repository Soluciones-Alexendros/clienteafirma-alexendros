# Changelog

### Propósito de este documento

- **Objetivos:** Registrar cambios de **este fork** (gobernanza, CI, higiene) sin reescribir el historial de producto de CTT.
- **Estructura:** Keep a Changelog → Unreleased → notas de alineación. La versión de producto sigue siendo **1.9.1**.
- **Contenido a integrar según contexto:** No copies un changelog de SaaS ni inventes releases de Autofirma. Los cambios de producto upstream se leen en [ctt-gob-es/clienteafirma](https://github.com/ctt-gob-es/clienteafirma).

Formato inspirado en [Keep a Changelog](https://keepachangelog.com/es/1.1.0/).

## [Unreleased]

### Security

- `org.json:json` unificado a `20260814` (GHSA-ghc9-756g-vrcv / CVE-2026-59171).
- Ola ASVS T3–T9 en la rama de integración: canal local TLS 1.2 sin suites SHA-1 (#167); keystore local sin contraseña fija y permisos acotados (#169); desactivación SSL delimitada y configuración remota validada (#171); validación de respuesta TSA con `strictSslChecks` (#173); token firmado de sesión trifásica (#174); `idsession` obligatorio y origen del websocket local (#175); cifrado por defecto en CBC y rechazo de algoritmos obsoletos salvo `-DallowLegacyCiphers=true` (#176). Los modos estrictos de websocket y token trifásico siguen en `false` por defecto hasta T20.
- **`strictSslChecks` activado por defecto** (#92): se comprueba el certificado del servidor. Si falla, el aviso explica qué ha pasado, qué revisar (fecha/hora, proxy/red, certificado) y cómo desactivar la opción en Preferencias (deja la conexión sin protección). El botón por defecto es Cerrar. Sin aviso al arrancar. Para sedes con certificado propio: desmarcar «Aceptar sólo conexiones con sitios seguros» en Preferencias, o `-DstrictSslChecks=false`.
- Retirados de `HEAD` el PFX de instalador, los `afirma.keystore` de aplicación y las contraseñas literales. La firma de instalador exige `AFIRMA_SIGN_PFX` y `AFIRMA_SIGN_PASS` fuera de Git. Gate: `make security-material`.

### Added

- Matriz ASVS L2 y modelo de amenazas (`docs/security/ASVS-L2.md`, #166); evidencia T10 de que `SHA1RejectionTest` cubre el helper `validateSHA1ForFormat`, no el rechazo en firmadores XAdES/CAdES/PAdES (#177). No es cumplimiento ASVS L2.
- Workflows de interop y imagen DSS, scripts `scripts/interop/*` y harness `tests/interop-dss/`.
- Suite Archify `.archify/20261001-sec-material/` (arquitectura, flujo, secuencia F2, material y ciclo de release).
- **Monorepo Autofirma-2026:** capa comunitaria (docs F0–F10, vectores, packaging, harness, Archify, gates 360º) absorbida desde `Alexendros/Autofirma-2026` @ `10996b6`. Este repo es el canónico.
- Jobs CI `community` + `build-baseline` in-tree (JAR + F2). `build-fork-bc` limitado a ramas `crypto/**`.
- Fachada ampliada: `make quality` / `make test-community` / `make validate` / gates `review360*`.

### Added (previo)

- Alineación P0 al contrato de repositorio: README con meta-sección Propósito, `LICENSE` de raíz (puntero SPDX; no pisa `license/`), SECURITY, CONTRIBUTING, CHANGELOG, CODEOWNERS, plantillas de issue/PR, Renovate y CI `quality` / `test` / `build` / `smoke`.
- Fachada `make lint` / `make test` / `make smoke` / `make build` sobre el núcleo `afirma-core`.

### Notes

- Fork de CTT (Cliente @firma 1.9.1) + programa comunitario. Meta histórico: [Autofirma-2026](https://github.com/Alexendros/Autofirma-2026) (archivado tras migración).
- `make build` empaqueta `afirma-core`; Autofirma.jar vía `-Denv=install` / `scripts/mvp.sh` / workflow `build-baseline`.
