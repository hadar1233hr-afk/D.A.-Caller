# Source-geometry business candidate: preparation boundary review

This is an **independent technical recommendation only**, not owner approval or
authorization for the pending0.12.0 signing/CI exception, native activation or
publication. No such owner approval is received or inferred by this review.

Technical conclusion: a **fresh, outside-app candidate projection** is legitimate
preparation for the existing business-directory request. It must remain explicitly not
production-admitted, not native-verified and not approved for publication. This
review does not authorize the pending signing/CI exception or any change to the
current0.12.0 APK, assets, feature flag, production packager or release guards.

The proposal is different from renaming the research database only if it applies
a separately specified candidate contract to immutable original records, builds
a new database and preserves the full source/admission evidence. Merely copying
the research tables into reader-shaped tables, filling country tags or changing
`geographic_scope` to make the existing packager accept them would be a bypass.

## Why the boundary matters

The unchanged [production packager](../../scripts/business-directory/package_app_dataset.py)
requires `geographic_scope=explicit-il` and an actual IL source-country tag in
`record_values`. All original research records still fail that admission rule.
The unchanged [native reader](../../app/src/main/java/com/dacaller/app/BusinessDirectory.java)
does **not** repeat country admission: it trusts packaged assets, checks their
manifest/hash/schema/row identities and validates display/dial identities while
reading. A database with the reader's schema and a `DA_CALLER_BUSINESSES` manifest
is therefore runtime-loadable if someone copies it to the expected asset paths.
Extra JSON fields such as `candidate_only=true` do not stop the current reader;
it ignores unknown manifest fields.

Consequently, retain an unmistakable outer candidate envelope and nonstandard
filenames, for example `candidate-businesses.sqlite`, `candidate-manifest.json`
and `review-status.json`. Do not emit an `app-assets/business-directory/` layout,
`manifest.json` asset or automatic install/copy/release step. The inner manifest
may use the real reader format for explicit compatibility tests, but it must be
named as a candidate and surrounded by status that says
`production_admission=false`, `native_integration=NOT_TESTED` and
`publication_approval=false`. These are preparation controls, not a new runtime
security boundary. Activating data in the current0.12.0 APK would also contradict
the owner's explicit business-free release scope even if the UI flag stayed off.

## Minimum candidate contract

1. Bind the exact expanded research DB/manifest/audit and the actual retained
   owner PBF/source receipt. Require all five cohort/audit identities, scopes,
   corpus hashes and reviewed producer/policy/reference hashes. Bind before
   validation and recheck before atomic retention; use a fresh output path.
2. For each row, retain exact raw source identity, URL, version/time, original
   phone/extension, country fields and scope in a decision/provenance ledger.
   Spatial evidence must identify the original point, complete way polygon or
   correctly assembled relation with true holes, and the hash-bound inclusion,
   both filled WB envelopes/Gaza exclusion and angular-margin evidence. Source
   geometry is not an invented per-row IL tag or an official/legal country claim.
3. Define a new **candidate-only source-spatial projection** explicitly. Never
   mutate a copy of a row's country/scope and then call the old `record_values`
   to simulate acceptance. The old strict packager stays unchanged and still
   rejects the original research rows. Fresh projection may reuse bounded-text,
   phone-normalization, FTS-normalization or schema helpers as disclosed reference
   contracts without pretending that the old country guard admitted the data.
4. Include only text-safe rows with `IDENTITY_UNCHANGED` phone review. Revalidate
   native limits and the exact canonical dial identity; preserve the source phone
   separately from its normalized national identity. Keep all10 UAN proposals and
   all3 Cf text rows in the ledger as withheld, not repaired or silently lost.
   Any future nonempty extension needs an explicit disposition; never append its
   digits to the base number. The actual current eligible cohort has0 extensions.
5. Keep distinct source objects sharing a phone. Use the existing source-ID plus
   canonical-base-phone stable-ID rule, require positive signed63-bit IDs and fail
   collisions/duplicate base identities explicitly. Do not merge branches based
   on name, address, proximity or phone alone. Measure record, distinct-source and
   distinct-normalized-phone counts separately from the actual output.
6. Preserve unmodified displayed source values after validation rather than
   silently stripping controls, reversing text or truncating it. In addition to
   Python code-point checks, enforce the native Java UTF-16 name bound. Preserve
   the raw source phone formatting in provenance; `phone_display` must itself pass
   the current reader's exact normalization check and need not be the free-form
   raw tag. Both `phone` and `phone_display` must normalize to `normalized_phone`.
