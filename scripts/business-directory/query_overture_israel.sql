-- NOT RUN: requires approved access to the official Overture source host and
-- normal installation of DuckDB's httpfs extension. Never change proxy policy
-- or move a blocked request to another route just to circumvent a denial.
-- Source schema/release verified in primary docs2026-11-02:
-- https://docs.overturemaps.org/guides/places/
-- https://docs.overturemaps.org/schema/reference/places/place/
-- This query returns candidates, not phone-ownership-verified contacts.

LOAD httpfs;
SET s3_region='us-west-2';

COPY (
    SELECT
        id,
        names,
        phones,
        addresses,
        taxonomy,
        basic_category,
        confidence,
        operating_status,
        sources,
        websites,
        bbox
    FROM read_parquet(
        's3://overturemaps-us-west-2/release/2026-10-21.0/theme=places/type=place/*',
        hive_partitioning=true
    )
    WHERE
        -- Broad spatial prefilter reduces remote scanning; country metadata,
        -- source/provider provenance and phone validation remain mandatory.
        bbox.xmin BETWEEN 33.8 AND 36.2
        AND bbox.ymin BETWEEN 28.8 AND 34.0
        AND addresses[1].country = 'IL'
        AND names.primary IS NOT NULL
        AND length(names.primary) > 0
        AND array_length(phones) > 0
        AND operating_status = 'open'
        AND confidence >= 0.95
) TO 'israel-overture-candidates.ndjson' (FORMAT JSON, ARRAY false);

-- After acquisition: reject explicit contradictory country addresses; validate
-- every phone with current IL libphonenumber metadata; preserve extensions,
-- all GERS/source IDs/update dates/record licenses. Deduplicate exact branches
-- without merging distinct businesses sharing a switchboard. Publish measured
-- record/unique-phone/reject counts and source bytes/SHA256. Preserve relevant
-- CDLA/Apache/CC0 license notices. Never label confidence as phone accuracy.
