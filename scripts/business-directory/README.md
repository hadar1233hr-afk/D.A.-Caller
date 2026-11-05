# Public business directory build tools

These scripts build only DA CALLER's independent public business dataset. They
never read Android contacts, call logs or the private call archive. No populated
production phonebook asset has been installed in the app. The app has no network
permission; data preparation happens before release.

## Current acquisition status

**Direct publisher acquisition remains BLOCKED; an owner-uploaded source is now
retained separately. No production records are bundled.** The configured proxy
returned HTTP403 for the normal Geofabrik source HEAD request
on2026-11-02. The current environment's supported allowlist still excludes
`download.geofabrik.de`. No alternative proxy, remote runner, mirror, direct route,
account or credential was used to bypass it. The recorded attempt is in
[acquisition-status.json](acquisition-status.json).

On2026-11-05 the owner uploaded a ZIP to this repository's existing release
(asset712722687). The recorded GitHub transfer has120,936,686 bytes and
SHA256`578d67213db0cb617f18ccd12c60eab5c532f073fa5520b399d9f5d36973b11e`;
its single PBF has121,050,024 bytes and
SHA256`62c6ff161b738588454719f0fd1c4971c9b46535f4ab04313e7bbe1dd582afdf`.
These identify retained owner-supplied inputs; they do not authenticate a download
from Geofabrik. Source origin is declared OSM/Geofabrik, and the original
publisher transfer has **not** been independently verified. Extraction and quality
measurements are retained under `docs/evidence/business-acquisition/owner-261103/`;
do not infer national coverage or current phone ownership from the file size.

Direct publisher acquisition needs the supported environment configuration to
permit the publisher's exact host. This repository cannot change that network policy.
Other researched licensed sources, and their limits, are in
[ALTERNATIVE_SOURCES.md](ALTERNATIVE_SOURCES.md). `query_overture_israel.sql` is an
unexecuted candidate, not proof of imported records. Around's search API must not
be enumerated across the country; its developer guidance forbids that use.

## Reproducible preparation after source access is available

Use a private workspace outside the repository for source snapshots, the virtual
environment and generated outputs:

```sh
python3 -m venv /workspace/work/business-data-venv
/workspace/work/business-data-venv/bin/pip install -r scripts/business-directory/requirements-lock.txt
/workspace/work/business-data-venv/bin/python scripts/business-directory/fetch_geofabrik.py /workspace/work/business-data/source-2026-11-04.osm.pbf
/workspace/work/business-data-venv/bin/python scripts/business-directory/extract_osm_businesses.py /workspace/work/business-data/source-2026-11-04.osm.pbf /workspace/work/business-data/extracted-2026-11-04
python3 scripts/business-directory/package_app_dataset.py /workspace/work/business-data/extracted-2026-11-04 /workspace/work/business-data/source-2026-11-04.osm.pbf.receipt.json /workspace/work/business-data/packaged-2026-11-04 --dataset-version osm-il-2026-11-04
```

The fetcher retains inherited proxy and CA trust, refuses redirects and existing
source/receipt names, writes an acquisition receipt and stops on HTTP failure.
The extractor is offline and preserves source names, URLs, object IDs, timestamps,
original phone values, extensions and exact measured counts. It filters business
and public-service categories, excluding private/residential-only and inactive
objects. No fax tags, map contributor identities or private user contacts enter
the dataset. Syntax/allocation validation is **not** proof of current business
ownership, and a recent extract can contain an old source object.

The default `explicit-il` scope requires a source country tag identifying Israel.
The combined Israel-and-Palestine extract is not an Israel-only boundary. The
extractor's optional combined-region/Israeli-phone mode is retained for research;
the app packager deliberately refuses to ship it as an Israeli business dataset.
Source rows with invalid names, missing object dates or incompatible dial identities
are omitted and counted. Exact base-number extensions are deduplicated for app
display; their original details remain in the downloadable source NDJSON. An
extension is never appended silently to a call.

## App contract and publication

The packager verifies the matching successful source receipt, actual byte count,
source hash, extracted SQLite/NDJSON hashes and exact NDJSON row count. It refuses
empty datasets, inflated declared counts, conflicting source/license/country
provenance and existing output directories. A checksum binds retained inputs; it
does not independently prove phone ownership. Source review remains necessary.

