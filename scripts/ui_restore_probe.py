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

def document_hierarchy(timeout=25):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        # A transition can leave UIAutomator without an idle root. Do not read an old dump.
        subprocess.run(["adb", "shell", "rm", "-f", "/sdcard/restore-ui.xml"],
                       stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=5)
        try:
            subprocess.run(["adb", "shell", "uiautomator", "dump", "/sdcard/restore-ui.xml"],
                           stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=20)
            result = subprocess.run(["adb", "shell", "cat", "/sdcard/restore-ui.xml"],
                                    capture_output=True, text=True, timeout=5)
            if result.returncode == 0:
                ET.fromstring(result.stdout)
                return result.stdout
        except (subprocess.TimeoutExpired, ET.ParseError):
            pass
        time.sleep(.5)
    raise RuntimeError("UIAutomator could not obtain a valid current hierarchy")

def click(pattern, timeout=45, scroll=False):
    deadline = time.monotonic() + timeout
    last = ""
    while time.monotonic() < deadline:
        try:
            last = document_hierarchy()
        except RuntimeError:
            continue
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
                time.sleep(.3)
                subprocess.run(["adb", "shell", "input", "tap", str((bounds[0]+bounds[2])//2),
                                str((bounds[1]+bounds[3])//2)], check=True)
                time.sleep(.5)
                return
        if scroll:
            container = next((node for node in nodes if node.get("scrollable") == "true"), None)
            if container is not None:
                bounds = list(map(int, re.findall(r"\d+", container.get("bounds", ""))))
                if len(bounds) == 4 and bounds[3] - bounds[1] > 80:
                    x = (bounds[0] + bounds[2]) // 2
                    margin = (bounds[3] - bounds[1]) // 5
                    subprocess.run(["adb", "shell", "input", "swipe", str(x), str(bounds[3] - margin),
                                    str(x), str(bounds[1] + margin), "450"], check=True)
        time.sleep(.5)
    pathlib.Path("smoke-results/restore-ui-failure.xml").write_text(last)
    with open("smoke-results/restore-ui-logcat.txt", "w") as log:
        subprocess.run(["adb", "logcat", "-d"], stdout=log)
    subprocess.run(["adb", "shell", "screencap", "-p", "/sdcard/restore-ui-failure.png"])
    subprocess.run(["adb", "pull", "/sdcard/restore-ui-failure.png", "smoke-results/"])
    raise RuntimeError(f"Restore UI control missing: {pattern}")

subprocess.run(["adb", "shell", "am", "start", "-W", "-n", f"{package}/fr.makoff.music.MainActivity"], check=True)
click("Account")
click("Settings")
click("Backup.*restore", scroll=True)
click("Restore")
click(re.escape(filename))
time.sleep(1)
hierarchy = document_hierarchy()
if "android.documentsui" in hierarchy:
    # Android 11's document list supports keyboard activation even when a tap only focuses it.
    for attempt in range(18):
        subprocess.run(["adb", "shell", "input", "keyevent", "KEYCODE_TAB"], check=True)
        hierarchy = document_hierarchy()
        nodes = ET.fromstring(hierarchy).iter("node")
        focused_file = next((node for node in nodes
            if node.get("focused") == "true"
            and node.get("resource-id", "").endswith(":id/item_root")
            and any(child.get("text") == filename for child in node.iter("node"))), None)
        if focused_file is not None:
            subprocess.run(["adb", "shell", "input", "keyevent", "KEYCODE_ENTER"], check=True)
            time.sleep(1)
            break
click("Restore")
print("RESTORE_UI_SUBMITTED: actual file picker and restore confirmation clicked")
