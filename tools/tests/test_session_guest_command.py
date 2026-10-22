"""The guest launch prefix clears the environment with /usr/bin/env -i for every package name."""
import json
from pathlib import Path
import re
import unittest

SERVICE = Path(__file__).resolve().parents[2] / "app/src/main/java/com/droiddeck/launcher/session/SessionService.kt"
PACKAGES = ["com.droiddeck.launcher", "com.droiddeck.launcher.debug"]


def guest_prefix():
    source = SERVICE.read_text()
    start = source.index("private fun addClientEnvironment(")
    prefix = []
    for line in source[start:].splitlines()[1:]:
        line = line.strip()
        if line.startswith("}"):
            break
        if not line.startswith("guest.add("):
            continue
        expression = line[len("guest.add("):-1]
        match = re.fullmatch(r'("[^"\\]*")', expression)
        if match is None:
            break
        prefix.append(json.loads(match[1]))
    return prefix


class SessionGuestCommandTest(unittest.TestCase):
    def test_env_clears_the_host_environment_first(self):
        self.assertEqual(["/usr/bin/env", "-i"], guest_prefix()[:2])


if __name__ == "__main__":
    unittest.main()
