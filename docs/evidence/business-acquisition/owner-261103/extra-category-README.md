# Additional source business categories, research only

The unchanged core extractor does not cover leisure-only categories or several
other public business categories. The [gap inventory](category-gap-README.md)
retains353 named phone-bearing source objects outside that filter. It includes
schools, clubs and other ambiguous/noncommercial listings; these353 objects
are not new Israeli businesses or geographic admissions.

A narrow reviewed policy considers only leisure fitness centres, bowling alleys,
escape games, trampoline parks and amusement arcades, plus amenity driving
schools, internet cafes and nightclubs. Already core-covered categories are
omitted to avoid duplicate source extraction. Restricted/unknown/conditional
access, residential buildings and lifecycle conflicts are held. Private
commercial ownership is not confused with private access. Generic sports centres,
pools, schools and clubs remain outside this extension without further review.
Absence of restrictive tags is not confirmation of public access or operation.

The original NODE runner's C++ key filter omits leisure-only objects. A new
dedicated research runner explicitly visits leisure/amenity nodes; the original
runner/core policy is unchanged. Exact source coordinates must pass the same
conservative source IL inclusion, both filled WB envelopes/Gaza exclusion and
0.001degree angular margin. Degrees are not meters or official borders. Separate
strict-category WAY inventory and the unchanged complete-polygon runner handle
the3 additional way rings, with exact source dependencies and no centroid/repair.

Actual result: **29 node rows and3 way rows,32 distinct phones**, with zero source
ID or phone overlap with the earlier cohorts.19 are fitness centres,4 nightclubs,
4 escape games,2 internet cafes and one each bowling/arcade/trampoline listing.
All32 missing country tags remain null, all text fields pass existing bounds,
and all32 rows still fail unchanged production country/scope admission. Source
object dates span2017–2026 and do not establish current phone ownership/activity.

The [node source audit](extra-category-node-independent-audit.json) reread all29
original PBF nodes and checked full tags, IDs/version/time, names/phones/addresses/
country fields and coordinates. Separate GEOS bindings replayed all29 points,
all PASS. The [way source audit](extra-category-way-independent-audit.json)
reread3 actual ways/15 referenced nodes and replayed their full polygons, all
PASS. Existing phone/category/geometry provenance contracts are disclosed shared
references, not independent phone-ownership verification.

All15 final policy unittest methods and18 gap-inventory synthetic privacy cases
PASS; these are not Android tests. Independent review found a lifecycle gap:
nonboolean values such as `construction=driving_school` and nonoperating building
values were initially missed. They are now held with3 regressions. No actual
whitelist candidate had those cases; the final node v2 was rebuilt after freeze
and audited. Initial v1 is private historical evidence only.

[Measured quality/aggregate report](extra-category-combined-quality.json),
[exact source corpus/report/audit/notice ZIP](extra-category-research-candidates.zip)
and [retention/code hashes](extra-category-retention.json) retain the results.
Combined research now has2,728 raw phone rows from2,668 source objects and2,623
distinct numbers. These are source counts, not verified physical businesses or
complete national coverage. No rows merged or added to the app. The earlier
audited SQLite stage remains its immutable2,696-row cohort; the32 new rows are
separate and must not enter it by renamed scopes/audits or invented country tags.

Reproduce using new output paths from the repository root:

```sh
/workspace/work/business-directory-research/.venv/bin/python scripts/business-directory/research/check_extended_business_category.py
/workspace/work/business-directory-research/.venv/bin/python scripts/business-directory/research/research_extra_category_nodes.py /workspace/work/business-data/israel-and-palestine-261103.osm.pbf /workspace/work/business-data /workspace/work/new-extra-category-nodes
/workspace/work/business-directory-research/.venv/bin/python scripts/business-directory/research/research_way_geography.py /workspace/work/business-data/israel-and-palestine-261103.osm.pbf /workspace/work/business-data/extra-category-inventory-owner-261103-v1 /workspace/work/business-data /workspace/work/new-extra-category-ways --extractor scripts/business-directory/research/extended_business_category.py
```

Use the exact owner PBF and retained geometry/inventory inputs. Reports bind
actual source/policy/shared-helper hashes; no original extractor is patched or
monkey-patched. ODbL1.0 attribution to OpenStreetMap contributors is retained.
The app, production packager, signing/CI gates and reviewed native APK remain
unchanged.0.12.0 has no business data; publication exception approval is pending.
