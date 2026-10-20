"""Validate Android's short and fully qualified component names without accepting other activities."""
import re
import sys
from pathlib import Path


def is_main_resumed(text, package):
    for line in text.splitlines():
        if not re.search(r"(?:mResumedActivity|topResumedActivity)", line):
            continue
        for owner, activity in re.findall(r"([\w.]+)/([\w.]+)", line):
            expanded = owner + activity if activity.startswith(".") else activity
            if owner == package and expanded == "fr.makoff.music.MainActivity":
                return True
    return False


if __name__ == "__main__":
    package, filename = sys.argv[1:]
    if not is_main_resumed(Path(filename).read_text(), package):
        sys.exit("Échec : l’activité principale attendue n’est pas au premier plan.")
    print("PASS: activité principale au premier plan : " + package)
