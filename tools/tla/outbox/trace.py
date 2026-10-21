"""Print a TLC counterexample as one line per step: the action and the variables that changed.
Usage: java ... tlc2.TLC ... | python3 -I trace.py"""
import re, sys

text = sys.stdin.read()
m = re.search(r"Error: Invariant (\w+) is violated", text)
if not m:
    sys.exit(0)
print(f"VIOLATED: {m.group(1)}")
states = re.split(r"\nState (\d+): ", text)
prev = {}
for k in range(1, len(states), 2):
    n, body = states[k], states[k + 1]
    head, _, rest = body.partition("\n")
    act = re.match(r"<(\w+)", head)
    cur, key, buf = {}, None, []
    for line in rest.split("\n"):
        mm = re.match(r"/\\ (\w+) = (.*)", line)
        if mm:
            if key:
                cur[key] = " ".join(x.strip() for x in buf)
            key, buf = mm.group(1), [mm.group(2)]
        elif key and line.strip() and not line.startswith(("Finished", "Error", "The number", "End of", "Progress")):
            buf.append(line)
    if key:
        cur[key] = " ".join(x.strip() for x in buf)
    skip = {"nid", "nmarks", "ncreate", "nsale", "nreload", "dseq", "sent", "todo"}
    diff = {a: b for a, b in cur.items() if prev.get(a) != b and a not in skip}
    simp = lambda s: re.sub(r"\s+", " ", re.sub(r"(id|day|qday|failed|kind) \|-> \w+,?\s*", lambda x: x.group(0) if x.group(1) in ("kind", "failed") else "", s))
    print(f"{n:>2} {act.group(1) if act else 'Init':<12}", "; ".join(f"{a}={simp(b)}" for a, b in diff.items() if a != "flight" or "none" not in b or True)[:420])
    prev = cur
