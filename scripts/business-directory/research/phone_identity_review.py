#!/usr/bin/env python3
"""Review phone identities without rewriting records or admitting app data.

Only a national-format/E.164 round trip is proposed. Library validity means a
number fits numbering-plan metadata; it does not prove assignment, ownership,
reachability, geographic membership or present-day business activity. This
module never places calls, changes country fields or writes an app dataset.
"""
from __future__ import annotations

import argparse
import collections
import copy
import hashlib
import importlib.util
import json
from functools import lru_cache
from pathlib import Path
import re
from typing import Any

import phonenumbers

STATUS = "RESEARCH_ONLY_NOT_APP_ACCEPTED"
PACKAGER_PATH = Path(__file__).resolve().parents[1] / "package_app_dataset.py"
BASE_FORM = re.compile(r"[+0-9() .\-]+", re.ASCII)


@lru_cache(maxsize=1)
def production_packager():
    """Read the existing production contract; never modify or weaken it."""
    spec = importlib.util.spec_from_file_location("phone_review_packager", PACKAGER_PATH)
    if spec is None or spec.loader is None:
        raise RuntimeError("Existing production packager is required for review")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def review_phone_identity(record: dict[str, Any]) -> dict[str, Any]:
    """Return a non-mutating proposal retaining the complete original record.

    The already source-audited canonical base ``phone`` is the parse input, not
    arbitrary digits stripped from the free-form original source string. If
    present, ``e164`` must agree exactly. Separate extension metadata is retained
    but is never appended to a dial identity. No result is production admission.
    """
    result: dict[str, Any] = {
        "status": STATUS,
        "app_accepted": False,
        "source_record": copy.deepcopy(record),
        "decision": "REJECTED_FOR_PHONE_PROPOSAL",
        "reason": "invalid-base-form",
        "proposal": None,
    }
    raw = record.get("phone")
    if (not isinstance(raw, str) or not 0 < len(raw) <= 80
            or BASE_FORM.fullmatch(raw) is None):
        return result
    # A plus may occur only once, at the start. Do not interpret ranges, extension
    # separators, hidden controls, Unicode digits, multiple identities or URLs.
    stripped = raw.strip()
    if "+" in stripped and (not stripped.startswith("+") or stripped.count("+") != 1):
        return result
    extension = record.get("extension", "")
    if (not isinstance(extension, str) or len(extension) > 20
            or (extension and re.fullmatch(r"[0-9]+", extension, re.ASCII) is None)):
        result["reason"] = "invalid-separate-extension"
        return result
    try:
        parsed = phonenumbers.parse(raw, "IL")
    except phonenumbers.NumberParseException:
        result["reason"] = "unparseable-base-number"
        return result
    if parsed.extension:
        result["reason"] = "embedded-extension-is-not-a-base-number"
        return result
    if (parsed.country_code != 972
            or phonenumbers.region_code_for_number(parsed) != "IL"):
        result["reason"] = "not-israeli-numbering-plan"
        return result
    if not phonenumbers.is_valid_number(parsed):
        result["reason"] = "invalid-by-numbering-plan-metadata"
        return result
    canonical = phonenumbers.format_number(parsed, phonenumbers.PhoneNumberFormat.E164)
    if "e164" in record and record["e164"] != canonical:
        result["reason"] = "source-canonical-identities-disagree"
        return result
    national = phonenumbers.format_number(parsed, phonenumbers.PhoneNumberFormat.NATIONAL)
    if BASE_FORM.fullmatch(national) is None:
        result["reason"] = "unsupported-national-format"
        return result
    candidate = re.sub(r"[() .\-]", "", national)
    if re.fullmatch(r"[0-9]+", candidate, re.ASCII) is None:
        result["reason"] = "unsupported-national-dial-identity"
        return result
    try:
        round_trip = phonenumbers.parse(candidate, "IL")
    except phonenumbers.NumberParseException:
        result["reason"] = "national-identity-cannot-round-trip"
        return result
    if (not phonenumbers.is_valid_number(round_trip)
            or phonenumbers.format_number(round_trip, phonenumbers.PhoneNumberFormat.E164) != canonical):
        result["reason"] = "national-identity-changes-source-number"
        return result
    app = production_packager()
    existing_identity = app.normalized_phone(raw)
    if app.normalized_phone(candidate) != candidate:
        result["reason"] = "national-identity-not-supported-by-current-app-contract"
        return result
    kind = phonenumbers.number_type(parsed)
    unchanged = existing_identity == candidate
    result.update(
        decision="IDENTITY_UNCHANGED" if unchanged else "RESEARCH_REFORMAT_PROPOSAL",
        reason=("existing-app-identity-matches-national-round-trip" if unchanged
                else "national-service-identity-without-trunk-zero"
                if kind in {phonenumbers.PhoneNumberType.UAN, phonenumbers.PhoneNumberType.TOLL_FREE}
                and candidate.startswith("1") else "national-format-preserves-e164-identity"),
        current_app_base_identity=existing_identity,
        proposal={
            "base_dial_identity": candidate,
            "e164": canonical,
            "national_display_by_library": national,
            "region_by_numbering_metadata": "IL",
            "phone_type": phonenumbers.PhoneNumberType.to_string(kind),
            "valid_by_library": True,
            "same_e164_round_trip": True,
            "extension_retained_separately": extension,
            "requires_review_before_production_use": True,
        },
    )
    return result


