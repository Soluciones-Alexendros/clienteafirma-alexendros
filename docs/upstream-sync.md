# Sincronización con el oficial (upstream)

- Fecha de integración: 2026-10-31
- Último commit integrado del oficial: bd9254be70004be095b1251b5a4af15b9d392dd9
- Enlace: https://github.com/ctt-gob-es/clienteafirma/commit/bd9254be70004be095b1251b5a4af15b9d392dd9
- Título: Corregir descripción paquete DEB
- Cambios integrados:
  1. Corregir el campo Description duplicado en el control del paquete DEB.
  2. Descartar entradas de clave simétrica (SecretKeyEntry) de los almacenes NSS.
  3. Corregir descripción paquete DEB.
- Diferencias deliberadas: 1 (ver abajo). Los otros 3 ficheros son idénticos al oficial.

- Diferencia deliberada en `afirma-core-keystores/src/main/java/es/gob/afirma/keystores/KeyStoreUtilities.java`: endurecimiento frente a inyección de comandos.
  - Qué cambia: se evita la concatenación de rutas en el nombre corto de Windows (metacaracteres) y se usa una variable de entorno en lugar de concatenar la ruta al comando.
  - Por qué: prevenir inyección de comandos al invocar utilidades del almacén NSS.
  - PR/commit que lo introdujo: https://github.com/Soluciones-Alexendros/clienteafirma-alexendros/commit/d22ca8c01
  - Cómo comprobarlo: `git diff upstream/master origin/master -- afirma-core-keystores/src/main/java/es/gob/afirma/keystores/KeyStoreUtilities.java` debe mostrar solo ese bloque.
  Se conserva la versión del fork por seguridad.
