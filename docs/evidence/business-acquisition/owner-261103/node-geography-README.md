# NODE geography research: owner-supplied261103 extract

Research-only result: **2,448 raw phone records from2,393 actual OSM nodes,2,353 distinct base phone numbers**. Country admission uses strict interior of source IL relation1473946, excludes the UNION of both fully filled source WestBank outer envelopes1803010/12331025 and source Gaza1473938, and excludes an angular margin0.001degree around source boundaries. The distance is measured in lon/lat degrees, not exact meters. All ways/relations are omitted because their complete geometry was not evaluated.

These are **research candidates**, not accepted app data or an official/legal country determination. The existing24-row explicit-address-tag package is unchanged;0 new rows are bundled. Syntax/allocation checks and OSM object timestamps do not confirm a current business/phone owner. No national coverage guarantee, phone call, private contact read or app/archive change occurred.

Every emitted node identity, source name, category, original/canonical phone, raw country fields, exact point coordinate and timestamp was independently re-read from the original PBF. Output counts/hash and all2,393 source IDs matched. The exact raw NDJSON is retained in node-geography-research-candidates.zip with an ODbL notice; the original work path/hash are recorded in node-geography-inputs.json and archive hashes in node-geography-retention.json. The source PBF remains the owner's verified release asset.

## Diplomatic field semantics

The initialv1 produced2,456 rows. Strict secondary-country handling inv2 conservatively omitted8 diplomatic rows. All9 prior-package observations are office:diplomatic with addr:country=IL and country identifying a represented state (IN/NG/CA/VA/DE/TR/ER/VN/EU). **This is not proof of an outside-Israel location or a defect in the prior24-row package.** See node-geography-country-field-semantics.json. A later reviewed semantic admission policy may handle represented-state tags separately; no v3 policy was applied.

The primary extractor, app, signing/CI/release gates and existing package were not changed. This research cannot authorize0.12.0 publication. Original executed v2 script has an importable GeometryScope classifier, country_decision and validate_geometry_provenance helpers plus a guarded CLI. The geometry-input ZIP retains exact reviewed inputs and the ODbL notice for reproducibility. Existing preinstalled GEOS C library is required; no guessed fallback or new network dependency is used.

## Independent review and production separation

All2,448 point classifications were independently replayed and matched; geometry
ZIP digest/CRC passed, and the primary24 source/package hashes and actual24-row
SQLite count/integrity remain unchanged.16 separate geometry/country contract
checks passed, including boundary/margin/union/invalid-coordinate and broad
secondary-country exclusion cases.

The source-node audit is preserved verbatim as original execution evidence. Its
label `country conflicts` means the broad field-level exclusion, not a proven
contradiction in physical location; the diplomatic semantic companion and this
note supersede that interpretation. The old primary24 entries are not declared
wrong. The research policy may deliberately omit legitimate diplomatic offices.

The unchanged production packager rejected all2,448 research rows:2,439 country/
scope rejections,7 app dial-identity rejections and2 source-text validation
rejections. No app record was added or silently repaired. Source object dates
span2010–2026;745 entries lack an address and5 duplicate-address rows require
review. Phone ownership/currentness remains unverified. Reproduction commands
and all pending integration requirements are in scripts/business-directory/
research/README.md. No0.12.0 release, app tree, signing or CI gate changed.
