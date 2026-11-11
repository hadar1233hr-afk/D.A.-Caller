# Conservative source-geometry research

These offline tools prepare **research candidates only** for DA CALLER. They do
not change the application, the production packager, signing or publication gates.
The original24-record explicit-address-tag package remains unchanged, and0.12.0
still contains no business data.

The latest outside-app candidate is documented in
[business-candidate-README.md](../../../docs/evidence/business-acquisition/owner-261103/business-candidate-README.md).
It retains all 2,728 raw lines/decisions and projects 2,715 unchanged identities.
The new builder/auditor and 28 contracts are separate from the old packager;
six actual-reader host tests are retained with their JUnit. This is not production
admission or emulator/native integration. Its reader-shaped schema can load if
manually copied, so keep the nonstandard filenames/outer envelope and never put
it in app assets. Missing country fields/scopes and old artifacts remain intact.

The retained261103 result has2,448 raw phone rows from2,393 source nodes and2,353
distinct base numbers. Source names, original/canonical phones, country fields,
coordinates and object timestamps were independently checked against the PBF.
This proves source fidelity, not a live business, current phone owner, official
country determination or complete national coverage. Source dates span2010–2026.

Admission requires a point strictly inside the complete source IL polygon and
outside BOTH filled WestBank outer envelopes and the source Gaza polygon, with
an angular0.001-degree margin around boundaries. Degrees are not exact meters.
The NODE tool omits ways/relations; the separately audited closed-way extension
below evaluates full polygons. The relation extension retains true source holes
and omits ambiguous/nested/incomplete geometry rather than guessing.
Missing country tags stay null; they are never fabricated from a phone prefix
or location. Any non-IL country field triggers a deliberately broad research
exclusion: on diplomatic offices `country` may mean the represented state.
Eight such rows were omitted conservatively; this is not proof of a foreign
address or an error in the prior24-row explicit-address-tag dataset.

## Reproduce without changing app assets

Use Python3.12, the pinned osmium/phonenumbers dependencies from the parent
requirements file and an existing GEOS C library. No fallback geometry or network
lookup is used. The source PBF must match the owner-upload receipt. The retained
geometry-input ZIP is under docs/evidence/business-acquisition/owner-261103.

From the repository root, unpack it into a new private workspace directory:

```sh
python3 -m zipfile -e docs/evidence/business-acquisition/owner-261103/node-geography-geometry-inputs.zip /workspace/work/node-geometry-inputs-261103
/workspace/work/business-directory-research/.venv/bin/python scripts/business-directory/research/research_node_geography.py /workspace/work/business-data/israel-and-palestine-261103.osm.pbf /workspace/work/node-geometry-inputs-261103 /workspace/work/node-geography-reproduced --extractor scripts/business-directory/extract_osm_businesses.py
python3 scripts/business-directory/research/check_node_geography.py
```

Output directories must not already exist. The tool is byte-identical to the
executed research source retained in the report. The explicit `--extractor`
argument makes the CLI usable outside the original workspace default. Repeated
runs record a new import observation time, so compare all record values except
`imported_at` when checking source-data equivalence. Keep the report's input and
code hashes; do not silently treat results from another source or policy as this
snapshot. The16 independent geometry/country checks require GEOS and are an
explicit research check, not an Android test or an automatic production CI gate.

## Integration remains pending

The production packager rejects all2,448 unchanged research rows.2,439 fail its
country/scope guard;7 fail the app dial-identity contract and2 fail source-text
bounds/control validation before that guard. No number or name was silently
repaired.745 rows have no address;5 duplicate-address rows require review before
claiming distinct physical businesses. A reviewed country-admission contract,
phone/text handling, deduplication and fresh native directory verification are
needed before any future app integration. Never rename the research scope or
fill a missing country tag merely to pass production validation.

The compressed research corpus and geometry inputs retain an ODbL notice,
source IDs, source timestamps and checksums. See the matching evidence README,
semantic review and retention manifest. They do not approve APK publication.

## Identity quality review without changing source rows

`duplicate_identity_review.py` reports shared base phones and repeated
name/phone/extension/nonempty-address keys. It preserves every source row and
flags ambiguous locations for manual review. On this corpus, five two-node
groups have matching keys: three points nearly coincide, one pair is12.029m
apart and one is809.489m apart. These are approximate source-coordinate
distances, not measured premises or proof of duplicate physical businesses.
Seventy-one numbers are shared by multiple source nodes; they are not merged.
All8 synthetic identity-review checks passed.

```sh
python scripts/business-directory/research/check_duplicate_identity_review.py
python scripts/business-directory/research/duplicate_identity_review.py /workspace/work/business-data/node-geography-owner-261103-v2/research-node-businesses.ndjson /workspace/work/new-duplicate-review.json
```

The output path must not exist. See the retained `duplicate-readiness-*`
evidence for exact groups and input/code hashes. This review neither reduces
the2,448-row source corpus nor adds records to the app.

