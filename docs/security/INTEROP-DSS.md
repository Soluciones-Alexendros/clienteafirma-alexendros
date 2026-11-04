# Interoperabilidad con EU DSS (T3b · A4)

Estado: **imagen propia en construcción**. La validación DSS está implementada y se ejecuta en
cada run. La imagen de la demo webapp la construye este repositorio desde la versión fijada de
DSS y se publica en GHCR; mientras no exista la primera imagen, el paso A4 va en **modo aviso**.

## Qué comprueba

`scripts/interop/dss-validate.sh` envía la firma (CAdES hecha con el certificado sintético) al
endpoint REST de validación de DSS y exige:

- `indication == TOTAL_PASSED`;
- `validSignaturesCount >= 1`.

Si no se cumple, el job falla. **No se desactiva DSS ni se marca el paso como omitido.**

## Hechos comprobados (2026-11-04)

| Vía de suministro | Comprobación | Resultado |
| --- | --- | --- |
| Imagen Docker oficial `nowina/dss-demo-webapp` | `docker run` en el run 41744474250 | **No existe**: `pull access denied` |
| Búsqueda en Docker Hub (`dss-demo`, `esig`, `nowina`, `digital-signature-service`) | API de Docker Hub | Ninguna imagen oficial de la demo webapp |
| GHCR `ghcr.io/esig/dss-demo-webapp` | `GET /v2/.../tags/list` | 401: no publicado |
| Repositorio del proyecto (`esig/dss`) | árbol de `master` | Sin `Dockerfile` ni `docker-compose` |
| Releases del proyecto | `gh api repos/esig/dss/releases/latest` | `6.5.1` con **0 assets** |
| Maven Central | `repo1.maven.org/maven2/eu/europa/esig/` | 404 |
| Repositorio CEF Digital | `.../cefdigital/artifact/...` | Redirige a `digital-building-blocks/artifact` y devuelve 404 |

DSS se distribuye como *bundle* descargable (zip/tar.gz con Tomcat) o como código fuente; no hay
imagen publicada ni artefactos en un repositorio Maven accesible.

## Solución adoptada (Arquitecto, 2026-11-04)

La demo webapp se publica como imagen propia desde este repositorio:

- **Workflow:** `.github/workflows/dss-image.yml` (semanal, lunes 03:00 UTC, y `workflow_dispatch`).
- **Origen fijado por tag:** `esig/dss-demonstrations` en la versión `DSS_VERSION` (`6.5.1`); el
  commit upstream queda en las etiquetas de la imagen. La demo webapp **no** está en `esig/dss`
  desde la serie 6.x: vive en `esig/dss-demonstrations`.
- **Construcción:** Dockerfile del propio upstream (Tomcat + WAR), sin parches nuestros.
- **Publicación:** `ghcr.io/soluciones-alexendros/dss-demo-webapp:<versión>` y `:latest`, con
  `actions/attest-build-provenance` (procedencia verificable).
- **Consumo:** `interop` usa la variable de repositorio `DSS_IMAGE` **con el digest**, en la forma
  `ghcr.io/soluciones-alexendros/dss-demo-webapp@sha256:<digest>`. El digest lo imprime el propio
  workflow en su resumen.

## Modo aviso temporal y condición de salida

Mientras `vars.DSS_IMAGE` esté vacía, el paso A4 no bloquea el PR:

```yaml
continue-on-error: ${{ vars.DSS_IMAGE == '' }}
```

- **Condición de salida:** en cuanto exista la primera imagen publicada y `vars.DSS_IMAGE` tenga
  el digest, la expresión pasa a `false` y A4 vuelve a ser **bloqueante** sin tocar el workflow.
- El aviso se emite como anotación `::warning` en el run y consta en este documento.
- A3 (firma sintética) es un paso independiente: su resultado nunca queda oculto por A4.

## Confianza de la CA de prueba

La CA sintética del run no está en el truststore de DSS. Para obtener `TOTAL_PASSED` hay que
importarla (`keytool -importcert` sobre el truststore de DSS) o servírsela por la ruta que admita
la instancia. Sin ese paso, DSS devuelve `INDETERMINATE` con causa `NO_TRUST_ANCHOR`, que **no** es
un resultado válido para este gate.

Variables implicadas: `DSS_TRUSTSTORE` (ruta del truststore dentro del contenedor) y el secreto
`DSS_TRUSTSTORE_PASS`. Si el primer run con imagen real devuelve `NO_TRUST_ANCHOR`, el ajuste
pendiente es el punto de montaje/importación del truststore, no el resultado esperado.
