#!/usr/bin/env python3
"""Delete Kotlin @Test functions by exact name. Usage: drop-kotlin-test.py FILE 'test name' ['test name' ...]

Fails when a name is not found once, so a renamed test is never skipped silently.
"""

import re
import sys
from pathlib import Path

path = Path(sys.argv[1])
src = path.read_text()
for name in sys.argv[2:]:
    pattern = re.compile(r"\n    @Test\n    fun `" + re.escape(name) + r"`\(\) \{\n.*?\n    \}\n", re.S)
    found = pattern.findall(src)
    if len(found) != 1:
        sys.exit(f"{path}: {len(found)} tests named {name!r}")
    src = pattern.sub("\n", src, count=1)
path.write_text(src)
