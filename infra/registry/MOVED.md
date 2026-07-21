# State address changes

There are no prior OpenTofu resource addresses for the reusable registry stack,
so its initial import intentionally contains no `moved` blocks.

The production bootstrap recovery deliberately does not move the existing
organization custom-role addresses. The obsolete
`google_organization_iam_custom_role.billing_refresh` and
`google_organization_iam_custom_role.control_project_refresh` resources remain
managed, deletion-protected, and unbound. The active billing grant now uses
predefined `roles/billing.viewer`, while the parentless control project uses the
new `google_project_iam_custom_role.control_project_refresh` resource. An
organization role and a project role are different cloud resources, so a
`moved` block between them would assert a false identity and is forbidden.

Do not delete the retained organization roles during recovery. Their eventual
removal is destructive cleanup and requires a separate complete plan and
explicit approval.

After state exists, never rename a module key, resource, environment root, or
`for_each` key without adding an explicit `moved` block in the same change. For
example:

```hcl
moved {
  from = module.registry.google_cloud_run_v2_job.long_lived["old_name"]
  to   = module.registry.google_cloud_run_v2_job.long_lived["new_name"]
}
```

Validate the move against a copied state first. Keep the block for at least one
full apply cycle in every affected environment. A `moved` block is not a
substitute for importing an existing resource that has never been in state.

The state-bucket IAM recovery preserves both managed
`google_storage_bucket_iam_policy.state` addresses. Do not forget, import,
rename, or move either address as part of recovery. The narrow access plan and
the complete reconciliation plan are described in
[README.md](README.md#partial-state-bucket-iam-recovery).

Recovery leaves the existing protected
`google_project_iam_custom_role.state_reader[0]` permission set unchanged. The
new two-permission
`google_project_iam_custom_role.state_bucket_policy_reader[0]` has no
predecessor and must not receive a `moved` block. The two
`google_project_iam_member.temporary_human_state_bucket_policy_read_access`
instances are new. The two
`google_project_iam_member.temporary_human_state_bucket_policy_access`
instances remain the exact setter-binding addresses. The five-change recovery
and later complete four-binding cleanup must use these addresses exactly; never
replace either operation with state forgetting or an address move.

The four immutable phase-evidence resources are also new and have no
predecessors:

- `terraform_data.recovery_reconciliation_record`;
- `terraform_data.project_creator_owner_adoption_record`;
- `terraform_data.recovery_cleanup_record`;
- `terraform_data.project_creator_owner_removal_record`.

Do not add `moved` blocks for them. Each valid value must originate in its own
applied phase and remain at the same deletion-protected address through handoff
and steady state. A new address or same-plan replacement is not equivalent to
prior-state evidence.
