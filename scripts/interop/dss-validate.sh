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
# No se inventa una imagen: no existe publicacion oficial de la demo webapp de DSS.
# Hay que indicarla por entorno (DSS_IMAGE) o apuntar a una instancia ya levantada (DSS_URL).
DSS_IMAGE="${DSS_IMAGE:-}"
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
	[ -n "$DSS_IMAGE" ] || fail "no hay DSS accesible en $DSS_URL. Indica DSS_URL (instancia existente) o DSS_IMAGE (imagen de la demo webapp). No existe imagen oficial publicada de DSS: ver docs/security/INTEROP-DSS.md"
	case "$DSS_IMAGE" in
		*@sha256:*) : ;;
		*) echo "[dss] AVISO: DSS_IMAGE no esta fijada por digest (@sha256:...); se recomienda el digest" >&2 ;;
	esac
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
REQUEST="$OUT/dss-request.json"

# La CA sintetica del run viaja en la propia peticion como ancla de confianza, para no
# depender de un truststore montado ni de secretos.
CA_B64=""
if [ -n "$CA_FILE" ] && [ -f "$CA_FILE" ]; then
	CA_B64="$(openssl x509 -in "$CA_FILE" -outform DER 2>/dev/null | base64 -w0)"
	[ -n "$CA_B64" ] || fail "no se pudo convertir la CA a DER/base64: $CA_FILE"
fi

python3 - "$B64" "$CA_B64" > "$REQUEST" <<'PY'
import json, sys
payload = {
    "signedDocument": {"bytes": sys.argv[1]},
    "tokenExtractionStrategy": "NONE",
}
if sys.argv[2]:
    # Forma esperada por DSS para un certificado en REST.
    payload["trustedCertificates"] = [{"encodedCertificate": sys.argv[2]}]
print(json.dumps(payload))
PY

HTTP_CODE="$(curl -s -o "$REPORT" -w '%{http_code}' \
	-X POST "$ENDPOINT" \
	-H 'Content-Type: application/json' \
	-H 'Accept: application/json' \
	--data-binary "@$REQUEST")"

if [ "$HTTP_CODE" != "200" ]; then
	echo "[dss] peticion enviada:" >&2
	head -c 400 "$REQUEST" >&2; echo >&2
	echo "[dss] respuesta del servicio (HTTP $HTTP_CODE):" >&2
	head -c 4000 "$REPORT" >&2; echo >&2
	fail "DSS devolvio HTTP $HTTP_CODE"
fi

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
if [ "$INDICATION" != "TOTAL_PASSED" ]; then
	echo "[dss] respuesta del servicio:" >&2
	head -c 4000 "$REPORT" >&2; echo >&2
fi
[ "$INDICATION" = "TOTAL_PASSED" ] || fail "DSS no devolvio TOTAL_PASSED (indication=$INDICATION)"
[ "$VALID_COUNT" -ge 1 ] || fail "DSS no contabiliza ninguna firma valida"
ok "DSS valida la firma con TOTAL_PASSED"