def review_corpus(source: Path) -> dict[str, Any]:
    """Measure every row with the unchanged packager; report only phone rejects.

    This function reads a local NDJSON file and returns JSON-serializable evidence.
    The source is never opened for writing. Country/text failures remain failures.
    """
    original_bytes = source.read_bytes()
    rows = [json.loads(line) for line in original_bytes.splitlines() if line.strip()]
    app = production_packager()
    failures: collections.Counter[str] = collections.Counter()
    phone_rejections = []
    for record in rows:
        try:
            app.record_values(record)
        except ValueError as error:
            reason = str(error)
            failures[reason] += 1
            if reason == "Invalid app dial identity":
                phone_rejections.append(review_phone_identity(record))
        else:
            failures["ACCEPTED_BY_CURRENT_PACKAGER"] += 1
    if source.read_bytes() != original_bytes:
        raise RuntimeError("Source changed during review; no stable report can be produced")
    return {
        "status": STATUS,
        "app_accepted": False,
        "rows_added_to_app": 0,
        "phonenumbers_version": phonenumbers.__version__,
        "source_sha256": hashlib.sha256(original_bytes).hexdigest(),
        "source_bytes": len(original_bytes),
        "review_helper_sha256": hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
        "unchanged_packager_sha256": hashlib.sha256(PACKAGER_PATH.read_bytes()).hexdigest(),
        "source_rows": len(rows),
        "production_results": dict(sorted(failures.items())),
        "phone_rejected_rows": len(phone_rejections),
        "phone_rejected_unique_e164": len({item["source_record"].get("e164") for item in phone_rejections}),
        "phone_review_decisions": dict(sorted(collections.Counter(item["decision"] for item in phone_rejections).items())),
        "phone_rejections": phone_rejections,
        "limitations": [
            "No source phone, name, country, scope or extension was rewritten.",
            "A proposed dial form is not production acceptance; existing country/scope guards still apply.",
            "Numbering-plan validity and round-trip identity do not prove assignment, reachability or current ownership.",
            "No phone calls, network lookup, APK edits, signing changes or app dataset writes were performed.",
        ],
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path, help="Source-audited local research NDJSON; read only")
    args = parser.parse_args()
    print(json.dumps(review_corpus(args.source), ensure_ascii=False, indent=2, sort_keys=True))


if __name__ == "__main__":
    main()
