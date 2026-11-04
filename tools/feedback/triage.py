#!/usr/bin/env python3
"""Triage feedback into AUTO_FIX, FEATURE_REQUEST or NOT_A_BUG. Writes a verdict, never closes or ships.

  triage.py run                      triage every untriaged user and agent report on prod, one ntfy each
  triage.py eval ROWS LABELS [--haiku]  accuracy of the classifiers on a labelled set (ROWS: id<TAB>kind<TAB>text)
  triage.py self-test                pure checks, no database
Verdict: rules and Haiku together; AUTO_FIX or NOT_A_BUG only when both agree, else FEATURE_REQUEST.
labels.txt is the hand-labelled set (Nov 4: rules 76%, Haiku 78%, combined 78% with 3 false AUTO_FIX
of 94). Rebuild ROWS from a pre-reset backup: pg_restore -a -t user_feedback, then id<TAB>kind<TAB>message.
"""
import json, os, re, subprocess, sys, time, urllib.request

VERDICTS = ("AUTO_FIX", "FEATURE_REQUEST", "NOT_A_BUG")
SHORT = {"A": "AUTO_FIX", "F": "FEATURE_REQUEST", "N": "NOT_A_BUG"}

EMAIL = re.compile(r"[\w.+-]+@[\w-]+\.[\w.]+")
PHONE = re.compile(r"\+?\d[\d\s-]{6,}\d")
APP_WORDS = {"client", "new", "owner", "user", "person", "admin", "test", "branch", "session", "sessions", "relief", "helper"}
UUID = re.compile(r"\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\b")


def redact(text, names):
    """Names of clients and users become [name]; emails, phone numbers and ids go too."""
    text = EMAIL.sub("[email]", text)
    text = UUID.sub("[id]", text)
    text = PHONE.sub("[phone]", text)
    for n in sorted({n for n in names if len(n) >= 3 and n.lower() not in APP_WORDS}, key=len, reverse=True):
        text = re.sub(r"(?i)\b" + re.escape(n) + r"\b", "[name]", text)
    return text


DEFECT = re.compile(r"(?i)\b(error|40\d|50\d|broken|bug|crash|slow(er)?|sluggish|load(ing|ed)?\b.*\b(long|seconds|slower)|"
                    r"overlap\w*|moves?( around| to)?|new row|two rows|zoom\w*|scroll\w*|still (there|says|see)|"
                    r"didn'?t|doesn'?t|does not|can'?t (see|tell|find|add|remove)|couldn'?t|disappear\w*|"
                    r"refresh\w*|hung|hangs?|stuck|timed? ?out|fail(s|ed|ing)?|ugly|stinks?|weird|occlud\w*|too (big|many|much)|confus\w*|annoying|"
                    r"not sure (what|which)|i don'?t know (which|what)|stupid)\b")
WISH = re.compile(r"(?i)\b(should be able|should only|need a way|i need|i want|would like|option to|why not|"
                  r"customi[sz]\w*|instead|redundant|rename\w*|should be|needs? (a )?confirmation|"
                  r"cumbersome|friction|unfeasible|needs work|minimi[sz]ed by default|list the|range picker)\b")
QUESTION = re.compile(r"(?i)^(does|is|are|do|can|how do|how are|not sure how|i'?m not sure how|sorry)\b")


def rules(kind, text):
    """Deterministic verdict and its reason."""
    if kind == "IDEA":
        return "FEATURE_REQUEST", "filed as an idea"
    if QUESTION.search(text) and not DEFECT.search(text):
        return "NOT_A_BUG", "a question about how the app works"
    w, d = WISH.search(text), DEFECT.search(text)
    if w and (not d or w.start() <= d.start()):
        return "FEATURE_REQUEST", f'asks for a change ("{w.group(0)}")'
    if d:
        return "AUTO_FIX", f'describes a defect ("{d.group(0)}")'
    return "FEATURE_REQUEST", "no defect word; needs a product call"


PROMPT = """You triage one feedback report from a small business web app (sessions, sales, clock-in, remittances).
Answer with one JSON object and nothing else: {"verdict": V, "reason": R}
V is one of:
- AUTO_FIX: a defect with one obvious fix an engineer could make without asking the owner (error, slowness, layout breaking, stale or wrong screen, confusing label, missing feedback).
- FEATURE_REQUEST: asks for new behavior, a new rule, a new option or a redesign the owner must decide.
- NOT_A_BUG: a question answered by how the app already works, expected behavior, or a follow-up note that adds no new problem.
R is one short sentence, at most 15 words, with no names.
Report kind: {kind}
Report: {text}"""


