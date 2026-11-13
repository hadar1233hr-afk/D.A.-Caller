# Role-aware complete multipolygon research

This bounded extension evaluates the five named, phone-bearing source
RELATIONs retained in the owner-upload Geofabrik/OSM inventory. It is research
only. No app, production packager, signing, workflow or publication guard changes.
DA CALLER0.12.0 still has no business data.

Every relation member must be an explicitly role-tagged `outer` or `inner` WAY,
with complete source references and coordinates. Nested relations, non-way
members, empty/unknown roles, duplicate member ways, unresolved references and
ambiguous/open endpoint graphs are rejected. Open source segments join only at
identical source node references with exactly two incident ways, preserving the
traversal direction of every original source edge. No closing edge is invented.

Closed outer/inner rings must be GEOS-valid/nonempty/positive-area polygons.
Every inner hole must lie strictly in exactly one outer shell, without touching
its boundary. The complete MULTIPOLYGON must remain valid, including all real
holes and disjoint outer islands. No hole is filled and no geometry is repaired,
unioned, simplified or replaced with a centroid.

The entire source shape must be inside the retained source IL polygon, more
than0.001degrees from its boundary, and disjoint/more than0.001degrees from both
filled source WB envelopes and Gaza. These are source lon/lat angular margins,
not surveyed meters, official/legal borders or proof of business ownership.
True source holes remain holes during the geographic tests.

The actual result is **one phone research row from one relation**:
[WeWork14307079](https://www.openstreetmap.org/relation/14307079), retaining the
exact original phone `09-9522222`, parsed base `+97299522222`, outer shell and inner
hole. Its source timestamp is2024-07-28T16:08:08+00:00; no present-day phone/name
verification is claimed. Its missing country tag stays null. Two other complete
relation shapes fail source IL containment and two intersect/are near the filled
historical WB envelope. The report retains every decision and source identity;
no rejected relation is relabeled or silently repaired.

All20 adversarial geometry/assembly checks PASS. They cover true holes, open-way
orientation, multiple outer islands, missing/invalid nodes, duplicate/nested/
unknown-role members, branching endpoints, orphan/touching/overlapping/nested
holes, overlapping shells, full-shape containment/exclusion/margins and source
immutability. A source hole genuinely enclosing an exclusion is tested as a hole;
the same shell with its hole removed correctly fails the exclusion predicate.
These are research checks, not Android/native tests or verified businesses.

The [independent audit](relation-geography-independent-audit.json) reread all
five source relations, their10 original member ways and131 source nodes directly
from the PBF. It independently reassembled role-aware cycles and replayed every
acceptance/rejection using separate GEOS bindings; all five decisions matched.
The emitted cohort is one relation, two member ways and39 nodes. Every retained
row field, source role/edge/traversal, node timestamp/coordinate and real inner
hole matched. Original relation versions are compared; the input inventory did
not retain member-way versions, so no such comparison is claimed. Shared source
category/phone parsing and source-geometry provenance validation remain disclosed
reference contracts rather than independent implementations.
The20 adversarial methods were independently rerun and all passed with zero
failures/errors/skips. The exact corpus/report/audit/quality/check log/license
are retained in [the derived ZIP](relation-geography-research-candidates.zip);
[retention hashes](relation-geography-retention.json) bind every member and the
executed helper/check/audit sources.

[The report](relation-geography-report.json) binds source, inventory, retained
country geometry, executed helper and reference parser/helper hashes. The
unchanged production packager still rejects the sole row at its country/scope
guard. All its text fields pass bounds/control checks; no phone reformat proposal
or country inference is needed. It adds one distinct number absent from the
prior NODE/WAY corpus: combined research becomes2,696 phone rows from2,636 source
objects and2,591 distinct base numbers. Source objects are not necessarily
distinct physical businesses; no automatic merge or national coverage claim.

From the repository root, using the existing locked research environment:

```sh
python scripts/business-directory/research/check_relation_geography.py
/workspace/work/business-directory-research/.venv/bin/python scripts/business-directory/research/research_relation_geography.py /workspace/work/business-data/israel-and-palestine-261103.osm.pbf /workspace/work/business-data/way-inventory-owner-261103-v2 /workspace/work/business-data /workspace/work/new-relation-geography --extractor scripts/business-directory/extract_osm_businesses.py
/workspace/work/business-directory-research/.venv/bin/python scripts/business-directory/research/audit_relation_geography.py /workspace/work/business-data/israel-and-palestine-261103.osm.pbf /workspace/work/new-relation-geography/research-relation-businesses.ndjson /workspace/work/business-data /workspace/work/new-relation-source-audit.json --extractor scripts/business-directory/extract_osm_businesses.py
```

Recreate or unpack the retained source inventory/geometry inputs first when
replaying elsewhere. Output paths must not exist. Replay observation timestamps
differ; original source fields remain unchanged. Owner-upload byte consistency
does not independently authenticate the original publisher. Derived research
data retains ODbL1.0 attribution to OpenStreetMap contributors and stays separate
from personal contacts and application code.
