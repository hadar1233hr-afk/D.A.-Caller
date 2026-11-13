# Combined-extract WAY/RELATION source inventory

This is **source research only**, not an Israeli directory or geographic
admission. The owner-upload PBF contains Israel and Palestine. Every object here
still needs complete-geometry country review and phone/text checks before any
future app integration. No source country tag, name, phone or coordinate was
rewritten. No app, production packager, signing, workflow or release gate changed.

The category filter and preferred-name/phone parsing use the unchanged existing
extractor. This inventory measured369 named, phone-bearing public-category ways
and5 relations. All369 ways have reference-closed structural rings with all
referenced coordinates available. This proves dependency availability only:
ring closure is not proof of valid polygon topology, geographic membership,
current business activity or actual phone ownership.

- Ways:342 have numbering-valid phone candidates,349 parsed phone rows.
- Ways:11 source `area=yes`,358 missing `area`,0 source `area=no`.
- Country review:2 ways have explicit IL aliases,356 have no country field and11
  have a non-IL country field under the deliberately broad research policy.
  Diplomatic `country` can mean represented state; a non-IL value is not proof
  of a foreign physical address. All raw tags remain retained.
- All5 relations have missing country fields and `type=multipolygon`; their
  direct members include7 `outer` and3 `inner` way roles. All10 referenced ways
  and all selected node coordinates were recovered. Their holes are not filled,
  recursive/role-aware assembly is not performed, and no relation is admitted.
- A total of3,944 selected source nodes were recovered,0 missing.

The report distinguishes prefiltered objects, qualifying categories, missing
names, named phone-bearing objects, country-tag observations, parsed candidate
availability and reference/coordinate completeness. These are source counts,
not a count of distinct physical Israeli businesses. No shared-number or
name/address merge is performed. Historical object timestamps remain intact;
source-listed numbers are not checked by making calls or live network lookups.

[Report](way-inventory-report.json), [retention/checks](way-inventory-retention.json)
and [compressed source objects plus geometry dependencies](way-inventory-source.zip)
retain source and code hashes. The source PBF SHA256 is
`62c6ff161b738588454719f0fd1c4971c9b46535f4ab04313e7bbe1dd582afdf`.
The ZIP's3 data files are `source-objects.ndjson` (full tags, original phones,
ordered node refs or relation members), `geometry-nodes.json` (exact selected
source coordinates/timestamps) and `relation-member-ways.json` (ordered refs and
availability only). Each record is marked research-only/not app accepted.

From the repository root, using the existing locked osmium/phonenumbers venv:

```sh
/workspace/work/business-directory-research/.venv/bin/python scripts/business-directory/research/way_source_inventory.py /workspace/work/business-data/israel-and-palestine-261103.osm.pbf /workspace/work/business-data/owner-upload-receipt.json /workspace/work/new-way-source-inventory
```

The output directory must not exist. The tool reads local bytes only, checks
the owner-upload receipt's exact PBF hash/size, runs C++ object/key filters then
bounded C++ ID filters for dependencies, and records output/code hashes. It
never requests network access or changes app data. Replays have a new report
observation timestamp; the three source-data files can be compared byte-for-byte.
Producer hash/size consistency is not independent source authentication or an
independent topology audit. Source rights and attribution remain ODbL1.0,
© OpenStreetMap contributors; see the retained license notice inside the ZIP.

DA CALLER0.12.0 remains without businesses. None of these objects has passed
geographic admission or entered an app asset, and this report does not approve
APK publication or change the pending signing/CI exception.
