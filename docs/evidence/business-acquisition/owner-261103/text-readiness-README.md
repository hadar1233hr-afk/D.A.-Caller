# Source-text research review

**Research only. No records, names, countries or source fields were rewritten or
admitted to the app.** The production extractor/packager, Android source/assets,
signing and publication gates remain unchanged. No Android/native/hardware test
or release approval is claimed here.

The helper independently checks all seven production text-field bounds over the
unchanged2,448-row geographic research corpus, including fields that the
production packager does not reach after its earlier country/scope rejection.
It found3 affected rows and4 affected fields:2 names,1 city,1 address. Every
failure is a Unicode `Cf` formatting control; **none is oversized**. The earlier
production first-failure histogram of2 source-text failures remains correct:
the third row's city/address problem is hidden behind its scope/country guard.
Do not bypass that guard to test or admit it.

| Source node | Field and length after NFC/strip | Exact cause | Source-provided name alternatives, review only |
| --- | --- | --- | --- |
| [5801536067](https://www.openstreetmap.org/node/5801536067) | name20/300 | Three U+202B RIGHT-TO-LEFT EMBEDDING and three U+202C POP DIRECTIONAL FORMATTING | `name:en`: `Cana Guest House`; `name`/`name:ar`: `دار كنا الضيافة` |
| [12543641901](https://www.openstreetmap.org/node/12543641901) | name15/300 | Leading U+200E LEFT-TO-RIGHT MARK | `name`/`name:en`: `R. Gallery Ltd` |
| [5066707334](https://www.openstreetmap.org/node/5066707334) | city9/300; address29/1200 | Trailing U+200E from original `addr:city`, propagated to the constructed address | No name failure; this is a city/address review issue, with no replacement proposed |

The first Hebrew name contains separate embedded words. Removing formatting
marks or reversing words could change its visible/logical meaning. The report
retains the original exact string and each offending code point/index, never
strips or reorders it. Exact clean alternative name values are retained with
their original tag keys and source timestamp, marked
`REVIEW_ONLY_NOT_SELECTED_OR_APP_ACCEPTED`. No alternative was selected. The
independent source reread found **no `short_name` tags** for these3 nodes.

[The machine-readable report](text-readiness-report.json) retains all3 original
research records, every independently reread raw node tag, field-by-field bounds
observations and exact source alternatives. Non-ASCII/control characters are
JSON-escaped to make bidi markers inspectable. Original text is preserved;
NFC/strip is an observation matching production behavior, not a source rewrite.
The source timestamps are historical:2019 for node5801536067,2025 for12543641901,
and the exact timestamp for5066707334 is retained in its row. Source-listing does
not confirm current business names, phone ownership or nationwide coverage.

The actual reread used pyosmium's C++ `EntityFilter(NODE)` plus `IdFilter` on the
owner-supplied PBF, so Python callbacks visited only the3 selected IDs. The
source and corpus SHA-256 values are respectively:

```text
PBF:    62c6ff161b738588454719f0fd1c4971c9b46535f4ab04313e7bbe1dd582afdf
Corpus: 37ce40b735e144cc38eee1e72ce75c785424f0213d05c9c35feb766c1b2704df
```

The helper/report also bind their production-packager and own source hashes.
Every supplied reread node must match its retained preferred raw name tags and
exact source ID/URL/timestamp, including city-only failures and clean rows.
Alternative name keys are limited to exact `name`/`short_name` and their explicit
`he`, `ar`, `en` language tags. Metadata keys such as `name:pronunciation` and
`name:etymology` are retained raw but never proposed as display names. No other
language or locale is guessed. The unchanged
corpus and app-accepted count0 were separately asserted.

Actual checks: **29 unittest methods PASS,0 failures/errors/skips**, with
additional corpus/report/hash/source-candidate assertions PASS. Cases cover
exact300/301 name lengths and all other field limits, NFC code-point length,
required/optional empty values, outer whitespace versus inner `Cc` controls,
`Cf` bidi/format marks, non-string values, independent parity with unchanged
production `bounded_text`, hidden city/address errors, immutable originals,
exact source-only alternatives, all-row source association, metadata-key and
unsupported-language rejection, no truncation/reversal,
no country filling/admission and exclusive report creation. These synthetic
research checks do not replace app/native/visual verification. See
[check result and hashes](text-readiness-checks.json).

Reproduce from the repository root using the existing research venv for osmium:

```sh
python scripts/business-directory/research/check_source_text_review.py
/workspace/work/business-directory-research/.venv/bin/python \
  scripts/business-directory/research/source_text_review.py \
  --corpus /workspace/work/business-data/node-geography-owner-261103-v2/research-node-businesses.ndjson \
  --source-pbf /workspace/work/business-data/israel-and-palestine-261103.osm.pbf \
  --output /tmp/da-caller-source-text-new-report.json
```

Use a new output path: the helper refuses to overwrite retained evidence.
Any future production naming/text policy still needs separate review; this
classification does not change the existing production contract.
