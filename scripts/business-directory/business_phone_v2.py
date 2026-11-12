"""Match DA CALLER's business-only national dial/query identities.

This is a bounded grammar, not a numbering-plan, ownership or country-admission
validator. A dataset producer must separately verify its source phone and E.164
round trip. No source record, private data, old helper or app asset is changed.
"""
from __future__ import annotations

import re

CONTRACT_ID = "DA_CALLER_BUSINESS_PHONE_V2"
_TRIM = "".join(chr(code) for code in range(33))  # Java String.trim(), not Unicode strip().
_FORM = re.compile(r"[+*0-9() .\-]+", re.ASCII)
_IDENTITY = re.compile(r"0(?:[2-489][0-9]{7}|[57][0-9]{8})|\*[0-9]{3,6}|1[0-9]{2,9}", re.ASCII)
_PREFIX = re.compile(r"[0-9*]{1,16}", re.ASCII)


def _phone_characters(raw: object) -> str | None:
    if not isinstance(raw, str):
        return None
    value = raw.strip(_TRIM).replace("\u00a0", " ")
    if _FORM.fullmatch(value) is None:
        return None
    return re.sub(r"[^0-9+*]", "", value, flags=re.ASCII)


def _national_digits(dial: str) -> str:
    for country in ("+972", "00972", "972"):
        if dial.startswith(country):
            national = dial[len(country):]
            return national if not national or national.startswith("1") else "0" + national
    return dial


def normalized_phone(raw: object) -> str:
    """Return the whole supported source dial identity, or an empty rejection."""
    digits = _phone_characters(raw)
    if digits is None:
        return ""
    national = _national_digits(digits)
    return national if _IDENTITY.fullmatch(national) else ""


def phone_prefix(raw: object) -> str:
    """Return a literal national prefix; never guess a suffix or extension."""
    digits = _phone_characters(raw)
    if digits is None:
        return ""
    national = _national_digits(digits)
    return national if _PREFIX.fullmatch(national) else ""


def is_country_code_only(raw: object) -> bool:
    """The native reader lists its Israel-only directory for these exact forms."""
    return _phone_characters(raw) in {"+972", "00972", "972"}
