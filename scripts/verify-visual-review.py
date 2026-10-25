#!/usr/bin/env python3
"""Keep a review label from publishing application code never visually reviewed."""
import json
import os
import re
import subprocess
from pathlib import Path


def output(*args):
    return subprocess.check_output(args, text=True).strip()


review = json.loads(Path("docs/evidence/2026-10-25/visual-review.json").read_text())
assert review.get("review_status") == "PASS", "Visual review has not passed"
assert review.get("reviewed_png_count") == 122, "Visual review must cover all 122 required PNGs"
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
