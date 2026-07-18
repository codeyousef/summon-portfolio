# State address changes

There are no prior OpenTofu resource addresses for this registry stack, so this
initial import intentionally contains no `moved` blocks.

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
