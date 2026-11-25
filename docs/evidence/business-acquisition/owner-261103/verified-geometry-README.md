# Source-spatial business directory for future native evaluation

This is the new clean source-spatial projection of the owner's November 3, 2026
OpenStreetMap extract, not a renamed research/candidate database. It is licensed
under [ODbL 1.0](https://opendatacommons.org/licenses/odbl/1-0/),
**© OpenStreetMap contributors**. The corresponding raw records, history,
decisions, source evidence and reproducible derivative materials are distributed
in [verified-geometry-source-and-dataset.zip](verified-geometry-source-and-dataset.zip),
including its `redistribution/` directory. The app's About data screen identifies
the contributors, license, source snapshot and actual record count.

The already delivered 0.12.0 APK remains immutable and business-free. This dataset
is being prepared for **0.13.0 / code 22 development**. Data/host verification is
separate from emulator Telecom, screenshots, visual review, signing and release
approval. No new verified APK or physical-device/update test is implied here.

## Measured coverage

- **2,720 records**, **2,660 source objects**, **2,616 whole dial identities**;
  78 shared-phone groups remain separate source records, without merges.
- All **2,728 original raw rows / 2,668 objects** and their historical decisions
  are retained. Eight rows remain withheld: one restricted-access office, six
  residential building flags and one unsafe city/address.
- Ten original 1599 service-number rows are admitted with unchanged source E.164
  numbers. Java/Python business-only phone v2 preserves the national identity
  without manufacturing a leading zero. Two explicitly reviewed primary source
  names are retained in Arabic/English; unsafe historical preferred names remain
  in the raw history.
- **2,704 admitted rows have NULL source-country fields**; they remain NULL.
  Admission uses complete source geometry strictly inside the reviewed source IL
  relation, outside the filled excluded source regions and their 0.001-degree
  angular margin. This is an operational source-spatial filter, not an official
  or legal border determination. No source country or old scope was fabricated.
- Source snapshot: **2026-11-03**; individual admitted source edits range from
  **2010-11-25** to **2026-11-03**. The original file was uploaded by the owner;
  transfer/ZIP CRC/member hashes were checked, but publisher authentication is
  false. There is no live reachability, present operation or phone-owner check.
- Listed categories include shops, restaurants and public services. Counts by
  category/cohort and missing data are in `redistribution/coverage.json`.
  Coverage is partial; a national denominator/percentage is unavailable.

## Exact final data inputs

| File | Bytes | SHA-256 |
| --- | ---: | --- |
| Clean `israel-businesses.sqlite` | 1,363,968 | `cbc7dc7b41c3fa9335cbf37907243681820fe23107e40541efb306dc1b566af3` |
| Native `manifest.json` | 500 | `fec348522615f62d36fa62ee65ff317340ea4806a85f15c457daa30378805cfa` |
| Source and dataset ZIP | 3,169,035 | `5d6358c7d8ebc7b3be51717c3829a1b3c56e0f1aacf90bbf54fa1a04f71a5224` |

The ZIP includes the clean assets, exact raw/history, new admission ledger, fresh
PBF tags/geometry replay, source receipt, coverage and 167 input bindings. The
production reader is separately bound at SHA
`5d8c3d01e4aab6f5dd198b4c2fe588e301c7298003192c21ec97332e279efa52`.
The one historical reader reference resolves to its exact retained old source;
it is not silently applied to the new reader. Earlier candidate files/helpers
and their original strict country-tag admission remain unchanged.

## Verification and reproduction

[Independent data review](verified-geometry-independent-review.json) and
[retained auditor/materials](verified-geometry-independent-materials.zip)
record a full independent PBF/geometry/field/phone/history/FTS/archive replay.
It independently reread 2,668 source objects plus four boundary relations,
489 supporting ways and 16,516 supporting nodes, and checked all projected
12-column fields, source publicness flags, source countries and both exact name
fallbacks. It checked all 167 bindings, source ZIP/member CRC/hash and every
redistribution member. The 24 exporter negative contracts and all 68 current
business Python methods passed. These are data-tool results, not emulator tests.

[Final actual-reader host review](verified-geometry-host-review.json) and
[exact host checks/JUnit/reproduction](verified-geometry-host-checks.zip) passed
**nine API33 Native SQLite host methods**, with zero failures/errors/skips:
2,720 rows across 55 complete pages, 2,616 dial identities, 78 shared groups,
13,080 generic phone queries, 32 explicit UAN alias queries, exact Arabic/English
names, all eight withheld source rows absent and corrupt-data/cache guards.
The current reader, final database and manifest hashes were checked before/after
the actual run. These are host checks, not emulator/UI/Telecom/upgrade checks.

After both independent data and final actual-reader checks passed, only the
clean three native assets were copied into `:app` for 0.13.0 development and
its separate Contacts business tab was enabled. No research/history tables were
copied into the app. Fresh whole-app/native/visual verification remains separate.

The new exporter explicitly consumes the frozen
[admission policy](../../../business/source-geometry-admission-261103.md).
With the retained original paths restored, run:

```sh
python scripts/business-directory/package_verified_geometry_dataset.py \
  --candidate /workspace/work/business-data/source-name-candidate-owner-261103-v1 \
  --evidence docs/evidence/business-acquisition/owner-261103 \
  --pbf /workspace/work/business-data/israel-and-palestine-261103.osm.pbf \
  --policy docs/business/source-geometry-admission-261103.md \
  --output /workspace/work/business-data/verified-geometry-package-new-run
```

Use the locked Python dependencies, including phonenumbers 9.0.42. The output
must be a new outside-app directory and will not overwrite an existing package.
The source-code/policy hashes are checked before validation and again before
atomic retention. Observed times/ZIP bytes may differ on reproduction; compare
actual database, fields and new run bindings rather than inventing byte equality.

The immutable v2 producer report correctly records its then-pending independent
and host checks and `app_assets_installed:false`. Later separate evidence records
actual integration/verification; the original report is not rewritten into a
claim that those later operations had already happened.

Future publication still requires its applicable signing safeguards and fresh
native/visual/public-byte verification. The owner's 0.12.0-only exception cannot
publish this dataset or another version. Physical Jelly Star/carrier/audio and
matching-key update checks remain NOT TESTED. Never uninstall an existing app:
that deletes its private archive and settings.
