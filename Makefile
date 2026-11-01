# Fachada local del monorepo comunitario (Alexendros/clienteafirma).
# Contrato Maven P0 + capa Autofirma-2026 (vectores, gates, packaging).

COMMUNITY_SCRIPTS := $(wildcard scripts/*.sh)

.PHONY: help lint quality test test-community smoke build validate \
	review360 review360-r2 remediation360 security-material test-crypto test-accessibility

help:
	@printf '%s\n' \
	  'make lint              contract checks (Propósito, LICENSE, POM)' \
	  'make quality           lint + bash -n + shellcheck on scripts/*.sh' \
	  'make test              mvn -pl afirma-core test' \
	  'make test-community    invariantes capa comunitaria (vectores, docs)' \
	  'make smoke             inventario fork + smoke comunitario' \
	  'make build             package afirma-core (+deps)' \
	  'make validate          lint + quality + test + test-community + smoke' \
	  'make review360         verify CODE-REVIEW-360 findings' \
	  'make review360-r2      verify CODE-REVIEW-360-R2 findings' \
	  'make remediation360    verify post-audit mitigations' \
	  'make security-material detecta PFX operativos y contraseñas literales'

lint:
	bash scripts/ci/check-contract.sh

quality: lint
	@for f in $(COMMUNITY_SCRIPTS); do bash -n $$f && echo "OK $$f"; done
	@if command -v shellcheck >/dev/null 2>&1; then \
		shellcheck -S error -x $(COMMUNITY_SCRIPTS); \
	else \
		echo "shellcheck no instalado; omitido (CI lo instala)"; \
	fi

TEST_MODULES := afirma-core,afirma-core-keystores,afirma-core-massive,afirma-crypto-batch-client,afirma-crypto-cades,afirma-crypto-cades-multi,afirma-crypto-cadestri-client,afirma-crypto-cipher2,afirma-crypto-cms,afirma-crypto-core-pkcs7,afirma-crypto-core-pkcs7-tsp,afirma-crypto-core-xml,afirma-crypto-odf,afirma-crypto-ooxml,afirma-crypto-padestri-client,afirma-crypto-pdf,afirma-crypto-pdf-common,afirma-crypto-validation,afirma-crypto-xades,afirma-crypto-xadestri-client,afirma-crypto-xmlsignature

test:
	mvn -B -pl $(TEST_MODULES) -am test

test-crypto:
	cd tests/validation-harness && mvn -B test

test-accessibility:
	@if command -v pytest >/dev/null 2>&1 && command -v python3 >/dev/null 2>&1; then \
		cd tests/accessibility && python3 -m pytest src/test/python/orca_test_suite.py::TestAccessibilityCodeVerification -v; \
	else \
		echo "pytest/python3 not available; skipping accessibility code verification (run in CI)"; \
	fi

test-community:
	bash scripts/ci-community-test.sh

smoke:
	bash scripts/ci/smoke.sh
	bash scripts/ci-smoke.sh

build:
	mvn -B -pl afirma-core -am package -DskipTests

validate: lint quality test test-community smoke

validate-full: validate test-crypto test-accessibility

review360:
	bash scripts/verify-code-review-360.sh

review360-r2:
	bash scripts/verify-code-review-360-r2.sh

remediation360:
	bash scripts/verify-remediation-360.sh

security-material:
	bash scripts/ci/test-detect-crypto-material.sh
	bash scripts/ci/detect-crypto-material.sh
