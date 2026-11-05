# Conservative NODE geography research

This offline tool prepares **research candidates only** for DA CALLER. It does
not change the application, the production packager, signing or publication gates.
The original24-record explicit-address-tag package remains unchanged, and0.12.0
still contains no business data.

The retained261103 result has2,448 raw phone rows from2,393 source nodes and2,353
distinct base numbers. Source names, original/canonical phones, country fields,
coordinates and object timestamps were independently checked against the PBF.
This proves source fidelity, not a live business, current phone owner, official
country determination or complete national coverage. Source dates span2010–2026.

Admission requires a point strictly inside the complete source IL polygon and
outside BOTH filled WestBank outer envelopes and the source Gaza polygon, with
an angular0.001-degree margin around boundaries. Degrees are not exact meters.
Ways/relations are omitted because their complete geometry was not evaluated.
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
