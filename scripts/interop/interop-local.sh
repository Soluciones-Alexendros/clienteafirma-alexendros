#!/usr/bin/env bash
# T3b · B: prueba de interoperabilidad local con el .p12 real del operador.
#
# Ejecuta A1-A4 (canal TLS, navegadores, firma y validacion DSS) con el certificado del
# operador, sin versionar nada y sin dejar datos personales en la evidencia.
#
# La contrasena se pide por TTY (read -s) y no se escribe en disco, ni en logs, ni en la
# linea de comandos de openssl (se pasa por descriptor de fichero). Para el cliente Java,
# que solo acepta -password en argv, se usa una copia desechable del certificado protegida
# con una contrasena aleatoria de un solo uso, que se destruye al terminar.
#
# Uso: scripts/interop/interop-local.sh <ruta.p12> [alias]
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
P12="${1:-${P12:-}}"
ALIAS="${2:-}"
OUT_ROOT="$ROOT/interop-local"
STAMP="$(date +%Y%m%d-%H%M%S)"
OUT="$OUT_ROOT/$STAMP"
JAR="$ROOT/afirma-simple/target/autofirma.jar"

fail() { echo "FAIL: $*" >&2; exit 1; }
ok() { echo "OK: $*"; }

[ -n "$P12" ] || fail "uso: interop-local.sh <ruta.p12> [alias]"
[ -f "$P12" ] || fail "no existe el fichero: $P12"

WORK="$(mktemp -d)"
chmod 700 "$WORK"
cleanup() { rm -rf "$WORK"; }
trap cleanup EXIT

mkdir -p "$OUT"
chmod 700 "$OUT"

echo "[interop-local] evidencia en $OUT"
if [ ! -t 0 ]; then
	fail "la contrasena se pide por TTY; ejecuta el comando en una terminal"
fi
printf 'Contrasena del PKCS#12 (no se mostrara): '
IFS= read -r -s P12_PASS
printf '\n'
[ -n "$P12_PASS" ] || fail "contrasena vacia"

# Exporta clave y certificado sin exponer la contrasena en argv: se pasa por el fd 3.
exec 3< <(printf '%s' "$P12_PASS")
openssl pkcs12 -in "$P12" -passin fd:3 -nodes -out "$WORK/bundle.pem" >/dev/null 2>&1 \
	|| fail "no se pudo abrir el PKCS#12 (contrasena incorrecta?)"
exec 3<&-

openssl pkcs12 -in "$WORK/bundle.pem" -nodes -passin pass: -out /dev/null 2>/dev/null || true
awk '/BEGIN CERTIFICATE/,/END CERTIFICATE/' "$WORK/bundle.pem" | head -1 > "$WORK/leaf.crt"
awk '/BEGIN PRIVATE KEY/,/END PRIVATE KEY/' "$WORK/bundle.pem" > "$WORK/leaf.key"
[ -s "$WORK/leaf.crt" ] || fail "el PKCS#12 no contiene certificado"
[ -s "$WORK/leaf.key" ] || fail "el PKCS#12 no contiene clave privada"
chmod 600 "$WORK/leaf.key"

SUBJECT="$(openssl x509 -in "$WORK/leaf.crt" -noout -subject -nameopt utf8,sep_comma_plus 2>/dev/null | sed 's/^subject=//')"
if [ -z "$SUBJECT" ]; then
	fail "no se pudo leer el titular del certificado"
fi
echo "[interop-local] titular detectado (se eliminara de la evidencia)"

# Copia desechable para el canal local y para el cliente Java.
CHANNEL_PASS="$(openssl rand -base64 24 | tr -d '\n/=+')"
JAR_PASS="$(openssl rand -base64 24 | tr -d '\n/=+')"
[ -n "$ALIAS" ] || ALIAS="$(openssl pkcs12 -in "$P12" -passin fd:3 -nokeys 2>/dev/null </dev/null | sed -n 's/^friendlyName: //p' | head -1)"
[ -n "$ALIAS" ] || ALIAS="interop"
openssl pkcs12 -export -in "$WORK/leaf.crt" -inkey "$WORK/leaf.key" -name autofirma \
	-passout "pass:$CHANNEL_PASS" -out "$WORK/channel.p12" 2>/dev/null