### Explicit owner-upload preparation

An owner upload must never be rewritten as a successful publisher HTTP200
receipt. The default `publisher-download` mode still requires its existing
successful publisher receipt. To prepare an owner-supplied source deliberately,
use the separate mode and retain the original ZIP and its PBF:

```sh
python3 scripts/business-directory/package_app_dataset.py /workspace/work/business-data/extracted-owner-261103-explicit-il /workspace/work/business-data/owner-upload-receipt.json /workspace/work/business-data/packaged-owner-261103 --dataset-version osm-il-owner-261103 --acquisition-mode owner-upload --source-archive /workspace/work/business-data/israel-and-palestine-261103.osm.zip --source-pbf /workspace/work/business-data/israel-and-palestine-261103.osm.pbf
```

This command is a preparation example, **not** evidence it has been executed or
that its output has been approved or installed. The selected receipt must identify
an owner-uploaded asset in this DA CALLER repository, explicitly state that
publisher provenance is not independently verified, and contain matching archive
and PBF byte counts/SHA256s plus the recorded ZIP member. Both actual files are
required; receipt booleans alone are insufficient. The tool verifies the retained
ZIP's one root PBF, reads it with CRC checking and matches its bytes/hash to the
retained PBF and extraction manifest. Existing source/license, explicit-IL,
per-record country, extraction hash, row-count and nonempty output checks apply.

Generated manifest, SQLite metadata, notice and packaging report retain
`method=owner-upload` and `publisher_download_authenticated=false`. This is local
input-consistency evidence, not independent publisher or phone authentication.
The exact original transfer receipt is included in the redistribution files.
Source/license review and exact-dataset native validation remain required before
an app bundles it. DA CALLER0.12.0 excludes the business directory; these tools do
not change that release scope or any APK publication/signing gates.

Its `app-assets/business-directory/` contains the exact backend contract:

- `israel-businesses.sqlite`: schema version1, `businesses` with stable positive
  numeric IDs, source attribution and base dial identities; indexed normalized
  phones; FTS4 `unicode61` search supported by Android26+.
- `manifest.json`: exact SQLite SHA256, measured record/unique-phone counts,
  dataset version, source/license URLs, extraction time and truthful coverage limits.
- `LICENSE-NOTICE.txt`: offline attribution, data license and source/accuracy notice.

Only search-index fields are normalized for unpointed Hebrew/diacritic searches;
the display names retain source spelling under Unicode NFC. No data is loaded
into the app automatically. Copy assets into `app/src/main/assets/` only after
source/provenance review, then test that exact bundled dataset in Android. Never
copy temporary test fixtures into production assets.

The dataset derives from OpenStreetMap and is offered under
[ODbL1.0](https://opendatacommons.org/licenses/odbl/1-0/), with
`© OpenStreetMap contributors` attribution and the
[OpenStreetMap copyright notice](https://www.openstreetmap.org/copyright).
Keep the independent public database separate from private contacts and program
code. Publish the exact `redistribution/` contents (derived SQLite, manifest,
notice, original extracted source NDJSON, source manifest and acquisition receipt)
alongside any distributing release, without access restrictions. Publish this
transformation source too. Include the applicable license text and source offer
when preparing the release; do not claim ODbL compliance from a hash alone.

## Actual checks

```sh
cd scripts/business-directory
/workspace/work/business-data-venv/bin/python -m unittest -v test_extractor.py test_package.py test_owner_upload.py
```

On2026-11-05, **37 local tests passed**, zero failures/errors/skips, using the
already installed pinned research environment. They check phone/country safety,
actual libosmium fixture parsing, source/hash/count refusal, exact Android SQLite
and manifest schema, FTS Hebrew-point handling, stable identity, extensions and
no-overwrite behavior. Fourteen owner-upload cases additionally cover explicit
mode selection, missing inputs, byte/hash/ZIP-member tampering, dishonest HTTP or
publisher claims, wrong repository/host, retained license/country/count checks,
truthful output provenance and no overwrite. All fixtures are temporary and
explicitly synthetic. The log is `/workspace/work/business-upload-support-tests.log`.

Real owner-source extraction is measured separately from these synthetic tests.
Production bundle queries/performance, independently confirmed current phone
ownership and complete country coverage remain **NOT TESTED**. No100,000/million-
number or complete-Israel claim is made.