def haiku(kind, text):
    """Verdict from claude -p haiku and the call's cost in USD."""
    out = subprocess.run(
        ["claude", "-p", "--model", "haiku", "--output-format", "json", "--tools", "",
         "--system-prompt", "You classify bug reports. Output JSON only.",
         PROMPT.replace("{kind}", kind).replace("{text}", text)],
        capture_output=True, text=True, timeout=120, cwd="/tmp")
    res = json.loads(out.stdout)
    body = res.get("result", "")
    m = re.search(r"\{.*\}", body, re.S)
    v = json.loads(m.group(0)) if m else {}
    verdict = v.get("verdict") if v.get("verdict") in VERDICTS else None
    return verdict, (v.get("reason") or "")[:200], float(res.get("total_cost_usd") or 0)


def combine(rule, model):
    """AUTO_FIX or NOT_A_BUG only when both agree; a split goes to the owner as a feature request."""
    return rule if rule == model else "FEATURE_REQUEST"


def classify(kind, text):
    """The timer's verdict, reason and which classifiers made it. Haiku failing leaves the rules alone."""
    rv, rr = rules(kind, text)
    try:
        hv, hr, _ = haiku(kind, text)
    except Exception:
        hv = None
    if hv is None:
        return rv, rr, "rules"
    if rv == hv:
        return rv, hr or rr, "rules+haiku"
    return combine(rv, hv), f"rules say {rv}, haiku says {hv}: owner decides", "rules+haiku"


# ---- prod side ---------------------------------------------------------------

def psql(sql, *args):
    c = os.environ.get("TRIAGE_DB_CONTAINER") or subprocess.run(
        "docker ps --format '{{.Names}}' | grep '^" + os.environ["DB_PREFIX"] + "' | head -1",
        shell=True, capture_output=True, text=True).stdout.strip()
    if not c:
        sys.exit("prod postgres container not running")
    cmd = ["docker", "exec", "-i", c, "psql", "-U", os.environ.get("DB_USER", "company_user"),
           "-d", os.environ.get("DB_NAME", "company_app"), "-v", "ON_ERROR_STOP=1", "-qAt", "-F", "\t"]
    for i, a in enumerate(args):
        cmd += ["-v", f"a{i}={a}"]
    r = subprocess.run(cmd, input=sql, capture_output=True, text=True)
    if r.returncode:
        sys.exit(r.stderr.strip())
    return [l.split("\t") for l in r.stdout.splitlines() if l]


PENDING = """
SELECT 'USER', f.id, f.kind, replace(f.message, E'\\n', ' ') FROM user_feedback f
 WHERE NOT EXISTS (SELECT 1 FROM feedback_triage t WHERE t.feedback_id = f.id)
UNION ALL
SELECT 'AGENT', a.id, 'PROBLEM', replace(a.message, E'\\n', ' ') FROM agent_feedback a
 WHERE NOT EXISTS (SELECT 1 FROM feedback_triage t WHERE t.agent_feedback_id = a.id);
"""
NAMES = """
SELECT n FROM (SELECT unnest(ARRAY[first_name, last_name, middle_name]) n FROM client
 UNION SELECT unnest(string_to_array(display_name, ' ')) FROM app_user
 UNION SELECT username FROM app_user) x WHERE n IS NOT NULL;
"""
SAVE = """
INSERT INTO feedback_triage (feedback_id, agent_feedback_id, verdict, reason, classifier)
VALUES (NULLIF(:'a0','')::uuid, NULLIF(:'a1','')::uuid, :'a2'::feedback_verdict, :'a3', :'a4')
ON CONFLICT DO NOTHING RETURNING 1;
"""
UNSENT = """SELECT coalesce(feedback_id, agent_feedback_id), verdict, reason, CASE WHEN feedback_id IS NULL THEN 'agent' ELSE 'user' END
FROM feedback_triage WHERE notified_at IS NULL;"""
MARK = "UPDATE feedback_triage SET notified_at = now() WHERE coalesce(feedback_id, agent_feedback_id) = :'a0'::uuid;"

def ntfy(title, body):
    url = os.environ["NTFY_URL"].rstrip("/") + "/" + (os.environ.get("TEST_TOPIC") or os.environ["NTFY_TOPIC"])
    req = urllib.request.Request(url, data=body.encode(), headers={"Title": title, "Tags": "memo"})
    urllib.request.urlopen(req, timeout=15).read()


def run():
    names = [r[0] for r in psql(NAMES)]
    for source, fid, kind, msg in psql(PENDING):
        verdict, reason, how = classify(kind, redact(msg, names))
        psql(SAVE, fid if source == "USER" else "", fid if source == "AGENT" else "", verdict, reason, how)
        print(f"triaged {source} {fid[:8]} {verdict}")
    notify_pending()


