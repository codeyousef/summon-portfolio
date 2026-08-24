#!/usr/bin/env bash
set -euo pipefail

readonly WRANGLER_VERSION="4.125.0"

exec npx --yes --package "wrangler@${WRANGLER_VERSION}" wrangler "$@"
