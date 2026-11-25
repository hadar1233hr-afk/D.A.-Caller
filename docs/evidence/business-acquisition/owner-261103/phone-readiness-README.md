# Phone identity research: seven 1599 rows

This is a read-only review of the exact 2,448-row owner 261103 research snapshot.
**No rows were added to DA CALLER. No source values, country tags or production
guards were changed.** The 0.12.0 APK and signing/publication restrictions remain
outside this research work.

The unchanged production `record_values` rejects all 2,448 rows: 2,439 encounter
its country/scope guard, 7 encounter its phone-identity guard and 2 encounter its
source-text guard first. These are first-failure counts, not proof that a row
has only one problem. The [measured report](phone-readiness-review.json) retains
all seven complete original records, exact source forms and separate proposals.
Its input SHA256 is
`37ce40b735e144cc38eee1e72ce75c785424f0213d05c9c35feb766c1b2704df`.

All seven phone failures are valid **UAN 1599** numbers according to the pinned
`phonenumbers 9.0.42` numbering-plan metadata. There are five unique E.164 base
identities across these seven nodes. The current app normalizer adds a trunk 0
when converting any `+972` prefix. For these service numbers that would produce
`01599…`, which does not meet its existing dial grammar. The library's national
format instead gives `1599…`. Parsing that national form again yields the exact
same E.164 base identity as both the source-audited canonical phone and the
original source phone. This supports a **research formatting proposal**, not a
change to the phone number or admission to the app.

|Source node|Source-listed name|Exact original source phone|Research national-form proposal|
|---|---|---|---|
|[1753285823](https://www.openstreetmap.org/node/1753285823)|קקאו|`1599555535`|`1599555535`|
|[5291178729](https://www.openstreetmap.org/node/5291178729)|משרד העלייה והקליטה|`1599-500-901`|`1599500901`|
|[9970152588](https://www.openstreetmap.org/node/9970152588)|משרד העלייה והקליטה|`1599 500 907`|`1599500907`|
|[10001924959](https://www.openstreetmap.org/node/10001924959)|Bershka|`1599510510`|`1599510510`|
|[11881331125](https://www.openstreetmap.org/node/11881331125)|אלתר ספרים|`+9721599554949`|`1599554949`|
|[12625537932](https://www.openstreetmap.org/node/12625537932)|סטרדיווריוס|`+972 159 951 0510`|`1599510510`|
|[14046803650](https://www.openstreetmap.org/node/14046803650)|סטרדיווריוס|`+972 159 951 0510`|`1599510510`|

All seven original country tags remain null and country-field objects remain
empty. A phone's Israeli numbering-plan identity does not establish the business's
physical country. Even a hypothetical national-form copy still fails the unchanged
production country/scope guard. Shared numbers are not proof of duplicate physical
businesses; three nodes share 1599510510 and require the separate identity review.
Extensions are retained separately and never appended to a proposed dial string.

The helper exposes `review_phone_identity(record)` and `review_corpus(path)`;
results are always `RESEARCH_ONLY_NOT_APP_ACCEPTED`, `app_accepted:false` and keep
the complete original record. It rejects foreign plans, malformed/ambiguous base
forms, disagreeing source canonical identities, invalid metadata and unsupported
national forms. It reads the unchanged app packager contract instead of duplicating
its acceptance regex. No rewritten production row is returned.

Reproduce from the repository root with the existing locked research environment:

```sh
/workspace/work/business-directory-research/.venv/bin/python scripts/business-directory/research/check_phone_identity_review.py
/workspace/work/business-directory-research/.venv/bin/python scripts/business-directory/research/phone_identity_review.py /workspace/work/business-data/node-geography-owner-261103-v2/research-node-businesses.ndjson
```

The 16 focused offline contracts pass, including a scan of the exact retained
2,448-row ZIP snapshot, both source/national E.164 round trips for all seven
forms, fixed/mobile preservation, foreign/malformed/conflicting identities,
separate extensions, original-record immutability and unchanged country admission.
The retained [checks](phone-readiness-checks.json) and [execution log](phone-readiness-checks.txt)
record the tested helper/check/report/packager hashes. These are data research
checks, not new Android, Telecom, physical-device or actual phone-call tests.

Library validity is not evidence that a line is assigned, reachable, presently
owned by the named business, or currently active. Source timestamps are retained;
no present-day ownership or national coverage claim is made. The owner-upload
provenance and ODbL attribution/license from the [source audit](node-geography-README.md)
remain applicable. This review used local bytes only, made no network requests
or calls, and does not approve an APK release.