def notify_pending():
    """Marks a row sent only after its ntfy went out; a failed or killed send is retried next run (at worst a repeat)."""
    for fid, verdict, reason, source in psql(UNSENT):
        read = f"app-feedback show {fid[:8]}" if source == "user" else "app-feedback triage"
        try:
            ntfy(f"{source.capitalize()} feedback {fid[:8]}: {verdict.replace('_', ' ').lower()}",
                 f"{reason}\nRead it: {read}\nNothing was closed or changed.")
        except Exception as e:
            print(f"ntfy failed for {fid[:8]}, retried next run: {e}", file=sys.stderr)
            continue
        psql(MARK, fid)

def evaluate(rows_path, labels_path, use_haiku):
    labels = dict(l.split() for l in open(labels_path) if l.strip())
    rows = [l.rstrip("\n").split("\t") for l in open(rows_path) if l.strip()]
    names = [n.strip() for n in open(os.environ["NAMES"])] if os.environ.get("NAMES") else []
    hit = {"rules": 0, "haiku": 0, "combined": 0}
    cost, secs, miss = 0.0, 0.0, []
    texts = [redact(t, names) for _, _, t in rows]
    got = [None] * len(rows)
    if use_haiku:
        from concurrent.futures import ThreadPoolExecutor
        def one(i):
            t = time.time()
            return haiku(rows[i][1], texts[i]) + (time.time() - t,)
        with ThreadPoolExecutor(8) as ex:
            got = list(ex.map(one, range(len(rows))))
    for (fid, kind, _), text, h in zip(rows, texts, got):
        want = SHORT[labels[fid]]
        r = rules(kind, text)[0]
        hit["rules"] += r == want
        line = [fid, want, r]
        if h:
            hit["haiku"] += h[0] == want
            hit["combined"] += combine(r, h[0]) == want
            cost += h[2]
            secs += h[3]
            line.append(str(h[0]))
        if len(set(line[1:])) > 1:
            miss.append("\t".join(line) + "\t" + text[:90])
    n = len(rows)
    print("\n".join(miss))
    print(f"rules {hit['rules']}/{n} = {hit['rules']/n:.0%}, cost $0, <1 ms each")
    if use_haiku:
        print(f"combined {hit['combined']}/{n} = {hit['combined']/n:.0%}")
        print(f"haiku {hit['haiku']}/{n} = {hit['haiku']/n:.0%}, cost ${cost:.4f} total, {secs/n:.1f} s each")


def self_test():
    r = redact("Why can't i add Juan dela Ceiz? mail juan@x.com +63 917 555 1234 id 3101d1b5-0000-4000-8000-000000000000",
               ["Juan", "dela", "Ceiz"])
    assert "Juan" not in r and "juan@x.com" not in r and "917" not in r and "3101d1b5-" not in r, r
    assert rules("IDEA", "Why not a range picker?")[0] == "FEATURE_REQUEST"
    assert rules("PROBLEM", "Got 400 error")[0] == "AUTO_FIX"
    assert rules("PROBLEM", "verify.sh up hung for 5 min with no output")[0] == "AUTO_FIX"
    assert rules("PROBLEM", "does age update when a year passes?")[0] == "NOT_A_BUG"
    assert rules("PROBLEM", "I need a way to take me back to the sessions tab")[0] == "FEATURE_REQUEST"
    assert combine("AUTO_FIX", "AUTO_FIX") == "AUTO_FIX" and combine("AUTO_FIX", "NOT_A_BUG") == "FEATURE_REQUEST"
    notify_test()
    print("triage self-test ok")


def notify_test():
    """A send that fails, or a run killed mid-loop, leaves every unsent row unsent for the next run."""
    module = sys.modules[__name__]
    order = [f"{i}0000000-0000-4000-8000-000000000000" for i in range(4)]
    rows = dict.fromkeys(order)
    sent = []

    def fake_psql(sql, *args):
        if sql == UNSENT:
            return [[f, "AUTO_FIX", "r", "user"] for f in order if rows[f] is None]
        if sql == MARK:
            rows[args[0]] = "now"
            return []
        raise AssertionError(sql)

    def fake_ntfy(title, body):
        sent.append(title)
        if len(sent) == 2:
            raise OSError("ntfy down")
        if len(sent) == 4:
            raise SystemExit("killed by the timer's timeout")

    real = module.psql, module.ntfy
    module.psql, module.ntfy = fake_psql, fake_ntfy
    try:
        try:
            notify_pending()
        except SystemExit:
            pass
        assert [rows[f] for f in order] == ["now", None, "now", None], rows
        notify_pending()
        assert all(rows.values()), rows
    finally:
        module.psql, module.ntfy = real

if __name__ == "__main__":
    a = sys.argv[1:]
    if a[:1] == ["run"]:
        run()
    elif a[:1] == ["eval"] and len(a) >= 3:
        evaluate(a[1], a[2], "--haiku" in a)
    elif a[:1] == ["self-test"]:
        self_test()
    else:
        sys.exit(__doc__)
