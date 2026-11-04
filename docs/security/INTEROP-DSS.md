# Interoperabilidad con EU DSS (T3b · A4)

Estado: **bloqueado por despliegue**. La validación DSS está implementada y se ejecuta en cada
run, pero necesita una instancia de la demo webapp de DSS que hoy no está disponible en el CI.

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

## Cómo desbloquearlo (elige una)

1. **Instancia propia** ya levantada (recomendado para no alargar el job):
   definir la variable de repositorio `DSS_URL` (por ejemplo `http://dss.interno:8080`) y, si hace
   falta confianza, `DSS_TRUSTSTORE` + el secreto `DSS_TRUSTSTORE_PASS`.
2. **Imagen propia** de la demo webapp: publicarla en el registro de la organización y definir
   `vars.DSS_IMAGE`. El script la levanta en el puerto 8080 del runner.
3. **Construir DSS desde fuente** en un job aparte (no por PR): clonar `esig/dss` en el tag fijado,
   `mvn -P jar -pl dss-demo-webapp -am package -DskipTests`, arrancar el JAR y apuntar `DSS_URL`.
   Coste estimado: 10–20 min y cientos de MB de dependencias; razonable en un job nocturno.

Mientras no exista ninguna de las tres, el job `interop` queda **rojo en el paso A4** a propósito:
es la señal correcta, no un fallo a silenciar.

## Confianza de la CA de prueba

La CA sintética del run no está en el truststore de DSS. Para obtener `TOTAL_PASSED` hay que
importarla (`keytool -importcert` sobre el truststore de DSS, o la ruta que indique la instancia).
Sin ese paso, DSS devuelve `INDETERMINATE` con causa `NO_TRUST_ANCHOR`, lo cual **no** es un
resultado válido para este gate.