`phone_identity_review.py` explains all seven existing phone-identity failures:
they are five distinct UAN1599 identities. A library-derived national form
round-trips to the same source E.164 base number without a trunk zero. Full raw
records and separate extensions are preserved. The proposals do not fill
country tags, change source phones or pass the existing country/scope guard.
Numbering-plan validity does not prove current ownership or reachability.
Sixteen focused checks, including the retained actual corpus, passed.

```sh
/workspace/work/business-directory-research/.venv/bin/python scripts/business-directory/research/check_phone_identity_review.py
/workspace/work/business-directory-research/.venv/bin/python scripts/business-directory/research/phone_identity_review.py /workspace/work/business-data/node-geography-owner-261103-v2/research-node-businesses.ndjson
```

`source_text_review.py` independently checks all seven production text bounds,
including fields hidden behind earlier scope rejection. Three rows have `Cf`
controls: two names and one city/address. None is oversized. An independent
PBF reread retains exact source tags and clean name alternatives for review;
no control was deleted, text reordered or alternative selected. The earlier
first-failure histogram of two text errors remains correct. See the retained
`text-readiness-*` and `phone-readiness-*` evidence for exact reproduction,
source IDs, original values, limitations and code/input hashes.
All29 text checks passed after independent review strengthened source-object
binding for every supplied reread and excluded pronunciation/etymology or
unsupported-language metadata from name alternatives. Only exact `name`,
`short_name` and their explicitly supported `he/ar/en` tags are considered;
unknown tags remain retained raw, without guessed language or selected names.

## Complete closed-way research extension

`way_source_inventory.py` independently inventories named, phone-bearing public
source ways/relations and resolves their referenced coordinates using bounded
PBF ID passes. Its retained source ZIP contains369 ways,5 relations and3,944
dependency nodes. Closure/available coordinates alone do not establish topology
or geographic membership. Relations retain outer/inner roles but are not
assembled or admitted.

`research_way_geography.py` considers complete standalone closed polygons only.
It tests the ENTIRE polygon against the same source IL inclusion, both filled
WB envelopes, Gaza and angular0.001degree margins. It rejects missing/open/
invalid/zero-area rings and `area=no`; it never invents a closing edge, centroid,
hole or country tag. Sixteen adversarial geometry checks passed, including
crossing edges despite all vertices being inside an inclusion, and enclosed
exclusions despite all vertices being outside them.

The measured result is247 phone rows from242 source ways/242 distinct numbers.
`audit_way_geography.py` independently reread242 original PBF ways and2,639
referenced nodes, compared every retained field and all2,920 coordinate slots,
and replayed all247 complete polygons with separate GEOS bindings. All passed.
It reuses the original category/phone parser and geometry-provenance validator
as disclosed reference contracts, not independent implementations of them.

```sh
python scripts/business-directory/research/check_way_geography.py
/workspace/work/business-directory-research/.venv/bin/python scripts/business-directory/research/research_way_geography.py /workspace/work/business-data/israel-and-palestine-261103.osm.pbf /workspace/work/business-data/way-inventory-owner-261103-v2 /workspace/work/business-data /workspace/work/new-way-geography --extractor scripts/business-directory/extract_osm_businesses.py
/workspace/work/business-directory-research/.venv/bin/python scripts/business-directory/research/audit_way_geography.py /workspace/work/business-data/israel-and-palestine-261103.osm.pbf /workspace/work/new-way-geography/research-way-businesses.ndjson /workspace/work/business-data /workspace/work/new-way-source-audit.json --extractor scripts/business-directory/extract_osm_businesses.py
```

All output paths must be new. Unpack/recreate the retained inventory/geometry
inputs first when replaying elsewhere. The report binds their source/code/data
hashes. The new way corpus remains rejected by unchanged production guards
(244scope/country,3same-E.164 UAN1599 format proposals). It has no text-bound/
control failures. Node+way research totals2,695 raw phone rows/2,635 source
objects/2,590 distinct numbers, with five shared point/area phones retained
separately. No count proves distinct physical businesses, current ownership,
official borders or full national coverage.0app records;0.12.0 remains without
businesses and publication approval remains pending. See matching
`way-geography-*` evidence, exact corpus ZIP and combined coverage report.

## Role-aware source relation research

`research_relation_geography.py` assembles original outer/inner members only at
matching source reference endpoints. It requires complete unambiguous rings,
unique hole-to-shell assignment and valid whole multipolygons. Source holes
remain holes; overlapping/orphan/touching holes, ambiguous/nested members and
invented closure/repair are rejected.20 adversarial contracts passed and were
independently rerun. All5 source relation decisions were independently replayed
from actual PBF5relations/10ways/131nodes. One relation yields1 source phone row;
4 are excluded. The emitted1relation/2ways/39nodes and actual hole matched.
See `relation-geography-README.md` in the retained source-evidence directory for
exact corpus/report/audit ZIP hashes and CLI reproduction.

