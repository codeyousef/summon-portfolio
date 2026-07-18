# Repository instructions

## Private task context

- Use the linked Linear issue and its latest comments as the canonical private
  handoff context. Do not add private planning, rationale, blockers, or progress
  files to this public repository.

## Builds

- Before a Gradle build or test, inspect current available memory and pass an
  explicit heap/metaspace cap, `--no-daemon`, and at most two workers. Use a
  4 GiB heap and 768 MiB metaspace only when current availability safely permits
  those limits; lower them otherwise.

## Local and sensitive artifacts

- Keep generated build/test output and agent-local state in ignored paths; do
  not stage it.
- Never stage, force-add, or print cloud credentials, copied SDK
  configurations, backend files, non-example variable files, state, saved or
  rendered plans, offline signing material, or ceremony inputs. Verify that
  generated infrastructure files are ignored before committing.

## Infrastructure changes

- Production, IAM, Cloud Run job, and scheduler changes require explicit
  approval and one complete reviewed saved plan. Apply only that exact plan.
- Keep routine application and privileged signer image digests immutable and
  separately reviewed.
- Treat temporary plan-reader IAM as an approval-scoped lease. Remove its
  binding, delete its custom role, and verify both removals before reporting
  completion.
