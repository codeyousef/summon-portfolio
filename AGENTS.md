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

- Approval to implement or review infrastructure code does not authorize a
  cloud plan or apply. Require separate, explicit authorization for the exact
  cloud phase before changing external state.
- Production, IAM, Cloud Run job, and scheduler changes require explicit
  approval and one complete reviewed saved plan. Apply only that exact plan.
- Keep production planning and applying on separate protected OIDC identities,
  and never expose a saved plan or rendered plan through public logs or a
  plaintext artifact.
- Treat protected review of the exact saved plan as the production apply
  security boundary. The apply identity has no direct standing data or signing
  role, but approved infrastructure changes can bind allowlisted runtime roles
  and deploy code as the finite pre-provisioned runtime service accounts.
- Keep routine application and privileged signer image digests immutable and
  separately reviewed.
- Treat temporary plan-reader IAM as an approval-scoped lease. Remove its
  binding, delete its custom role, and verify both removals before reporting
  completion.

## Production deployment events

- When merging to `master` must trigger downstream production workflows, use a
  user-authenticated Git or GitHub web merge path. In this repository,
  app/connector merges have suppressed downstream workflow events.
- After the merge, verify that `master` points to the exact reviewed merge SHA
  and that the expected workflow run was created for that SHA before reporting
  the production-triggering merge complete.