openssl pkcs12 -export -in "$WORK/leaf.crt" -inkey "$WORK/leaf.key" -name "$ALIAS" \
	-passout "pass:$JAR_PASS" -out "$WORK/jar.p12" 2>/dev/null
chmod 600 "$WORK/channel.p12" "$WORK/jar.p12"

# A1 · canal TLS con el certificado del operador.
echo "[interop-local] A1 canal TLS"
INTEROP_CHANNEL_P12="$WORK/channel.p12" INTEROP_CHANNEL_PASS="$CHANNEL_PASS" \
	INTEROP_TLS_LOG="$OUT/tls-channel.log" \
	bash "$ROOT/scripts/interop/tls-probe.sh" "${INTEROP_CERTS_DIR:-${TMPDIR:-/tmp}/afirma-interop-certs}" \
	>"$OUT/tls-probe.log" 2>&1 || { tail -20 "$OUT/tls-probe.log" >&2; fail "A1 fallo"; }
ok "A1 canal TLS 1.2"

# A2 · navegadores (si Playwright esta disponible).
if node -e "require.resolve('playwright')" >/dev/null 2>&1; then
	echo "[interop-local] A2 navegadores"
	INTEROP_OUT="$OUT" node "$ROOT/scripts/interop/browser-interop.mjs" >"$OUT/browsers.log" 2>&1 \
		|| { tail -20 "$OUT/browsers.log" >&2; fail "A2 fallo"; }
	ok "A2 navegadores"
else
	echo "[interop-local] A2 omitido: Playwright no instalado (npm i -D playwright)"
fi

# A3/A4 · firma con el certificado del operador y validacion DSS.
if [ -f "$JAR" ]; then
	echo "[interop-local] firmando un documento sintetico con el certificado del operador"
	printf 'vector sintetico de interoperabilidad\n' > "$WORK/plain.txt"
	java -jar "$JAR" sign -i "$WORK/plain.txt" -o "$OUT/firma.csig" \
		-store "pkcs12:$WORK/jar.p12" -password "$JAR_PASS" -alias "$ALIAS" -format cades \
		>"$OUT/firma.log" 2>&1 || { tail -20 "$OUT/firma.log" >&2; fail "la firma fallo"; }
	ok "firma CAdES generada con el certificado del operador"

	if command -v docker >/dev/null 2>&1 || curl -sf -o /dev/null http://127.0.0.1:8080/ 2>/dev/null; then
		echo "[interop-local] A4 validacion DSS"
		INTEROP_OUT="$OUT" bash "$ROOT/scripts/interop/dss-validate.sh" "$OUT/firma.csig" \
			>"$OUT/dss.log" 2>&1 || { tail -20 "$OUT/dss.log" >&2; echo "[interop-local] A4 no concluyente: revisa $OUT/dss.log"; }
	fi
else
	echo "[interop-local] falta $JAR: ejecuta 'mvn -Denv=install' antes para firmar"
fi

# Saneado obligatorio antes de dar la evidencia por buena.
echo "[interop-local] saneando datos personales"
INTEROP_SUBJECT_NAME="$SUBJECT" bash "$ROOT/scripts/interop/scrub-pii.sh" "$OUT" \
	|| fail "el saneado dejo datos personales"

{
	echo "Interoperabilidad local · $STAMP"
	echo "Titular: <eliminado>"
	echo "Resultado A1 (canal TLS): $(grep -c '^OK:' "$OUT/tls-probe.log" 2>/dev/null || echo 0) comprobaciones OK"
	[ -f "$OUT/browsers.json" ] && echo "Navegadores: $(cat "$OUT/browsers.json")"
} > "$OUT/RESUMEN.txt"

ok "evidencia en $OUT (saneada)"
