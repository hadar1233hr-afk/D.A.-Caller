#!/usr/bin/env python3
"""Keep a review label from publishing application code never visually reviewed."""
import json
import os
import re
import subprocess
from pathlib import Path


def output(*args):
    return subprocess.check_output(args, text=True).strip()


review_path = Path(os.environ.get("DA_CALLER_VISUAL_REVIEW", "docs/evidence/2026-10-25/visual-review.json"))
review = json.loads(review_path.read_text())
assert review.get("review_status") == "PASS", "Visual review has not passed"
expected = int(review.get("expected_png_count", 122))
assert expected >= 122, "Existing visual regression coverage must be preserved"
assert review.get("reviewed_png_count") == expected, "Visual review must cover every required PNG"
if "personalization" in str(review_path):
    assert expected > 122, "Personalization screenshots must be reviewed as well"
    assert set(review.get("verified_features", [])) == {
        "settings_backup_restore", "five_launcher_icons", "favorites_strip",
        "compact_summary", "avatar_shapes", "archive_contact_identity",
        "call_notifications", "return_from_call"
    }, "Review must cover all eight requested features"
if "external-island-" in str(review_path):
    assert expected == 227, "All219 previous and8 actual Telecom captures must be reviewed"
    assert set(review.get("verified_features", [])) == {
        "compact_expanded_island", "call_actions_guarded", "island_customization_backup",
        "activity_lifecycle_and_list_state", "offline_optional_overlay_permission",
        "real_telecom_external_island"
    }, "Review must cover actual external call windows and explicit optional overlay access"
elif "island-" in str(review_path):
    assert expected == 219, "All 183 regressions and 36 island captures must be reviewed"
    assert set(review.get("verified_features", [])) == {
        "compact_expanded_island", "call_actions_guarded", "island_customization_backup",
        "activity_lifecycle_and_list_state", "offline_no_overlay_permission"
    }, "Review must cover island controls, customization, lifecycle and permission scope"
source = str(review.get("screenshot_source_sha", ""))
run_id = str(review.get("screenshot_run_id", ""))
tree = str(review.get("reviewed_app_tree", ""))
assert re.fullmatch(r"[a-f0-9]{40}", source), "Missing screenshot source commit"
assert re.fullmatch(r"[a-f0-9]{40}", tree), "Missing reviewed application tree"
assert re.fullmatch(r"[0-9]+", run_id), "Missing screenshot workflow run"
assert output("git", "rev-parse", "HEAD:app") == tree, "Application source changed after visual review"
# The reviewer inspected the actual artifacts from this successful source run.
repository = os.environ["GH_REPO"]
run = json.loads(output("gh", "api", f"repos/{repository}/actions/runs/{run_id}"))
assert run.get("head_sha") == source, "Screenshot run used a different source commit"
assert run.get("conclusion") == "success", "Screenshot workflow did not pass"
assert run.get("path") == ".github/workflows/android.yml", "Unexpected verification workflow"
try:
    source_tree = output("git", "rev-parse", f"{source}:app")
except subprocess.CalledProcessError:
    subprocess.run(["git", "fetch", "--no-tags", "--depth=1", "origin", source], check=True)
    source_tree = output("git", "rev-parse", f"{source}:app")
assert source_tree == tree, "Recorded screenshots do not match the reviewed application tree"
print(f"Visual review covers exact app tree {tree}; verified screenshot run {run_id}")
