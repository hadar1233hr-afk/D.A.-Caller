# Primary-source acquisition review —2026-11-06

This is metadata/license research only. **Zero new phone records or source files
were acquired/imported.** Search-index previews are not a phonebook, complete
download or currentness check. DA CALLER0.12.0, application assets, existing
research cohorts and signing/CI/publication safeguards remain unchanged.

## DataGov dataset license

The [official DataGov terms](https://data.gov.il/he/terms-of-use), whose displayed
update date is2025-08-30, distinguish dataset reuse from ordinary website terms.
The dataset license allows commercial/noncommercial copying, distribution and
technical derivatives, with source attribution. A dataset-specific license takes
precedence. Privacy, unlawfully obtained information and rights the publisher
cannot license remain excluded; misleading changes and implied state endorsement
are not permitted. Preserve the applicable dataset/license evidence at actual
acquisition time rather than assuming every government webpage is open data.

## Beer Sheva licensed-business records

- [Official dataset](https://data.gov.il/he/datasets/beer-sheva/business-licensing-br7):
  publisher Municipality of Beer Sheva; metadata label `Other (Open)`. The stated
  scope is businesses covered by the business-licensing law in that municipality,
  excluding other businesses and pending applications. This is not citywide or
  national coverage of every business.
- [Official CSV resource preview](https://data.gov.il/he/datasets/beer-sheva/business-licensing-br7/7d4c61e2-2416-453e-8efb-bd02ec89db35):
  the indexed preview displays3,108 **license rows** with business name, business
  telephone, street/house, license identifier, status and expiry fields. Visible
  examples contain empty/zero phone values and multiple licenses sharing the same
  business/phone/address. No unique valid-phone/business count is established.
- Dataset/resource views show differing update labels, including2026-10-18 and
  2026-10-25; the latter is displayed on the indexed resource. Direct live page
  opening yielded a JavaScript loading shell or inaccessible resource. These
  cached/indexed observations cannot substitute for a hash-bound file receipt.

Before import: obtain the actual official file through supported access, retain
its full metadata/license/date/hash, check for any dataset override, and select
public business-contact fields only. Do not import email/contact-person details
or assume a license row proves a current operating business. Validate numbers,
deduplicate source identities carefully and retain rejected rows/reasons. No
country tag may be fabricated from a phone prefix; any municipal-scope decision
must be explicit source provenance rather than a pretend per-row country field.

## Bank of Israel physical-branch records via DataGov

- [Official DataGov dataset](https://data.gov.il/he/datasets/bank_israel/branches)
  identifies Bank of Israel as publisher, labels the dataset `Other (Open)` and
  describes physical bank branches. Its resource cards display2026-11-01.
- [Official Hebrew CSV preview](https://data.gov.il/he/datasets/bank_israel/branches/2202bada-4baf-45f5-aa61-8c5bad9646d3)
  displays1,157 **branch rows**, with bank/branch codes and names, address/city,
  `Telephone`, opening/closure/merge fields and coordinate columns. Other indexed
  resource pages display2026-10-21/30. The views are not one frozen file snapshot.
- Previewed branches share central telephone numbers; closure dates and merged
  branches are visible. Preserve bank+branch identity, do not call1,157 rows
  unique phones or active branches. Example coordinate values appear latitude
  first despite X/Y labels; verify the source coordinate specification before
  spatial admission instead of guessing axes.

The [direct Bank of Israel website terms](https://www.boi.org.il/website_terms/)
restrict copying/distribution without prior written consent, subject to law.
Do not transfer the DataGov license assumption onto an arbitrary direct-BOI file.
For a future DataGov acquisition, bind the actual dataset license/resource origin
and confirm any overrides; no full raw metadata or dataset-specific license URL
was acquired here. This source remains a research candidate, not import approval.

## AllThePlaces generated outputs

The [official repository license](https://github.com/alltheplaces/alltheplaces/blob/master/LICENSE)
assigns CC0 to generated data published on the project website and MIT to its
spider software. This does not make arbitrary source-site scraping or code/test
fixtures a licensed, acquired phonebook.

The [official API documentation](https://github.com/alltheplaces/alltheplaces/blob/master/API.md)
documents `https://data.alltheplaces.xyz/runs/history.json` and `latest.json`.
Run metadata includes output URL, time, size and aggregate counts, with historical
metadata possibly incomplete. It also documents per-spider output at
`/runs/latest/output/{spider_name}.geojson`; a valid current manifest/redirect and
actual file are still needed before selecting a subset. No current run/Israel
count/size/checksum or generated-data file was obtained. The official
[GitHub releases endpoint](https://api.github.com/repos/alltheplaces/alltheplaces/releases)
returned an empty list, so no small official GitHub data asset was found there.

The [official data-format guide](https://github.com/alltheplaces/alltheplaces/blob/master/DATA_FORMAT.md)
warns that country fields may be inferred by reverse geocoding, geometry can be
missing, invalid/unparsed phone values can be retained, and territorial ambiguity
can cause incorrect formatting. Dataset IDs can change and source errors or
stale crawls can persist. Therefore an IL field, an `_il` spider name or an
existence count alone is not original-country/phone-ownership evidence. Preserve
source URI/ref/spider/raw phone/date and independently review scope and numbers.

## Access and exact status

Current managed-runtime observations were current/enforced/restricted. The local
policy lists GitHub but not `data.gov.il` or `data.alltheplaces.xyz`. No shell
request, bulk download, alternate mirror/proxy/route or account change was used
to avoid that restriction. Official web previews/docs were inspected; dynamic
resource pages did not provide a complete acquired source file. Supported
environment configuration review or an owner-provided exact official file with
its license/metadata is needed before claiming acquisition.

The [machine-readable observation](primary-sources-2026-11-06.json) distinguishes
observed metadata counts from unknown usable phone counts and records0imports.
No nationwide completeness, live phone verification, new Android test or APK
publication approval is implied. The existing signing/CI exception remains a
separate pending owner decision and must not be bypassed for these sources.
