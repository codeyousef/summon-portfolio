#!/usr/bin/env bash
# Show a saved plan created by the "Plan and apply AWS infrastructure"
# workflow, after checking that it is the exact object the run reported.
# Usage: review-saved-plan.sh <dev|prod> <plans/...tfplan> <sha256>
# Run with operator AWS credentials in the environment. The downloaded plan
# stays in an ignored, owner-only directory and is removed on exit.
set -euo pipefail

target="$1"
key="$2"
digest="$3"
bucket="yousef-portfolio-tofu-state"

[[ "${target}" == "dev" || "${target}" == "prod" ]] || { echo "environment must be dev or prod" >&2; exit 1; }
[[ "${key}" == "plans/${target}/"*".tfplan" ]] || { echo "plan key does not belong to ${target}" >&2; exit 1; }
[[ "${digest}" =~ ^[0-9a-f]{64}$ ]] || { echo "sha256 must be 64 lowercase hex characters" >&2; exit 1; }

root="$(cd "$(dirname "$0")/../environments/${target}" && pwd)"
[[ -f "${root}/backend.hcl" ]] || cp "${root}/backend.hcl.example" "${root}/backend.hcl"

umask 077
plan="${root}/review.tfplan"
trap 'rm -f "${plan}"' EXIT

aws s3api get-object --bucket "${bucket}" --key "${key}" --region eu-north-1 "${plan}" > /dev/null
echo "${digest}  ${plan}" | sha256sum --check --quiet
tofu -chdir="${root}" init -input=false -backend-config=backend.hcl > /dev/null
tofu -chdir="${root}" show review.tfplan