7. Retain ODbL1.0 attribution, the exact derived machine-readable database and
   source/decision/provenance records for the later distribution offer. Record
   owner-upload acquisition truthfully: consistency with the retained upload is
   not an authenticated publisher download. Separate extraction/candidate times
   from original object dates; make no current ownership, active-business or
   national/million-record coverage claim.

Read-only observation of the existing expanded DB on2026-11-06 found2,715 default
rows,2,655 distinct source IDs,2,612 distinct source-phone strings and2,699 null
country fields in that subset; all were `IDENTITY_UNCHANGED` and text-safe, with
0 nonempty extensions. Maximum name length was89 in both code points and Java
UTF-16 units. These are observations of the input cohort, not a built candidate
or a native compatibility test. Counts in a future package must be measured
again, including its normalized-phone count.

## Checks that would make the candidate reviewable

- Compare every projected row and every withheld decision with the exact original
  line/key and its audit proof. Reconstruct all2,728 source lines and prove no
  mutation, fabricated country, silent loss or cross-object merge.
- Assert the original app tree, feature flag, source APK, packager and signing/CI
  files remain unchanged. Negative-check that original research still fails the
  strict country admission and that the candidate is not present in app assets.
- Verify manifest bounds/types/HTTPS/source/license/version/count/hash against
  the actual native reader. The current count cap is2,000,000 and DB cap512MiB;
  JSON booleans must not pass integer counters. Use the candidate's real hash.
- Verify `user_version=1`, the12 named `businesses` columns, matching
  `metadata.dataset_version`, FTS4 `business_search`, exact counts and complete
  `docid=id` coverage. Include missing/extra/swapped FTS rows and stable-ID/hash
  collisions as rejection cases; check integrity before retention.
- Validate every row using native name/URL/phone/display/normalized-phone rules.
  Exercise the reader's actual query forms: empty listing, national/E.164 phone
  prefixes, Hebrew with/without niqqud, English, punctuation and50+1 offset pages
  with duplicate source names and shared phones. The reader uses implicit FTS
  intersection; a desktop search using explicit `AND` alone is insufficient.
- Verify the dataset digest and old artifacts stay unchanged after read-only
  queries; verify mutated sources/audits/policy/counters cause no partial output.
  Attribute shared parser/normalizer/query references honestly. Host SQL/schema
  checks do not prove Android SQLite, UI, actual dialing or device performance.

Before later activation, the new source-spatial admission policy needs its own
source/license/phone review and fresh actual native installation/query/UI/dial
validation in the appropriate future app source. The existing research desktop
audits and0.12.0 call screenshots do not establish those results. All current
signing/publication approvals remain separate and pending.

## Projection-risk checkpoints for implementation review

- Bind selection to the exact retained independently audited expanded DB and
  cohort proof, not just a self-consistent caller-supplied database/manifest pair.
  Recompute row eligibility rather than treating `default_searchable=1` alone as
  country/phone/naming admission or authority to package.
- Treat the spatial contract as candidate eligibility evidence. Missing raw
  country tags remain null in provenance, and no `explicit-il` label is fabricated
  to pass the existing country-tag gate. A schema-compatible candidate does not
  mean that production admission has changed.
- Keep source-original, phone-projection and withholding decisions separately
  attributable. An eligible row must retain the exact raw key/line hash and
  known cohort identity; one-to-many source phones must not be folded into one
  source row or confused with distinct physical businesses.
- Fail rather than repair uncertain identities, duplicate stable IDs, mismatched
  display/normalized phones, contradictory audit counters, missing spatial proof
  or mutated inputs. Candidate count/unique-source/unique-phone totals must be
  measured from the fresh output, not inferred from research totals.
- Runtime compatibility is a property to test separately. A candidate-only marker
  is ignored by the unchanged native reader; filenames/outer-envelope and absence
  of copy/install paths prevent accidental activation but are not a substitute
  for later source-admission and native review.
- Code/read-only host SQL checks remain technical observations. They cannot be
  promoted into owner authorization, Android/native test results, signing/CI
  exception approval or permission to publish0.12.0 with business data.

This review changed only this document. No candidate was generated, no tests were
reported as newly run, and no app/data/guard/asset file was modified here.
