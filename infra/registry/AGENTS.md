# Seen registry infrastructure instructions

- Produce and inspect one complete saved OpenTofu plan before every apply, then
  apply only that exact reviewed plan. Targeted operations require separate
  approval and an immediate complete reconciliation plan.
- Treat schedule creation, enablement, unpausing, cadence/target/IAM changes,
  and manual schedule execution as separately reviewable cloud mutations.
  Approval must identify every affected schedule.
- Keep newly created schedules paused until their targets, caller identity,
  retry policy, and one scheduler-originated execution have been verified. Do
  not infer schedule activation from approval to deploy workloads.
- Omit an all-default Cloud Scheduler `retry_config`; the API omits
  `retry_count = 0`, so declaring it creates perpetual drift.
- Temporary cloud access is an approval-scoped lease. On success or failure,
  remove its binding, delete its custom role, remove copied local
  credentials/configuration, and verify cleanup before reporting completion.
- Do not use `registry-service/scripts/provision-development-owner.sh` to
  create or update schedules once OpenTofu owns the environment.
