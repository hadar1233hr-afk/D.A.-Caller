#!/usr/bin/env python3
"""Narrow additional source categories, research-only; core policy unchanged."""
import importlib.util
from pathlib import Path

CORE_PATH = Path(__file__).resolve().parents[1] / "extract_osm_businesses.py"
spec = importlib.util.spec_from_file_location("extended_category_core_reference", CORE_PATH)
core = importlib.util.module_from_spec(spec)
spec.loader.exec_module(core)

WHITELIST = {
    "leisure": frozenset({"fitness_centre", "bowling_alley", "escape_game",
                          "trampoline_park", "amusement_arcade"}),
    "amenity": frozenset({"driving_school", "internet_cafe", "nightclub"}),
}
RESIDENTIAL_BUILDINGS = frozenset({"house", "residential", "apartments", "detached",
    "semidetached_house", "terrace", "hut", "cabin", "dormitory", "bungalow"})
ACCESS_ALLOWED = frozenset({"", "yes", "public", "customers", "permissive"})
LIFECYCLES = ("disused", "abandoned", "demolished", "razed", "construction", "proposed")
NON_OPERATING_BUILDINGS = frozenset({"construction", "ruins", "demolished", "proposed", "abandoned"})


def category_decision(tags: dict) -> tuple[str, str | None]:
    """No publicness/ownership assurance; reject uncertain source privacy states."""
    if core.category(tags) is not None:
        return "omit-already-covered-by-core-category", None
    candidate = next((f"{key}:{tags[key]}" for key, values in WHITELIST.items()
                      if tags.get(key) in values), None)
    if candidate is None:
        return "omit-outside-reviewed-extra-category", None
    if str(tags.get("access", "")).strip().lower() not in ACCESS_ALLOWED:
        return "hold-restricted-or-ambiguous-access", None
    if tags.get("access:conditional", "").strip():
        return "hold-conditional-access", None
    if (str(tags.get("building", "")).strip().lower() in RESIDENTIAL_BUILDINGS
            or str(tags.get("residential", "")).strip().lower() in {"yes", "private"}):
        return "hold-residential-source-object", None
    if str(tags.get("building", "")).strip().lower() in NON_OPERATING_BUILDINGS:
        return "hold-source-lifecycle-conflict", None
    for lifecycle in LIFECYCLES:
        if str(tags.get(lifecycle, "")).strip().lower() not in {"", "no", "false", "0"}:
            return "hold-source-lifecycle-conflict", None
        if any(tags.get(lifecycle + ":" + key, "").strip() for key in WHITELIST):
            return "hold-source-lifecycle-conflict", None
    # ownership=private/operator:type=private can describe commercial ownership;
    # neither is interpreted as access=private or deleted from retained source.
    return "research-extra-category-no-restricted-source-access", candidate


def category(tags: dict) -> str | None:
    return category_decision(tags)[1]


# Explicit shared reference contracts for the existing source-spatial runner.
# No mutation, replacement or monkey patch of the original extractor is used.
clean = core.clean
phones = core.phones
normalized_key = core.normalized_key
osmium = core.osmium
phonenumbers = core.phonenumbers
