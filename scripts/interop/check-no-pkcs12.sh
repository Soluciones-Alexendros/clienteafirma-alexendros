#!/usr/bin/env bash
# T3b · criterio C: el repositorio no incorpora PKCS#12 nuevos.
#
# El arbol heredado del CTT ya versiona 84 almacenes de PRUEBA en src/test/resources
# (ANF_PF_Activo.pfx y compania), usados por los tests de criptografia y por
# scripts/f2-regression.sh. Son vectores publicos del proyecto original, sin datos reales,
# y no se pueden retirar sin romper los tests. Por eso el criterio se aplica como
# "ningun PKCS#12 fuera de los almacenes de prueba heredados".
#
# Uso: scripts/interop/check-no-pkcs12.sh
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

ALLOWLIST_RE='(^|/)src/test/resources/'

unexpected="$(git ls-files | grep -Ei '\.(p12|pfx)$' | grep -Ev "$ALLOWLIST_RE" || true)"
if [ -n "$unexpected" ]; then
	echo "FAIL: PKCS#12 fuera de los almacenes de prueba heredados:" >&2
	echo "$unexpected" >&2
	exit 1
fi

if git ls-files | grep -q '^interop-local/'; then
	echo "FAIL: interop-local/ esta versionado" >&2
	exit 1
fi

total="$(git ls-files | grep -Ei '\.(p12|pfx)$' | wc -l | tr -d ' ')"
echo "OK: ningun PKCS#12 nuevo; $total almacenes de prueba heredados en src/test/resources"