## Separate offline research SQLite

`stage_research_sqlite.py` creates a distinctly named RESEARCH_ONLY schema,
never a production app dataset. Exact audited NODE/WAY/optional RELATION bytes,
reports/audits, original scopes, code/dependency hashes and runtime versions are
bound and rechecked before retaining a fresh output. No existing app asset,
country field, personal contact/history/archive or production guard is changed.

Actual final staging retains2696 original lines/SHAs and2680 null countries.
2693 text-safe FTS rows include10 separately marked same-E.164 phone-format
proposals requiring explicit boolean opt-in; default search has2683 entries.
3 source-text rows remain stored outside search. Raw bytes and ordered cohorts
can be reconstructed exactly. Stable INTEGERPRIMARYKEY FTS bindings survive
VACUUM. Every app_accepted value remains0, and the schema has no app businesses
table. Querying opens read-only with bound MATCH, literal tokens and keyset pages.

25 contracts and an independent full actual-database audit PASS.33 pages covered
1217 shop matches without omissions/repeats. The query plan uses FTS+INTEGERPK
lookup and a temporary sorting B-tree; desktop checks are not Android UI or
million-record performance tests. See `research-search-README.md` and its exact
database/manifest/audit/check ZIP in source evidence. Production admission and
future native integration remain separate, and0.12.0 stays without businesses.

```sh
/workspace/work/business-directory-research/.venv/bin/python scripts/business-directory/research/check_research_sqlite.py
/workspace/work/business-directory-research/.venv/bin/python scripts/business-directory/research/stage_research_sqlite.py query /workspace/work/business-data/research-search-stage-owner-261103-v2/research-search.sqlite "אג׳נדה"
```

## Additional source categories without broadening the core filter

`category_gap_inventory.py` retains353 source objects outside the original
category filter, with18 synthetic privacy cases checked. These include
noncommercial/ambiguous schools/clubs and are not geographic/business admissions.
`extended_business_category.py` supplies a narrow8-value research policy, holds
access/residential/lifecycle ambiguity and avoids existing core-covered objects.
Private ownership does not mean private access.15 final policy methods passed,
including regressions for nonboolean lifecycle/nonoperating-building conflicts.
The core extractor and earlier runners remain byte-identical.

`research_extra_category_nodes.py` explicitly visits leisure/amenity NODEs;
the earlier core C++ filter does not visit leisure-only objects.29 extra nodes
passed source-spatial research and independent direct-PBF field/point replay.
`extra_category_geometry_inventory.py` retains3 extra WAY rings/15 exact nodes;
the unchanged full-way runner/auditor with the explicit new policy emitted and
independently checked all3. No additional relation was admitted by this policy.
The32 phones/sourceIDs have0overlap with prior cohorts. All country fields remain
missing, all text checks pass, and unchanged production packaging rejects all32.

Aggregate source research now has2728rows/2668sourceobjects/2623distinctphones.
This does not establish distinct physical businesses, current ownership or full
coverage. The existing2696-row researchSQLite and app data remain unchanged.
See `extra-category-README.md` and matching source/audit/retention ZIPs in the
retained evidence directory for exact reproduction and disclosed references.

## Explicit expanded research staging

`stage_expanded_research_sqlite.py` is a separate builder using the unchanged
base schema/insertion/read-only query contracts. It requires allfive explicit
families (NODE/WAY/RELATION plus extra_node/extra_way), their distinct audits,
source/report/PBF hashes and current reviewed policy/producer/reference code.
Original physical IDs/scopes/null countries remain intact; old audit files are
not renamed to approve extra categories. Contradictory app-counter aliases fail
even when the first is0. Cross-family source-object overlap fails without merge.

14 new contracts and independent actualexpandedDB audit PASS:2728original
lines,2725FTS projections,2715default/10phoneproposals/3textwithheld,2712null
countries. All32 extra-phone queries found their source rows; fitness4pages19
and shops33pages1217 had0repeat/omission. The prior2696-row DB and basehelper
remain byte-identical. The new schema/profile is still RESEARCH_ONLY, every
app_accepted=0 and0rows enter app assets or production admission. See
`expanded-research-README.md` in retained evidence for exact ZIP/hashes/audit and
CLI reproduction. Query tests are desktop SQLite evidence, not Android UI or
million-record performance validation.

New primary-source/license observations are separately retained in
`docs/evidence/business-acquisition/primary-sources-2026-11-06.md/.json`.
DataGov license/branch preview counts are metadata counts, not imported phones;
officialAllThePlaces code/spider names are not an acquired dataset. Download
hosts lack executor destination permission and actual files/overrides still
need supported acquisition.0new external files/phones imported; no bypass or
third-party snippets converted into a phonebook.
