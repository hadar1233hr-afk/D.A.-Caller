# Bounded technical admission policy: owner 261103 source geometry

This review applies only to a new, explicitly named source-spatial projection of
the five audited owner-261103 cohorts. It permits preparation outside the app and,
after the independent data checks below pass, copying a newly generated clean
database into a future DA CALLER version for native validation. It does not admit
the old research/candidate databases, authorize publication, establish a legal
country boundary, or claim that a listed phone currently belongs to a business.

The delivered 0.12.0 APK and its evidence remain immutable. This policy does not
extend its owner's one-release signing/CI exception. Future release defaults,
including the original signing and populated-directory CI gates, remain intact.
Only `:app` is in scope; `:businessbook` and `:offline` are separate products.

## Source and spatial meaning

The source is the user's GitHub release asset 712722687, named
`israel-and-palestine-261103.osm.zip`. Its transfer was checked against the
downloaded asset; this is not an authenticated Geofabrik publisher download.
The ZIP SHA-256 is
`578d67213db0cb617f18ccd12c60eab5c532f073fa5520b399d9f5d36973b11e`;
its exact 121,050,024-byte PBF member SHA-256 is
`62c6ff161b738588454719f0fd1c4971c9b46535f4ab04313e7bbe1dd582afdf`.
The filename identifies the 2026-11-03 snapshot; individual source edits can be
much older. Keep the existing receipt and all source dates rather than describing
this as live, complete, or independently verified phone ownership information.

Admission means that the complete source object is strictly inside the reviewed
source IL relation 1473946, outside the union of the *filled* source West Bank
outer envelopes 1803010 and 12331025 and source Gaza 1473938, and farther than
0.001 longitude/latitude degrees from the reviewed admission boundaries. The
margin is angular, not meters. It is an operational source-spatial inclusion
rule, not an official or legal country determination.

Re-read the immutable PBF for point coordinates and every way/relation member,
role, node reference, tag, source timestamp and available source version.
Reconstruct whole ways and true-hole multipolygons using the reviewed topology
rules. Reject missing nodes, open or invalid rings, ambiguous/unsupported member
roles, guessed closing edges, fabricated repairs, invalid coordinates, zero-area
shapes and boundary/margin cases. Neither a centroid nor an inside vertex proves
admission of a complete area. Keep the true source holes in relation geometry;
do not confuse them with the deliberately filled excluded outer envelopes.

All raw country fields and their NULL values remain as sourced. Existing broad
secondary-country exclusions remain conservative, including represented-state
diplomatic tags; they do not prove that an office is physically outside Israel.
Do not invent `IL`, relabel old scopes as `explicit-il`, change the old 24-row
package, or silently broaden the bounded source set. The unchanged strict
`package_app_dataset.py` continues to reject the old research rows. The new
exporter needs its own explicit admission contract and decision ledger.

## Fresh category, access and lifecycle check

The original core extractor's `category()` rejects exact `access=private` and
four exact lifecycle `yes` flags. That is weaker than the extra-category policy.
Core research rows do not retain every original access/lifecycle/residential
tag. Consequently replaying the old category label or raw research row is
insufficient: re-read the complete actual PBF tags for every proposed source
object and retain the fresh tag evidence bound to the PBF hash and object ID.

Keep the reviewed core category selection and eight narrow extra categories;
do not add categories in this admission step. Apply one conservative publicness
filter to all five cohorts. Trim and case-fold flag values for decisions while
retaining unmodified source tags. Allow access only when absent, `yes`, `public`,
`customers` or `permissive`; withhold other or conditional access. Withhold
residential source objects, inactive/non-operating objects and nonempty lifecycle
prefix tags as specified below. Private commercial ownership (`ownership=private`
or `operator:type=private`) is not private access and is not a rejection by itself.

These are conservative omissions, not assertions that every omitted object is
private or that every admitted object is presently operating. The reviewed core
also includes public services such as libraries and clinics; coverage reports
must describe actual listed category counts without implying every row is a
commercial shop. Source tags and OSM timestamps do not certify current operation.

## Machine-readable bounded contract

