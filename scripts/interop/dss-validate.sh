#!/usr/bin/env bash
# T3b · A4: valida una firma con EU DSS por API REST y exige TOTAL_PASSED.
#
# Levanta la demo webapp oficial de DSS en Docker (si no hay una ya escuchando), envia la
# firma al endpoint REST de validacion y comprueba el resultado. La CA de prueba se usa
# como ancla de confianza.
#
# Uso: scripts/interop/dss-validate.sh <fichero-firmado> [ca.crt]
# Entorno:
#   DSS_URL          base de la webapp (por defecto http://127.0.0.1:8080)
#   DSS_IMAGE        imagen (por defecto nowina/dss-demo-webapp:latest)
#   DSS_TRUSTSTORE   ruta del truststore dentro del contenedor (si el operador la conoce)
set -euo pipefail

SIGNED="${1:-}"
CA_FILE="${2:-}"
DSS_URL="${DSS_URL:-http://127.0.0.1:8080}"
DSS_IMAGE="${DSS_IMAGE:-nowina/dss-demo-webapp:latest}"
OUT="${INTEROP_OUT:-interop-local}"

fail() { echo "FAIL: $*" >&2; exit 1; }
ok() { echo "OK: $*"; }

[ -n "$SIGNED" ] || fail "uso: dss-validate.sh <fichero-firmado> [ca.crt]"
[ -f "$SIGNED" ] || fail "no existe el fichero firmado: $SIGNED"
mkdir -p "$OUT"
command -v curl >/dev/null || fail "curl no disponible"

ENDPOINT="$DSS_URL/services/rest/validation/validateSignature"

started_container=""
cleanup() {
	if [ -n "$started_container" ]; then
		docker rm -f "$started_container" >/dev/null 2>&1 || true
	fi
}
trap cleanup EXIT

if ! curl -sf -o /dev/null "$DSS_URL/" 2>/dev/null; then
	command -v docker >/dev/null || fail "DSS no responde en $DSS_URL y no hay docker disponible"
	echo "[dss] levantando $DSS_IMAGE"
	started_container="$(docker run -d -p 8080:8080 "$DSS_IMAGE")"
	for _ in $(seq 1 60); do
		curl -sf -o /dev/null "$DSS_URL/" 2>/dev/null && break
		sleep 2
	done
	curl -sf -o /dev/null "$DSS_URL/" 2>/dev/null || fail "DSS no arranca en $DSS_URL"
fi

# Ancla de confianza: la CA de prueba debe estar en el truststore de DSS. El camino exacto
# depende de la imagen; se acepta explicitamente por entorno para no adivinarlo.
if [ -n "${DSS_TRUSTSTORE:-}" ] && [ -n "$CA_FILE" ] && [ -f "$CA_FILE" ]; then
	echo "[dss] importando la CA de prueba en $DSS_TRUSTSTORE"
	docker exec -i "$started_container" keytool -importcert -noprompt \
		-alias interop-test-ca -file /dev/stdin \
		-keystore "$DSS_TRUSTSTORE" -storepass "${DSS_TRUSTSTORE_PASS:?falta DSS_TRUSTSTORE_PASS}" \
		< "$CA_FILE" >/dev/null || fail "no se pudo importar la CA en el truststore de DSS"
else
	echo "[dss] AVISO: sin DSS_TRUSTSTORE la CA de prueba no se anade como confianza;"
	echo "[dss]        una firma con CA sintetica dara INDETERMINATE (NO_TRUST_ANCHOR)."
fi

B64="$(base64 -w0 "$SIGNED")"
REPORT="$OUT/dss-report.json"

# El endpoint REST de DSS recibe el documento en JSON (campo signedDocument.bytes).
python3 - "$B64" > /tmp/dss-request.json <<'PY'
import json, sys
print(json.dumps({"signedDocument": {"bytes": sys.argv[1]},
                  "tokenExtractionStrategy": "NONE"}))
PY

HTTP_CODE="$(curl -s -o "$REPORT" -w '%{http_code}' \
	-X POST "$ENDPOINT" \
	-H 'Content-Type: application/json' \
	-H 'Accept: application/json' \
	--data-binary @/tmp/dss-request.json)"

[ "$HTTP_CODE" = "200" ] || { cat "$REPORT" >&2; fail "DSS devolvio HTTP $HTTP_CODE"; }

INDICATION="$(python3 - "$REPORT" <<'PY'
import json, sys
data = json.load(open(sys.argv[1]))
report = data.get("SimpleReport") or data.get("simpleReport") or data
print(report.get("indication") or report.get("Indication") or "")
PY
)"

VALID_COUNT="$(python3 - "$REPORT" <<'PY'
import json, sys
data = json.load(open(sys.argv[1]))
report = data.get("SimpleReport") or data.get("simpleReport") or data
value = report.get("validSignaturesCount", report.get("ValidSignaturesCount", 0))
print(int(value or 0))
PY
)"

echo "[dss] indication=$INDICATION firmasValidas=$VALID_COUNT (informe: $REPORT)"
[ "$INDICATION" = "TOTAL_PASSED" ] || fail "DSS no devolvio TOTAL_PASSED (indication=$INDICATION)"
[ "$VALID_COUNT" -ge 1 ] || fail "DSS no contabiliza ninguna firma valida"
ok "DSS valida la firma con TOTAL_PASSED"
