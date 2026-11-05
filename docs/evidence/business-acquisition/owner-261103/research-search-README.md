# Offline research search staging, outside the app

The exact audited NODE/WAY/RELATION source cohorts now have an offline SQLite/FTS4
research stage. This is not the app's `businesses` schema, not production admission
and not a business-enabled APK. Application code/assets, user contacts/history/
archive, production packaging, signing and publication safeguards are unchanged.

The6,844,416-byte database retains all2,696 exact original NDJSON lines, including
line hashes and cohort order. Source identities, original phones, extensions,
names, scopes and raw country fields stay intact.2,680 country values remain
null; no country is inferred from phones or coordinates. A separate search
projection has2,693 text-safe rows. Default search includes2,683 rows with
unchanged phone identities;10 same-E.164 UAN1599 formatting proposals require an
explicit research opt-in. Three source-text rows with Cf controls are retained
in full but excluded from search. Nothing is silently repaired, merged or lost.

National dial-form proposals live in a separate column from source phones.
Searchability/proposals never set `app_accepted`: every row and metadata remain
research-only. A row CHECK enforces0; original source/base/extension keys remain
distinct. An explicit integer search ID keeps FTS/source bindings stable through
VACUUM. Full source lines can be reconstructed byte-for-byte for all three cohorts.

The build binds actual PBF/corpus bytes, retained successful source audits,
source-geometry scopes, helper/normalizer/lock hashes and runtime versions. It
snapshots before validation and rechecks before atomically retaining a fresh
output; existing output cannot be overwritten. This is consistency with retained
evidence, not new publisher authentication or live phone ownership verification.

All25 synthetic contracts passed and were independently rerun. They cover raw
bytes/null country/phone/extension preservation, Cf withholding, genuine explicit
phone-proposal opt-in, invalid phones, duplicate keys, shared phones across source
objects, Hebrew/English diacritics, formatted phone search, literal operators,
deterministic keyset paging, read-only file hashes, sparse-row VACUUM identity,
audit/corpus/PBF mismatches, required hole-aware relation proof, no overwrite and
source mutation leaving no partial output. An initial positive synthetic sample
used an allocation-invalid number; the corrected test uses the library's example
and now separately asserts that the invalid sample is retained outside search.
No actual source number was changed to make a test pass.

[Independent actual database audit](research-search-independent-audit.json)
checked every one of2,696 original lines and source projections, all2,693 FTS
bindings, all metadata/code/source-audit hashes and SQLite integrity/foreign keys.
Known Hebrew and phone queries matched their source identities; explicit UAN
opt-in/default exclusion worked.33 actual keyset pages covered1,217 shop matches
with zero repeats/omissions. The file stayed byte-identical after queries.
The plan uses an FTS virtual index and integer-primary-key lookup, plus a temporary
sorting B-tree. This is desktop SQLite evidence, not Android UI/hardware or a
million-record performance result. Shared phone/text/normalizer references are
disclosed; source-PBF authenticity was not independently repeated by this DB audit.

[Manifest](research-search-manifest.json), [25-check log](research-search-checks.txt),
[retention hashes](research-search-retention.json) and
[exact database+manifest+audit+checks+ODbL notice ZIP](research-search-stage.zip)
retain the actual final v2 bytes. A private initial v1 is historical only; final
v2 was rebuilt after the code froze and independently checked. Database SHA256:

```text
b8d5f5209ea87969a1b42d3907e73f99466f869f7df2afcd8d7bb4eeb4dda274
```

From the repository root, using a fresh output path and recreated/unpacked exact
source cohorts plus the owner PBF:

```sh
/workspace/work/business-directory-research/.venv/bin/python scripts/business-directory/research/check_research_sqlite.py
/workspace/work/business-directory-research/.venv/bin/python scripts/business-directory/research/stage_research_sqlite.py build /workspace/work/business-data/israel-and-palestine-261103.osm.pbf /workspace/work/business-data/node-geography-owner-261103-v2/research-node-businesses.ndjson /workspace/work/business-data/way-geography-owner-261103-v1/research-way-businesses.ndjson docs/evidence/business-acquisition/owner-261103 /workspace/work/new-research-search --relation-corpus /workspace/work/business-data/relation-geography-owner-261103-v2/research-relation-businesses.ndjson
/workspace/work/business-directory-research/.venv/bin/python scripts/business-directory/research/stage_research_sqlite.py query /workspace/work/new-research-search/research-search.sqlite "אג׳נדה"
```

Production country-admission/naming/phone decisions and future native integration
remain separate. Source object counts are not distinct verified physical
businesses, historical timestamps do not confirm current ownership, and no
national completeness claim or actual call validation is made. APK0.12.0 remains
without business data and still needs the previously requested publication approval.