The exporter must consume and enforce these values, bind this document's exact
SHA-256, and record the effective policy in its report. A policy hash alone is
not a substitute for actually applying the rules. No self-reported `PASS` field
or removal of a candidate flag constitutes admission.

<!-- BEGIN_ADMISSION_POLICY_JSON -->
```json
{
  "policy_id": "da-caller-owner-261103-source-spatial-v1",
  "review_status": "CONDITIONAL_GO_FOR_OUTSIDE_APP_NATIVE_EVALUATION",
  "source_pbf_sha256": "62c6ff161b738588454719f0fd1c4971c9b46535f4ab04313e7bbe1dd582afdf",
  "source_pbf_bytes": 121050024,
  "bounded_raw_rows": 2728,
  "bounded_source_objects": 2668,
  "admission_scope": "source-il-interior-minus-filled-wb-gaza-angular-margin",
  "inside_relation": 1473946,
  "filled_excluded_relations": [1803010, 12331025, 1473938],
  "margin_lonlat_degrees": 0.001,
  "require_complete_geometry": true,
  "require_fresh_pbf_tags": true,
  "preserve_raw_country_nulls": true,
  "preserve_prior_country_exclusions": true,
  "allow_new_source_objects": false,
  "category_policy": "unchanged-reviewed-core-plus-reviewed-eight-extras",
  "flag_normalization": "strip-casefold-for-decisions-only",
  "access_allowed": ["", "yes", "public", "customers", "permissive"],
  "hold_any_nonempty_access_conditional": true,
  "residential_buildings": ["house", "residential", "apartments", "detached", "semidetached_house", "terrace", "hut", "cabin", "dormitory", "bungalow"],
  "residential_values_held": ["yes", "private"],
  "nonoperating_buildings": ["construction", "ruins", "demolished", "proposed", "abandoned"],
  "lifecycle_keys": ["disused", "abandoned", "demolished", "razed", "construction", "proposed"],
  "lifecycle_allowed_values": ["", "no", "false", "0"],
  "hold_any_nonempty_lifecycle_prefixed_tag": true,
  "inactive_category_values": ["no", "vacant", "closed", "disused", "abandoned", "demolished", "razed", "construction", "proposed", "ruins", "private", "residential"],
  "private_ownership_alone_is_private_access": false,
  "allowed_exact_name_fallback_ids": ["osm:node:5801536067", "osm:node:12543641901"],
  "always_hold_unsafe_city_address_ids": ["osm:node:5066707334"],
  "require_native_python_phone_v2_roundtrip": true,
  "maximum_possible_projected_rows": 2727,
  "force_expected_row_count": false,
  "retain_every_raw_row_and_old_decision": true,
  "license_id": "ODbL-1.0",
  "attribution": "© OpenStreetMap contributors",
  "ready_state_before_native_verification": "READY_FOR_NATIVE_VALIDATION_ONLY",
  "authorizes_publication_or_signing_exception": false
}
```
<!-- END_ADMISSION_POLICY_JSON -->

Any source, policy, helper, schema, row, phone or reader change invalidates a
previous admission run. A different rule must be explicit and independently
reviewed, not hidden as a cleanup transformation. Additional conservative holds
are allowed when documented; never force the output to the upper-bound count.

## Identity, phone and text conditions

Retain all 2,728 exact original raw lines and historical decisions outside the
app, including their hashes and all previous text/phone holds. The five cohorts
are the existing 2,448 node, 247 way, one relation, 29 extra-node and three
extra-way rows. Keep separate source identities and multiple source phones;
shared phones do not justify merging businesses or manufacturing a preferred
owner. Emit one new decision per original row, explaining any new hold.

The exact-source-name derivative SHA-256
`36a7c0a07fad679249c44c975cba7c8d4fb00171d593d19f4ff0c6413a0f115a`
is comparison/history evidence, not the production dataset. Only these two
already reviewed alternate raw `name` tags may replace an unsafe preferred name:

- `osm:node:5801536067`: `دار كنا الضيافة`, also the exact `name:ar` value.
- `osm:node:12543641901`: `R. Gallery Ltd`, also the exact `name:en` value.

