#!/usr/bin/env python3
"""Acquire one public source after the environment permits its exact host.

Uses inherited proxy/CA settings. No retries, mirrors, direct routes, credentials
or policy edits are attempted after a failure. This does not access the app.
"""
import argparse
import datetime as dt
import hashlib
import json
from pathlib import Path
import ssl
import tempfile
import urllib.error
import urllib.request

URL = "https://download.geofabrik.de/asia/israel-and-palestine-latest.osm.pbf"
MAX_BYTES = 500 * 1024 * 1024


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        # Inspecting response.geturl after automatic redirect is too late: it
        # would already have requested a different, unreviewed source host.
        return None


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("destination", type=Path)
    args = p.parse_args()
    if args.destination.exists():
        p.error("Use a fresh destination; source snapshots are immutable")
    args.destination.parent.mkdir(parents=True, exist_ok=True)
    receipt = {"source_url": URL, "attempted_at_utc": dt.datetime.now(dt.timezone.utc).isoformat(),
               "source_license": "ODbL-1.0", "actual_source_bytes": 0, "status": "NOT_DOWNLOADED"}
    receipt_path = args.destination.with_suffix(args.destination.suffix + ".receipt.json")
    if receipt_path.exists():
        p.error("Acquisition receipt already exists; choose a fresh snapshot name")
    temp_path = None
    try:
        request = urllib.request.Request(URL, headers={"User-Agent": "DA-CALLER-open-business-data-build/1.0"})
        opener = urllib.request.build_opener(NoRedirect(),
            urllib.request.HTTPSHandler(context=ssl.create_default_context()))
        with opener.open(request, timeout=60) as response:
            if response.status != 200:
                raise RuntimeError(f"Unexpected source HTTP status {response.status}")
            if response.geturl() != URL:
                raise RuntimeError("Unexpected source redirect; approve a new source explicitly")
            declared = response.headers.get("Content-Length")
            if declared and not 0 < int(declared) <= MAX_BYTES:
                raise RuntimeError("Source size outside declared safety bound")
            receipt.update(http_status=response.status, last_modified=response.headers.get("Last-Modified"),
                           content_type=response.headers.get("Content-Type"))
            h, size = hashlib.sha256(), 0
            with tempfile.NamedTemporaryFile(prefix="geofabrik-source-", dir=args.destination.parent, delete=False) as out:
                temp_path = Path(out.name)
                while chunk := response.read(1024 * 1024):
                    size += len(chunk)
                    if size > MAX_BYTES:
                        raise RuntimeError("Source exceeds download safety limit")
                    h.update(chunk)
                    out.write(chunk)
            if size == 0 or declared and size != int(declared):
                raise RuntimeError("Truncated or empty public source")
            # PBF begins with a bounded BlobHeader length. libosmium performs
            # the complete PBF schema/decompression checks during extraction.
            with temp_path.open("rb") as stream:
                length = int.from_bytes(stream.read(4), "big")
                if not 0 < length <= 65536:
                    raise RuntimeError("Invalid bounded OSM PBF header length")
                header = stream.read(length)
            if len(header) != length or b"OSMHeader" not in header:
                raise RuntimeError("Response is not an OSM PBF header")
            temp_path.rename(args.destination)
            temp_path = None
            receipt.update(status="DOWNLOADED", actual_source_bytes=size, source_sha256=h.hexdigest())
    except urllib.error.HTTPError as error:
        receipt.update(status="BLOCKED_OR_HTTP_FAILURE", http_status=error.code,
                       error="Public source request failed; no retry, mirror or policy bypass attempted")
    except Exception as error:
        receipt.update(status="FAILED", error=f"{type(error).__name__}: {error}")
    finally:
        if temp_path:
            temp_path.unlink(missing_ok=True)
        receipt_path.write_text(json.dumps(receipt, indent=2) + "\n")
    print(json.dumps(receipt, indent=2))
    return 0 if receipt["status"] == "DOWNLOADED" else 1


if __name__ == "__main__":
    raise SystemExit(main())
