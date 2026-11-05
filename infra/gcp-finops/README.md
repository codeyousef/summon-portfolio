# Dev GCP FinOps foundation

This root owns only the dev keyless identity and bounded BigQuery source for
the Felidai Studio spending dashboard. It cannot target production and creates
nothing unless `resources_enabled = true` in an ignored `terraform.tfvars`.

The enabled shape creates:

- a US `billing_export` dataset with destruction protection, plus an optional
  existing standard-export source dataset for preserving billing history;
- a `finops-reader` service account;
- the `cloudflare-dev/access-dev` OIDC WIF pool/provider;
- an exact subject binding for one Cloudflare Access service-token client ID;
- project-scoped `roles/bigquery.jobUser`; and
- dataset-scoped `roles/bigquery.dataViewer`.

It creates no service-account key and grants no Firestore business-data role,
billing mutation role, or project-wide data-viewer role. The Access issuer and
application audience are validated by the OIDC provider, while the attribute
condition also requires the exact service-token `common_name`, application
token type, and empty subject.

When standard export is already enabled, set `billing_source_dataset_id` to its
existing US dataset and leave the account-level export untouched. The reader
receives dataset-scoped access only to that source. The separately managed
`billing_export` dataset remains reserved for a future reviewed cutover.

The Cloud Billing standard export switch is intentionally not represented by
a Terraform resource because Google does not provide a stable resource for
that account-level setting. After applying the exact reviewed dev plan, enable
the **standard usage cost** export for the selected billing account into the
created dataset. Google will create the
`gcp_billing_export_v1_<BILLING_ACCOUNT_ID>` table and configure its export
service identity. Do not enable detailed or pricing exports for this phase.

Use the same private R2 state bucket as the Cloudflare roots with the isolated
`state/gcp-finops-dev.tfstate` key. Plans stay in `/tmp`, are never rendered or
uploaded, and are deleted after apply or rejection.

Offline verification:

```sh
tofu fmt -check -recursive infra/gcp-finops
cd infra/gcp-finops/dev && tofu test
```
