#!/usr/bin/env bash
# Local fixture checks for scripts/ci/semgrep-new-high-gate.py (#89).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
GATE="$ROOT/scripts/ci/semgrep-new-high-gate.py"
FIX="$ROOT/scripts/ci/fixtures/semgrep-gate"

python3 "$GATE" --sarif "$FIX/warning-only.sarif" --baseline "$FIX/baseline.sarif"
echo "OK: warning-only passes"

if python3 "$GATE" --sarif "$FIX/new-high.sarif" --baseline "$FIX/baseline.sarif"; then
  echo "FAIL: new-high should fail" >&2
  exit 1
fi
echo "OK: new-high fails"

python3 "$GATE" --sarif "$FIX/baseline.sarif" --baseline "$FIX/baseline.sarif"
echo "OK: same as baseline passes"

echo "test-semgrep-new-high-gate: all OK"
