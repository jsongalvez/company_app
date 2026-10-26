#!/usr/bin/env python3
"""Phone alert when a key route is slow. Run every minute by the alert timer, after probe.mjs wrote rows.

A route is in breach when its last two probe runs are both good and over its limit, or when real traffic
in the last hour has 20 or more requests with a p95 over the limit. A breach sends one alert, a recovery
(two good probe runs under the limit; for a breach seen in real traffic, 20 fresh real requests under the limit too) sends one message, and real traffic is counted only from then on. Failed probe runs
say nothing: the app being down is the health alert's job. State: one file per route in ALERT_STATE.
Settings come from the environment so the tests can point them elsewhere:
PROBE_LOG, APP_LOG, LIMITS, ALERT_STATE, NOW (ISO, tests only), NOTIFY_CMD (tests only), TEST_TOPIC.
"""
import json, os, re, subprocess, sys, urllib.request
from datetime import datetime, timedelta, timezone
from pathlib import Path

HOME = Path.home()
HERE = Path(__file__).resolve().parent
PROBE_LOG = Path(os.environ.get("PROBE_LOG", HOME / "logs/company_app/probe.jsonl"))
APP_LOG = Path(os.environ.get("APP_LOG", HOME / "logs/company_app/app.log"))
LIMITS = Path(os.environ.get("LIMITS", HERE / "limits.json"))
STATE = Path(os.environ.get("ALERT_STATE", HOME / ".local/state/company-app-alerts"))
MIN_REAL = 20
RUNS_IN_A_ROW = 2
PROBE_STALE = timedelta(minutes=20)
COMPLETED = re.compile(r'(\{"event":"request_completed".*\})')


def parse_ts(s):
    return datetime.fromisoformat(s.replace("Z", "+00:00"))


NOW = parse_ts(os.environ["NOW"]) if os.environ.get("NOW") else datetime.now(timezone.utc)


def log_ts(line):
    """The first timestamp on an app-log line: Docker's (UTC, ends in Z) or the app's own (host time)."""
    m = re.search(r"(\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d)(?:[.,](\d{1,9}))?(Z?)", line)
    if not m:
        raise ValueError("no timestamp")
    t = datetime.fromisoformat(m.group(1) + "." + (m.group(2) or "0").ljust(6, "0")[:6])
    return t.replace(tzinfo=timezone.utc) if m.group(3) else t.astimezone()


def p95(values):
    v = sorted(values)
    return v[max(0, -(-95 * len(v) // 100) - 1)]


def probe_runs(route):
    """Recent probe rows for a route, oldest first, failed runs included. Stale rows are not data."""
    if not PROBE_LOG.exists():
        return []
    rows = []
    for line in PROBE_LOG.read_text().splitlines():
        try:
            r = json.loads(line)
        except ValueError:
            continue
        if r.get("route") == route and NOW - parse_ts(r["ts"]) < PROBE_STALE:
            rows.append(r)
    return rows


def real_requests(spec, since):
    """Completed requests for the route template in the last hour (and after the last recovery), from the app log."""
    if not APP_LOG.exists():
        return []
    cut = max(NOW - timedelta(hours=1), since)
    out = []
    with APP_LOG.open(errors="replace") as f:
        for line in f:
            if spec["template"] not in line:
                continue
            m = COMPLETED.search(line)
            if not m:
                continue
            try:
                e = json.loads(m.group(1))
                ts = log_ts(line)
            except ValueError:
                continue
            if ts >= cut and e.get("template") == spec["template"] and e.get("method") == spec["method"] and e.get("status", 500) < 500:
                out.append(e)
    return out


def notify(title, message, priority, tags):
    if os.environ.get("NOTIFY_CMD"):
        subprocess.run([os.environ["NOTIFY_CMD"], title, message, priority, tags], check=False)
        return
    env = dict(l.split("=", 1) for l in (HOME / ".config/company-app-alerts.env").read_text().splitlines() if "=" in l)
    topic = os.environ.get("TEST_TOPIC") or env["NTFY_TOPIC"].strip()
    req = urllib.request.Request(
        f"{env['NTFY_URL'].strip()}/{topic}",
        data=message.encode(),
        headers={"Title": title, "Priority": priority, "Tags": tags},
    )
    try:
        urllib.request.urlopen(req, timeout=15).read()
    except OSError as e:
        print(f"ntfy send failed: {title}: {e}", file=sys.stderr)


def split(db_ms, total_ms):
    where = "database" if db_ms * 2 > total_ms else "code"
    return f"{max(total_ms - db_ms, 0)}ms in code, {db_ms}ms in database", where


def evaluate(route, spec, since):
    """Returns (breach_or_None, probe_under). A breach is (reason, text, trace id), reason probe or real."""
    limit = spec["limitMs"]
    runs = probe_runs(route)
    last = [r for r in runs[-RUNS_IN_A_ROW:] if r["ok"]]
    probe_over = len(last) == RUNS_IN_A_ROW and all(r["dbMs"] + r["appMs"] > limit for r in last)
    probe_under = len(last) == RUNS_IN_A_ROW and all(r["dbMs"] + r["appMs"] <= limit for r in last)
    real = real_requests(spec, since)
    real_over = len(real) >= MIN_REAL and p95([e["elapsedMs"] for e in real]) > limit
    if probe_over:
        worst = max(last, key=lambda r: r["dbMs"] + r["appMs"])
        total = worst["dbMs"] + worst["appMs"]
        shares, where = split(worst["dbMs"], total)
        return ("probe", f"p95 {total}ms over {limit}ms limit in {RUNS_IN_A_ROW} probe runs. {shares}.", worst["trace"]), probe_under
    if real_over:
        worst = max(real, key=lambda e: e["elapsedMs"])
        shares, where = split(worst.get("dbMs", 0), worst["elapsedMs"])
        return ("real", f"p95 {p95([e['elapsedMs'] for e in real])}ms over {limit}ms limit in {len(real)} real requests in the last hour. Slowest: {shares}.", worst["traceId"]), probe_under
    return None, probe_under


def main():
    STATE.mkdir(parents=True, exist_ok=True)
    for route, spec in json.loads(LIMITS.read_text()).items():
        flag = STATE / f"latency-{route}"
        recovered_at = STATE / f"latency-{route}.recovered"
        since = parse_ts(recovered_at.read_text()) if recovered_at.exists() else NOW - timedelta(hours=1)
        breach, probe_under = evaluate(route, spec, since)
        if flag.exists():
            reason, _, alerted_at = flag.read_text().partition(" ")
            if reason == "probe":
                cleared = probe_under
            else:
                fresh = real_requests(spec, parse_ts(alerted_at))
                cleared = probe_under and len(fresh) >= MIN_REAL and p95([e["elapsedMs"] for e in fresh]) <= spec["limitMs"]
            if cleared:
                notify(f"{spec['label']} is back", f"{spec['label']} is back under its {spec['limitMs']}ms limit.", "default", "white_check_mark")
                flag.unlink()
                recovered_at.write_text(NOW.isoformat())
                print(f"recovered {route}")
        elif breach:
            reason, text, trace = breach
            notify(f"{spec['label']} slow", f"{spec['label']} slow. {text} Run perf-slow {trace}", "high", "snail")
            flag.write_text(f"{reason} {NOW.isoformat()}")
            print(f"alerted {route}")


if __name__ == "__main__":
    main()
