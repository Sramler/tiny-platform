#!/usr/bin/env bash
set -euo pipefail

MODE="${1:-default}"
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TREE_FILE="$(mktemp "${TMPDIR:-/tmp}/tiny-camunda-deps.XXXXXX")"
trap 'rm -f "${TREE_FILE}"' EXIT

case "${MODE}" in
  default)
    MAVEN_PROFILE=""
    ;;
  rest)
    MAVEN_PROFILE="-Pcamunda-rest"
    ;;
  *)
    echo "usage: $0 [default|rest]" >&2
    exit 2
    ;;
esac

cd "${ROOT_DIR}"

if grep -REn --include='*.java' \
    'com\.fasterxml\.jackson\.(core|databind|dataformat|datatype)' \
    tiny-oauth-server/src/main tiny-oauth-server/src/test; then
  echo 'FAIL: first-party source still depends on an unshaded Jackson 2 API' >&2
  echo 'NOTE: com.fasterxml.jackson.annotation remains the Jackson 3 annotation namespace and is allowed' >&2
  exit 1
fi

if [[ -n "${MAVEN_PROFILE}" ]]; then
  mvn -q -pl tiny-oauth-server "${MAVEN_PROFILE}" dependency:tree \
    -Dscope=runtime \
    -DoutputType=text \
    -DoutputFile="${TREE_FILE}"
else
  mvn -q -pl tiny-oauth-server dependency:tree \
    -Dscope=runtime \
    -DoutputType=text \
    -DoutputFile="${TREE_FILE}"
fi

fail_if_present() {
  local pattern="$1"
  local reason="$2"
  if grep -Eq "${pattern}" "${TREE_FILE}"; then
    echo "FAIL: ${reason}" >&2
    grep -E "${pattern}" "${TREE_FILE}" >&2 || true
    exit 1
  fi
}

if [[ "${MODE}" == "default" ]]; then
  fail_if_present 'org\.camunda\.bpm\.springboot:camunda-bpm-spring-boot-starter-rest:' \
    'default Engine Only runtime must not contain the Camunda REST starter'
  fail_if_present 'org\.springframework\.boot:spring-boot-jackson2:|org\.glassfish\.jersey\.media:jersey-media-json-jackson:' \
    'default Engine Only runtime must not contain the Jersey/Jackson 2 compatibility bridge'
  fail_if_present 'com\.fasterxml\.jackson\.core:(jackson-core|jackson-databind):|com\.fasterxml\.jackson\.(dataformat|datatype):' \
    'default runtime must not contain an unshaded Jackson 2 implementation dependency'
  echo "PASS: default Camunda runtime is Engine Only"
  exit 0
fi

if ! grep -Eq 'org\.camunda\.bpm\.springboot:camunda-bpm-spring-boot-starter-rest:' "${TREE_FILE}"; then
  echo 'FAIL: camunda-rest profile did not add the Camunda REST starter' >&2
  exit 1
fi

if ! grep -Eq 'tools\.jackson\.jakarta\.rs:jackson-jakarta-rs-json-provider:' "${TREE_FILE}"; then
  echo 'FAIL: Camunda REST runtime does not contain the Jackson 3 Jakarta REST provider' >&2
  exit 1
fi

fail_if_present 'org\.springframework\.boot:spring-boot-jackson2:|org\.glassfish\.jersey\.media:jersey-media-json-jackson:|com\.fasterxml\.jackson\.core:(jackson-core|jackson-databind):|com\.fasterxml\.jackson\.(dataformat|datatype):' \
  'Camunda REST Jackson 3 runtime still contains an unshaded Jackson 2 dependency'

echo "PASS: Camunda REST runtime uses the Jackson 3 provider without an unshaded Jackson 2 bridge"