Re-read both selections from the original PBF, preserve their old unsafe name
and the two selection events, and require the new source-name projection to
match. Do not strip/reverse invisible controls. Keep `osm:node:5066707334`
(Rena's House) withheld because its city and constructed address contain U+200E;
no clean city/address alternative was reviewed. Continue enforcing native text
limits, forbidden control checks and safe source IDs/URLs on every emitted row.

Ten previously held phone rows represent eight source E.164 identities with
1599 service numbers. They may enter only if the separately reviewed new Java
business-only normalizer and matching Python v2 contract accept the *unchanged*
source E.164 phone, preserve its dialing identity, and give matching exact/prefix
queries. Do not rewrite raw phones into national form and pretend they were
sourced that way. Prove fixed/mobile/star preservation and reject malformed
phones, extensions and ranges that the reader cannot safely support. Existing
extractor/strict-packager/research phone helpers remain unchanged.

## Reproducible clean projection and independent proof

Build a fresh database with the native reader's 12 business columns, regenerated
FTS and actual derived metadata/manifest. Do not rename a candidate DB, remove
its flags, carry research/history tables into the app, or round-trip research
rows through the old country-tag contract with fabricated fields. Bind the exact
new exporter, policy, current Java reader, Python v2 helper, dependency versions,
PBF, five original cohorts, geometry inputs and all retained evidence hashes.

Historical evidence references the old `BusinessDirectory.java`. Only for this
one reference, use the exact historical Git source
`0d266d63d446b46d3e2a98048b112572d22768fa` or an already retained exact copy,
verify its original referenced file SHA-256, and record both the original path
and actual historical source/blob/path. This preserves historical proof rather
than falsely applying it to the modified reader. Bind and test the current
reader/Python v2 separately. No other old source/helper/audit hash substitution,
renaming or rewriting is permitted.

Snapshot and check dependencies before validation, recheck them after the run,
write atomically to a new output directory, and fail on drift or overwrite.
Retain a redistribution archive with the original raw rows, old decisions, two
source-name events, fresh PBF tag/geometry evidence, new admission ledger,
coverage counts, source/date receipt, ODbL-1.0 notice/license and attribution.
The distributable clean dataset and the required corresponding source/derivative
materials must remain reproducible and available under the source license.

An independent reviewer must check every original row and every projected field,
raw-line and policy/code binding; independently reconstruct the source geometry,
source tags, category/access/lifecycle decisions, phone/text decisions and both
name fallbacks; check full FTS/query equivalence, schema/integrity, duplicate
identity preservation, counts, manifest hashes and actual reader limits. Record
projected rows, distinct source objects, distinct dial identities, shared-phone
groups, null countries, holds by reason, category/geometry counts and source edit
date range. These measure only the bounded snapshot; there is no national
coverage denominator or current phone-owner verification.

Required negative cases include boundary/crossing/margin and overlapping
exclusions, missing/open/invalid geometry and true holes; NULL/non-IL country
preservation; restricted/conditional/private access, residential and lifecycle
conflicts (including case variants and prefixed flags); source/hash drift;
unauthorized name substitution/unsafe city/control/overlength text; malformed
1599/fixed/mobile/star/extension/range identity changes; and reader/FTS/schema or
manifest mismatch. Synthetic method counts are not native/UI tests.

Only after these data and current actual-reader host checks pass may the clean
DB be copied to future app assets for native evaluation. Then build a new future
version and run the required fresh Android 13 Telecom/native suite, all
populated-directory screens and real screenshots, full independent visual
review, offline/dial/data/archive checks, and the unchanged release gates.
Historical host checks and 0.12.0 screenshots cannot serve as that future proof.
Publication additionally needs its applicable signing/owner approval and public
download byte equality. Physical Jelly Star and matching-key upgrade tests stay
NOT TESTED unless actually performed; never advise uninstalling an existing app.

This document records a conditional technical policy only. It does not claim
that a new exporter, full source audit, production dataset, native test or
publication has already passed.
