# Two exact source-name selections — 2026-11-11

This outside-app derivative recovers two previously name-withheld rows by
selecting their original OSM `name` tag. It does not strip formatting controls,
translate/reverse words or rewrite the preferred `name:he`. Original raw records,
all 2,728 historical decisions and the prior 2,715 business/FTS rows remain intact.
The [technical selection review](../../../releases/business-source-name-selection-review.md)
is not owner authorization, production admission or publication approval.

| Source | Exact selected source `name` | Original object date |
| --- | --- | --- |
| `osm:node:5801536067` | دار كنا الضيافة | 2019-05-04 |
| `osm:node:12543641901` | R. Gallery Ltd | 2025-01-30 |

Both rows retain the exact source phone, URL, timestamp, null country and scope.
Their unsafe preferred Hebrew names remain in the original BLOBs and historical
ledger. A separate two-event source-name ledger and SQL table identify the
selected tag/value, old withholding, raw-line/report hashes and new projection.
The historical decision table still has three text-withheld rows; the derivative
adds two explicit selections rather than changing that history. Its effective
remaining holds are ten phone-format rows and one unsafe city/address row.

The actual output measures **2,717 rows / 2,657 source objects / 2,614 normalized
phones**, with 77 shared-phone groups retained separately. 2,701 projected country
fields remain null; no country was inferred. 1,508 rows lack a city and 820 lack
an address. Dates still span 2010–2026. These are source records, not verified
distinct premises, current phone owners, active businesses or nationwide coverage.
[Measured counts](source-name-measured-coverage.json).

Database: 6,643,712 bytes, SHA256:

```text
36a7c0a07fad679249c44c975cba7c8d4fb00171d593d19f4ff0c6413a0f115a
```

The reader manifest SHA256 is
`de06f2770c48e4da65966c1dda7a8fd4e1af8596698f3537652259827bcdebe5`.
The immutable [parent v2](business-candidate-README.md), DB SHA `4eee8e84...`,
remains unchanged and retains its independent earlier evidence.

All **24 data-tool methods PASS**, independently rerun by the root agent.
Cases cover exact source association/tag/phone/history, no sanitization or
country relabelling, malformed text and UTF-16 bounds, no old projector/guard
call, no overwrite, source/code drift and app-admission CHECK rejection.
Review fixed snapshot ordering and immutable-hash anchor replacement: executing
code/check/policy are bound before input validation, and later observations
cannot replace pinned expected hashes. Drift regressions pass. [Final log](source-name-checks.txt).

The [independent actual-data audit](source-name-independent-audit.json) checked
every old raw/history/FTS row and the two new 12-column projections/events, all
49 original bindings plus new source/code/policy bindings, stable IDs, current
withholding, 55 complete pages, Arabic/Gallery queries and six in-memory
corruption/CHECK cases. It never imported the derivative producer/projector.
Native-compatible text/phone/FTS helpers are disclosed shared references. No new
PBF object reread or live source/phone verification occurred.

Seven genuine **HOST_ROBOLECTRIC_API33_NATIVE_SQLITE** tests PASS, zero failures,
errors or skips in retained JUnit. The actual unchanged reader loaded all 2,717
rows in 55 pages, checked 10,368 generic phone-alias queries/2,614 phone groups,
77 shared groups, both exact selected source IDs/names and five explicit name
queries, including Arabic `دار`/`كنا` and `R. Gallery`. Two additional selected
phone lookups passed. Manifest/checksum/schema/count/metadata/FTS rejection and
read-only synthetic cache canary checks remain included. This first run completed
successfully in 38 seconds. [Host review](source-name-host-review.json) and
[exact JUnit/Java/init/command/log/observations](source-name-host-checks.zip).
These are host checks, not emulator, Telecom, UI, hardware, real-user archive,
upgrade or native-integration approval. No new app APK was built or altered.

[Exact derivative/source/history/evidence ZIP](source-name-reviewed.zip) and
[all member hashes/status](source-name-retention.json) retain final v1. Original
source proofs, ODbL notice and machine-readable raw/decision data remain available.
The owner PBF/ZIP hashes were rechecked through the parent bindings; ZIP CRC/member
validation is inherited from the parent execution, **not newly performed here**.
There was no publisher download or new external-source acquisition.

Reproduce with the recorded source/parent environment and a new output path:

```sh
/workspace/work/business-directory-research/.venv/bin/python scripts/business-directory/research/check_source_name_candidate.py
/workspace/work/business-directory-research/.venv/bin/python scripts/business-directory/research/derive_source_name_candidate.py /workspace/work/business-data/business-candidate-owner-261103-v2 docs/evidence/business-acquisition/owner-261103/text-readiness-report.json /workspace/work/new-source-name-candidate
```

The ZIP also retains the independent auditor's exact executed source at
`tools/withheld-final-review-audit.py`; it uses the recorded `/workspace/D.A.-Caller`
repository path and takes parent, derivative and new output JSON paths. The host
ZIP retains its exact external-source init and command. Restore their recorded
paths to reproduce this bounded snapshot; no app asset copy is part of the flow.

The nonstandard candidate filenames/outer envelope remain NOT_ADMITTED with
production/publication false and app records added zero. The current reader
ignores unknown candidate flags, so those markers are preparation controls,
not a runtime admission boundary. Never copy this candidate to app assets.
DA CALLER 0.12.0 stays business-free; existing signing/CI guards and the pending
explicit publication exception are unchanged. Future production admission and
genuine business-enabled emulator UI/dial validation remain pending.
