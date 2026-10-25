#!/usr/bin/env python3
"""Drive the real restore UI outside instrumentation so Android can restart the app."""
import pathlib
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

package = sys.argv[1]
proof = subprocess.check_output(["adb", "shell", "run-as", package, "cat", "no_backup/restore-proof"], text=True).splitlines()
filename = f"restore-probe-{proof[0]}.backup"

def click(pattern, timeout=25):
    deadline = time.monotonic() + timeout
    last = ""
    while time.monotonic() < deadline:
        subprocess.run(["adb", "shell", "uiautomator", "dump", "/sdcard/restore-ui.xml"],
                       stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=20)
        last = subprocess.check_output(["adb", "shell", "cat", "/sdcard/restore-ui.xml"], text=True)
        try:
            nodes = list(ET.fromstring(last).iter("node"))
        except ET.ParseError:
            time.sleep(.3)
            continue
        matches = [node for node in nodes if any(re.fullmatch(pattern, node.get(key, ""), re.I)
                    for key in ("text", "content-desc"))]
        for node in reversed(matches):
            bounds = list(map(int, re.findall(r"\d+", node.get("bounds", ""))))
            if len(bounds) == 4 and bounds[2] > bounds[0] and bounds[3] > bounds[1]:
                subprocess.run(["adb", "shell", "input", "tap", str((bounds[0]+bounds[2])//2),
                                str((bounds[1]+bounds[3])//2)], check=True)
                time.sleep(.5)
                return
        time.sleep(.5)
    pathlib.Path("smoke-results/restore-ui-failure.xml").write_text(last)
    subprocess.run(["adb", "shell", "screencap", "-p", "/sdcard/restore-ui-failure.png"])
    subprocess.run(["adb", "pull", "/sdcard/restore-ui-failure.png", "smoke-results/"])
    raise RuntimeError(f"Restore UI control missing: {pattern}")

subprocess.run(["adb", "shell", "am", "start", "-W", "-n", f"{package}/fr.makoff.music.MainActivity"], check=True)
click("Account")
click("Settings")
click("Backup.*restore")
click("Restore")
click(re.escape(filename))
click("Restore")
print("RESTORE_UI_SUBMITTED: actual file picker and restore confirmation clicked")
