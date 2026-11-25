# Primary-source alternatives, inspected2026-11-02

## Overture Places — stronger candidate for broad coverage

The [official Places guide](https://docs.overturemaps.org/guides/places/) identifies a current2026-10-21.0 release with names, phones, addresses, stable GERS IDs and existence-confidence data. It explicitly says this theme has no OpenStreetMap data. Places licenses are CDLA-Permissive2.0, Apache2.0 and source-specific CC0 contributions; retain source-specific notices and the applicable license texts. The app and private contacts can remain separate. [Official attribution table](https://docs.overturemaps.org/attribution/#places), [CDLA-Permissive2.0](https://cdla.dev/permissive-2-0/).

The same guide warns of duplicates, junk and incomplete properties. Confidence concerns place existence, not current phone ownership; it is not uniformly calibrated across providers. No current Israel phone count was published in the inspected guide. No claim of100,000 or one million phone records is established.

The [schema reference](https://docs.overturemaps.org/schema/reference/places/place/) is v2.0.0. SinceSeptember2026 the old `categories` property has been removed: use `taxonomy`/`basic_category`. `phones` is a string array, `addresses` carries country codes, and `operating_status` describes business operation rather than today's opening hours.

The [official STAC catalog](https://stac.overturemaps.org/) marked2026-10-21.0 latest when inspected. The [quickstart](https://docs.overturemaps.org/getting-data/) offers bounding-box downloads through its client or Explorer without downloading the worldwide corpus. Official data locations:

- `s3://overturemaps-us-west-2/release/2026-10-21.0/theme=places/type=place/*`
- `https://overturemapswestus2.blob.core.windows.net/release/2026-10-21.0/theme=places/type=place/*`

Neither host is in the executor's inspected permitted destinations. No bulk request was attempted to these hosts, no route/mirror/proxy bypass was used, and no data was downloaded. A supported environment configuration approval is needed before actual acquisition. The separate SQL candidate is a documented query plan, **NOT RUN** against real data.

## AllThePlaces

The [primary repository license](https://github.com/alltheplaces/alltheplaces/blob/master/LICENSE) dedicates generated spider data published on its website under CC0; its spider software has a separate MIT license. It is a candidate business-chain source rather than a guarantee of complete national coverage. The public data entry point `https://data.alltheplaces.xyz/` could not be opened through the web tool in this run. No release or Israel-specific phone count was acquired. Do not scrape individual retailer/competitor sites merely because a spider implementation exists.

## Actual output status

Only metadata/license research and private extraction tooling exist. Production business records acquired from these alternatives:0. No website snippets or synthetic fixtures have been represented as an imported phonebook.
