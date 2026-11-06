#!/usr/bin/env python3
"""Bridge a native test request to the disposable emulator's simulated GSM modem.

The app/test never constructs a Call or injects a CallIslandSession.  Only the
host emulator console can originate an incoming simulated modem call.  Commands
are deliberately restricted to one fictional number and emulator serials.
"""
import json
from pathlib import Path
import re
import signal
import subprocess
import sys
import time

DIRECTORY = "/sdcard/Download/DA-CALLER-snapshots"
NUMBER = "0500099971"
LOG = Path(sys.argv[1] if len(sys.argv) > 1 else "app/build/emulator-call-driver.jsonl")
LOG.parent.mkdir(parents=True, exist_ok=True)
stopping = False


def stop(_signal, _frame):
    global stopping
    stopping = True


signal.signal(signal.SIGTERM, stop)
signal.signal(signal.SIGINT, stop)


def adb(*args, check=True):
    return subprocess.run(["adb", *args], text=True, capture_output=True,
                          check=check, timeout=15)


serial = adb("get-serialno").stdout.strip()
assert re.fullmatch(r"emulator-[0-9]+", serial), "Refuse modem commands on a physical device"
assert adb("shell", "getprop", "ro.kernel.qemu").stdout.strip() == "1", "Expected disposable emulator"
LOG.with_suffix(".ready.json").write_text(json.dumps({"ready": True, "serial": serial, "number": NUMBER}) + "\n")
deadline = time.monotonic() + 1800
seen = set()
issued = False
try:
    while not stopping and time.monotonic() < deadline:
        pending = adb("shell", "cat", f"{DIRECTORY}/external-gsm-request.json", check=False)
        if pending.returncode:
            time.sleep(.3)
            continue
        try:
            request = json.loads(pending.stdout)
        except json.JSONDecodeError:
            time.sleep(.3)
            continue
        identity = str(request.get("id", ""))
        if identity in seen:
            time.sleep(.3)
            continue
        assert re.fullmatch(r"[0-9]+", identity), "Invalid request identity"
        operation = request.get("operation")
        assert operation in {"call", "cancel"}, "Unsupported emulator modem operation"
        assert request.get("number") == NUMBER, "Unexpected test number"
        seen.add(identity)
        result = adb("emu", "gsm", operation, NUMBER, check=False)
        issued = True
        response = {"id": identity, "operation": operation, "number": NUMBER,
                    "serial": serial, "returncode": result.returncode,
                    "stdout": result.stdout.strip(), "stderr": result.stderr.strip()}
        with LOG.open("a") as output:
            output.write(json.dumps(response) + "\n")
        # Pass JSON as stdin, without a shell interpolation or command substitution.
        subprocess.run(["adb", "shell", "tee", f"{DIRECTORY}/external-gsm-response.json"],
                       input=json.dumps(response), text=True, capture_output=True,
                       check=True, timeout=15)
        time.sleep(.15)
finally:
    if issued:
        adb("emu", "gsm", "cancel", NUMBER, check=False)
