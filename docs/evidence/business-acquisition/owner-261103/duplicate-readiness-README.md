# Source-identity review, no automatic merges

The unchanged2,448-row geographic research corpus contains five pairs with the
same normalized source name, base phone, extension and nonempty address, but
different source node IDs. Approximate spherical distance between source points
puts three pairs within5m, one at12.029m and one at809.489m. These distances do
not measure actual premises or prove that a pair is one physical business.

All source rows are retained. No merge, deletion, source-coordinate repair or app
write occurred. Shared base numbers appear across multiple source nodes for71
numbers; a shared call center or different branches may legitimately share one
number. They are not merged. The745 rows without an address are not grouped by
an empty address. Extension metadata stays a separate identity component.

[Full measured groups](duplicate-readiness-report.json) and
[hashes plus8 executed synthetic contract checks](duplicate-readiness-verification.json)
retain the evidence. All8 checks passed with zero failures/errors/skips; these
are data-review checks, not new Android tests or business verification.

From the repository root:

```sh
python scripts/business-directory/research/check_duplicate_identity_review.py
python scripts/business-directory/research/duplicate_identity_review.py /workspace/work/business-data/node-geography-owner-261103-v2/research-node-businesses.ndjson /workspace/work/new-duplicate-review.json
```

The output path must not already exist. Input SHA256 is recorded; the tool reads
the corpus and never rewrites it. Production packaging, app assets, application
code, signing and release safeguards remain unchanged. The original24-record
explicit-address-tag package also remains unchanged.
