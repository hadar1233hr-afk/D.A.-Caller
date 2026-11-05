# Uncovered source-category inventory, no new geographic admission

The unchanged existing category contract misses some public-service/commercial
category values. This bounded inventory reads named, phone-bearing `amenity`,
`leisure` and `club` source objects rejected by that contract. It preserves exact
source tags/IDs/URLs/versions/timestamps, original phone fields and parsed phone
candidates. Actual point coordinates, ordered way references or original relation
members remain available for later complete geometry review.

The combined Israel-and-Palestine owner-upload snapshot yielded **353 source
objects:209 nodes,141 ways,3 relations** after conservative explicit private/
residential/inactive exclusions.325 objects have at least one numbering-plan
candidate,331 candidate phone rows and316 distinct base candidates. These are
not geographically admitted Israeli contacts or verified distinct businesses.
The original2,696-row audited research/search stage is unchanged.

Measured source values include29 `fitness_centre`,23 `sports_centre`,11
`driving_school`,12 `events_venue`,7 `internet_cafe`,5 `marketplace` and4
`escape_game` observations. Other values are ambiguous/noncommercial:86 schools,
29 kindergartens,26 places of worship, parking, clubs and others. Multiple category
fields may appear on one source object, so category observations must not be added
as distinct businesses. Misspellings and multi-values remain raw, without guessed
correction. A strict commercial/public-service whitelist is still a separate
review decision; this inventory does not promote schools, clubs or pools.

One explicit private/no-access object and one explicit residential object were
excluded. Source-tag exclusions are conservative, not proof of private ownership
or inactivity.338 retained objects lack an explicit access tag; absence never
becomes public access.141 educational objects,9 clubs and25 leisure facilities
retain explicit publicness/commercial-status ambiguities.350 objects have no
country fields, with no invented tag. The3 country-tagged objects retain IL/Israel
source values; phone prefixes and tags alone do not establish geographic scope.
Dates span2015–2026 and do not confirm present activity or phone ownership.

[Full report](category-gap-report.json), [retention/hash checks](category-gap-retention.json)
and [exact source objects/report/ODbL notice ZIP](category-gap-source.zip) retain
the measured source records. The source PBF SHA256 is
`62c6ff161b738588454719f0fd1c4971c9b46535f4ab04313e7bbe1dd582afdf`.
All records explicitly remain category-gap inventory only, with geographic/app
admission false and country inference false. No contributor user IDs/names were
read. Raw OSM tags remain as source evidence.

Eighteen actual synthetic privacy/exclusion/ambiguity contracts passed. Producer
retention assertions rechecked exact hashes/counts/current-category rejection,
raw phone/country fields, explicit exclusions, non-admission, ZIP contents/CRC and
member hashes. These checks are not independent source-authenticity, topology,
Android/native or live phone verification. No calls or network requests occurred.

From the repository root with the existing locked research environment:

```sh
/workspace/work/business-directory-research/.venv/bin/python scripts/business-directory/research/category_gap_inventory.py check-policy
/workspace/work/business-directory-research/.venv/bin/python scripts/business-directory/research/category_gap_inventory.py inventory /workspace/work/business-data/israel-and-palestine-261103.osm.pbf /workspace/work/business-data/owner-upload-receipt.json /workspace/work/new-category-gap-inventory
```

Use a fresh output directory. The tool binds the actual PBF/owner-upload receipt,
executed helper and unchanged category/phone extractor; their bytes are checked
again before output. Replays have new observation timestamps. Data remains ODbL1.0,
© OpenStreetMap contributors, separate from app code and personal contacts.
No app/production category/packager/signing/CI/release guard changed;0.12.0 stays
without businesses and its existing publication approval question is unaffected.
