# Complete closed-way geography research

This extends the earlier NODE-only research using the same owner-uploaded
Geofabrik/OSM snapshot and conservative source-geometry policy. It does not
change production geographic admission, Android assets, signing or CI guards.
DA CALLER0.12.0 still has no business directory/data; APK publication remains a
separate pending explicit-approval step.

The [source inventory](way-inventory-README.md) found369 named phone-bearing
public-category ways and5 relations. Every way had a closed reference ring and
all referenced source coordinates available. Full-polygon checks qualified245
ways;3 had no qualifying phone, leaving **247 phone rows from242 source ways**,
with242 distinct base numbers.245 emitted phone rows have no source country
tag and2 retain IL. No tag was inferred from location or a phone prefix.

Rejection counts are11 explicit non-IL fields under the existing broad policy,
105 polygons not wholly inside the source IL geometry,7 intersecting/near the
filled historical WestBank envelope and1 near the source IL boundary. Non-IL
diplomatic fields can describe the represented state; these rejections are not
proof of a foreign physical address. All5 relations are omitted: their7 outer
and3 inner roles require complete role-aware assembly, not filled guessed holes.

The tool requires the original ordered way references, every exact node
coordinate, GEOS-valid/nonempty/positive-area polygons and no `area=no`. The
ENTIRE polygon must be contained inside the retained source IL ring, more than
0.001degrees from its boundary, and disjoint/more than0.001degrees from BOTH
filled WestBank envelopes and Gaza. The angular margin is not meters. Boundary
contact, crossing edges or polygons enclosing an excluded area are rejected;
checking only vertices or a centroid would wrongly accept some such polygons.
No ring is closed, simplified, repaired or assigned a new centroid.

All16 adversarial geometry checks passed and were independently rerun/reviewed.
They cover concave inclusion geometry with every business vertex inside but an
edge outside, complete enclosure of an exclusion with every vertex outside it,
crossing/tangent/near boundaries, both WB envelopes, open/missing/mismatched
references, invalid coordinates, self-intersection, zero area and `area=no`.
These are research checks, not new Android/native tests or verified businesses.

An [independent audit](way-geography-independent-audit.json) reread all242
original ways and2,639 referenced nodes directly from the PBF, rather than using
the cached inventory as a source substitute. It compared all247 phone rows,
including2,920 ordered coordinate slots, full raw tags, IDs, versions, timestamps,
names, categories, addresses, country fields and original/canonical phones.
All matched. A separate GEOS implementation replayed every complete polygon;
all247 passed. The closest measured clearance is0.0012394803860540896degrees,
not meters. Source category/phone parsing and the retained geometry-provenance
validator remain shared reference contracts; their independence is not claimed.

[Measured research report](way-geography-report.json),
[unchanged-packager/text/aggregate checks](way-geography-quality-summary.json),
[same-identity phone proposals](way-geography-phone-quality.json) and
[cross-representation identity notes](way-geography-cross-identities.json)
retain the results. All247 unchanged research rows still fail the production
packager:244 country/scope guards and3 dial identities. The latter are same-E.164
UAN1599 national-format proposals, not rewritten source phones or app admission.
All production text fields pass bounds/control checks on these247 rows.

Together with the unchanged2,448 NODE rows, this gives **2,695 raw phone rows
from2,635 source objects and2,590 distinct base numbers**. Five numbers appear
on both point and area source listings; they remain separate, with zero merges.
Source objects are not necessarily distinct physical businesses.816 phone rows
lack an address and1,497 lack a city tag. The
[combined source-coverage breakdown](way-geography-combined-coverage.json)
retains exact source city/category/date counts without reverse geocoding or a
national coverage percentage. Source object dates are historical, not evidence
of current names, phone ownership, active businesses or complete coverage.

The exact corpus, report, independent audit, quality notes and attribution are
retained in [the derived research ZIP](way-geography-research-candidates.zip).
[Retention hashes/checks](way-geography-retention.json) bind the actual executed
helper/check/audit sources and retained bytes. No app or release approval is
implied by source-fidelity or geometry checks.

Reproduce from the repository root using the locked research venv and a fresh
output directory:

```sh
python scripts/business-directory/research/check_way_geography.py
/workspace/work/business-directory-research/.venv/bin/python scripts/business-directory/research/research_way_geography.py /workspace/work/business-data/israel-and-palestine-261103.osm.pbf /workspace/work/business-data/way-inventory-owner-261103-v2 /workspace/work/business-data /workspace/work/new-way-geography --extractor scripts/business-directory/extract_osm_businesses.py
```

The source PBF must match the owner-upload receipt. Recreate/unpack the inventory
and previously retained geometry inputs before replaying elsewhere. Output
directories must not exist. Code, source, inventory and geometry hashes are
bound in the report; replay observation times differ, while source values stay
unchanged. Raw derived data retains ODbL1.0 attribution to OpenStreetMap
contributors and stays separate from personal contacts and application code.
