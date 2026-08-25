#!/usr/bin/env bash

set -euo pipefail

readonly SEEN_VERSION="0.11.1"
readonly SEEN_ARCHIVE="seen-${SEEN_VERSION}-linux-x64.tar.gz"
readonly SEEN_SHA256="9d6c6efadb5f0df934953e710b5aee5b6c31c26b61f48c4330b48076591adc6a"
readonly SEEN_URL="https://github.com/codeyousef/SeenLang/releases/download/v${SEEN_VERSION}/${SEEN_ARCHIVE}"

readonly PORTFOLIO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
readonly TOOLS_DIR="${PORTFOLIO_ROOT}/seen-tools"
readonly WORK_DIR="${RUNNER_TEMP:-/tmp}/portfolio-seen-${SEEN_VERSION}"
readonly ARCHIVE_PATH="${WORK_DIR}/${SEEN_ARCHIVE}"
readonly RELEASE_ROOT="${WORK_DIR}/seen-${SEEN_VERSION}-linux-x64"

if [[ -e "${TOOLS_DIR}" ]]; then
  echo "Refusing to replace existing ${TOOLS_DIR}; use a clean checkout." >&2
  exit 1
fi

mkdir -p "${WORK_DIR}"
curl --fail --location --silent --show-error "${SEEN_URL}" --output "${ARCHIVE_PATH}"
printf '%s  %s\n' "${SEEN_SHA256}" "${ARCHIVE_PATH}" | sha256sum --check --strict
tar --extract --gzip --file "${ARCHIVE_PATH}" --directory "${WORK_DIR}"

test -x "${RELEASE_ROOT}/bin/seen"
test -d "${RELEASE_ROOT}/lib/seen/runtime"
test -d "${RELEASE_ROOT}/share/seen/languages"

mkdir "${TOOLS_DIR}"
install -m 0755 "${RELEASE_ROOT}/bin/seen" "${TOOLS_DIR}/seen"
cp -R "${RELEASE_ROOT}/lib/seen/runtime" "${TOOLS_DIR}/seen_runtime"
cp -R "${RELEASE_ROOT}/share/seen/languages" "${TOOLS_DIR}/languages"

"${TOOLS_DIR}/seen" --version
