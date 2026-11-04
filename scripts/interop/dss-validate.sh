#!/usr/bin/env bash
# T3b · A4: valida una firma con el validador propio sobre EU DSS y exige TOTAL_PASSED.
#
# El servicio (tests/interop-dss) recibe la firma y la CA del run en la propia peticion:
# no hay truststore montado, ni LOTL, ni secretos, ni red desde el contenedor.
#
# Uso: scripts/interop/dss-validate.sh <fichero-firmado> <ca.crt> [documento-original]
# Entorno:
#   DSS_URL    base del validador ya escuchando (por defecto http://127.0.0.1:8080)
#   DSS_IMAGE  imagen a levantar si no hay servicio (por digest)
set -euo pipefail

SIGNED="${1:-}"
CA_FILE="${2:-}"
CONTENT="${3:-}"
DSS_URL="${DSS_URL:-http://127.0.0.1:8080}"
DSS_IMAGE="${DSS_IMAGE:-}"
OUT="${INTEROP_OUT:-interop-local}"

fail() { echo "FAIL: $*" >&2; exit 1; }
ok() { echo "OK: $*"; }

[ -n "$SIGNED" ] || fail "uso: dss-validate.sh <fichero-firmado> [ca.crt]"
[ -f "$SIGNED" ] || fail "no existe el fichero firmado: $SIGNED"
[ -n "$CA_FILE" ] && [ -f "$CA_FILE" ] || fail "falta la CA del run: $CA_FILE"
if [ -n "$CONTENT" ] && [ ! -f "$CONTENT" ]; then
	fail "no existe el documento original indicado: $CONTENT"
fi
mkdir -p "$OUT"
command -v curl >/dev/null || fail "curl no disponible"

started_container=""
cleanup() {
	if [ -n "$started_container" ]; then
		docker rm -f "$started_container" >/dev/null 2>&1 || true
	fi
}
trap cleanup EXIT

healthy() {
	curl -sf -o /dev/null --max-time 3 "$DSS_URL/health" 2>/dev/null
}

if ! healthy; then
	command -v docker >/dev/null || fail "no hay validador en $DSS_URL y no hay docker disponible"
	[ -n "$DSS_IMAGE" ] || fail "no hay validador en $DSS_URL. Indica DSS_URL o DSS_IMAGE (por digest). Ver docs/security/INTEROP-DSS.md"
	case "$DSS_IMAGE" in
		*@sha256:*) : ;;
		*) echo "[dss] AVISO: DSS_IMAGE no esta fijada por digest (@sha256:...); se recomienda el digest" >&2 ;;
	esac
	echo "[dss] levantando $DSS_IMAGE (solo lectura, sin red salvo el puerto 8080)"
	started_container="$(docker run -d --rm \
		--name interop-dss-validator \
		--read-only --tmpfs /tmp:rw,noexec,nosuid,size=64m \
		--cap-drop ALL --security-opt no-new-privileges \
		-p 8080:8080 "$DSS_IMAGE")" || fail "no se pudo arrancar el validador"
	for _ in $(seq 1 60); do
		healthy && break
		sleep 2
	done
	healthy || { docker logs --tail 40 "$started_container" >&2 || true; fail "el validador no responde en $DSS_URL/health"; }
fi

B64="$(base64 -w0 "$SIGNED")"
CA_B64="$(openssl x509 -in "$CA_FILE" -outform DER 2>/dev/null | base64 -w0)"
[ -n "$CA_B64" ] || fail "no se pudo convertir la CA a DER/base64: $CA_FILE"

BASE="$(basename "$SIGNED")"
REPORT="$OUT/dss-report-${BASE}.json"
REQUEST="$OUT/dss-request-${BASE}.json"

# Sin registrar contenido: solo hashes en consola.
echo "[dss] firma sha256=$(sha256sum "$SIGNED" | cut -d' ' -f1) ca_sha256=$(sha256sum "$CA_FILE" | cut -d' ' -f1)"
if [ -n "$CONTENT" ]; then
	echo "[dss] documento original sha256=$(sha256sum "$CONTENT" | cut -d' ' -f1)"
fi

CONTENT_B64=""
if [ -n "$CONTENT" ]; then
	CONTENT_B64="$(base64 -w0 "$CONTENT")"
fi

python3 - "$B64" "$CA_B64" "$CONTENT_B64" > "$REQUEST" <<'PY'
import json, sys
payload = {"signature": sys.argv[1], "ca": sys.argv[2]}
if sys.argv[3]:
    payload["content"] = sys.argv[3]
print(json.dumps(payload))
PY

HTTP_CODE="$(curl -s -o "$REPORT" -w '%{http_code}' \
	-X POST "$DSS_URL/validate" \
	-H 'Content-Type: application/json' \
	-H 'Accept: application/json' \
	--data-binary "@$REQUEST")"

if [ "$HTTP_CODE" != "200" ]; then
	echo "[dss] respuesta del servicio (HTTP $HTTP_CODE):" >&2
	head -c 4000 "$REPORT" >&2; echo >&2
	fail "el validador devolvio HTTP $HTTP_CODE"
fi

read -r INDICATION SUB_FORMAT VALID_COUNT <<<"$(python3 - "$REPORT" <<'PY'
import json, sys
data = json.load(open(sys.argv[1]))
sub = data.get("subIndication") or "-"
fmt = data.get("signatureFormat") or "-"
print(data.get("indication", ""), f"{sub}|{fmt}", data.get("validSignaturesCount", 0))
PY
)"

echo "[dss] indication=$INDICATION detalle=$SUB_FORMAT firmasValidas=$VALID_COUNT (informe: $REPORT)"
if [ "$INDICATION" != "TOTAL_PASSED" ]; then
	head -c 2000 "$REPORT" >&2; echo >&2
	fail "el validador no devolvio TOTAL_PASSED (indication=$INDICATION)"
fi
[ "${VALID_COUNT:-0}" -ge 1 ] || fail "no se contabiliza ninguna firma valida"
ok "EU DSS valida la firma con TOTAL_PASSED"
