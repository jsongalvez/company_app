#!/usr/bin/env node
// Gate checker — runs CHECK commands from a gates file, flips boxes only when
// EXPECT matches, writes the deciding output as EVIDENCE.
// Format spec: docs/agents/gates.md
// Zero-dep Node 16+.

import { readFileSync, writeFileSync, renameSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { spawnSync } from "node:child_process";

const ROOT = fileURLToPath(new URL("..", import.meta.url));
const BOX_RE = /^\s*- \[([ x])] (G\d+):\s*(.*)$/;
const BOX_ANY_RE = /^\s*- \[/;

const usage = `usage: node scripts/gate-check.mjs [--dry] <gates-file>...

Runs every gate's CHECK (from the repo root), flips its box [x] and writes
EVIDENCE only when EXPECT matches. Exit 0 iff every gate is met.
--dry  print the verdicts without writing the file.`;

const EXPECT_DEFAULT = "EXIT 0";

function testRegex(pattern, stdout, stderr) {
  const raw = (stdout === "" && stderr === "") ? "" : `${stdout}\n${stderr}`;
  if (pattern === "^$") return raw.replace(/\n+$/, "") === "";
  const data = raw.replace(/\n+$/, "");
  const script = `const fs=require("node:fs");const d=fs.readFileSync(0,"utf8");try{process.stdout.write(String(new RegExp(process.argv[1],"m").test(d)))}catch{process.stdout.write("false")}`;
  const r = spawnSync(process.execPath, ["-e", script, pattern], {
    input: data.slice(0, 200_000),
    encoding: "utf8",
    timeout: 5_000,
  });
  return r.stdout?.trim() === "true";
}

function matchExpect(expect, stdout, stderr, code) {
  const exit = /^EXIT (\d+)$/.exec(expect);
  if (exit) return code === Number(exit[1]);
  const matches = /^MATCHES (.+)$/s.exec(expect);
  if (matches) return testRegex(matches[1], stdout, stderr);
  const combined = `${stdout}\n${stderr}`;
  return combined.includes(expect);
}

function parse(lines) {
  const gates = [];
  const errors = [];
  let cur = null;
  lines.forEach((line, i) => {
    if (BOX_ANY_RE.test(line)) {
      const box = BOX_RE.exec(line);
      if (!box) {
        errors.push(`line ${i + 1}: malformed box line (expected format: "- [ ] G<n>[:] title"): ${line.trim()}`);
        return;
      }
      cur = { line: i, id: box[2], title: box[3], check: null, expect: null, evidence: null, lastField: i };
      gates.push(cur);
      return;
    }
    if (!cur) {
      if (/^\s*(CHECK|EXPECT|EVIDENCE):/.test(line)) {
        errors.push(`line ${i + 1}: field line outside any gate (${line.trim()}) — fields belong under a "- [ ]" box`);
      }
      return;
    }
    if (line.trim() === "") { cur = null; return; }
    const check = /^\s*CHECK:\s*(.+)$/.exec(line);
    if (check) { cur.check = check[1]; cur.lastField = i; return; }
    const expect = /^\s*EXPECT:\s*(.*)$/.exec(line);
    if (expect) { cur.expect = expect[1]; cur.lastField = i; return; }
    const evidence = /^\s*EVIDENCE:\s*(.*)$/.exec(line);
    if (evidence) { cur.evidence = { line: i }; cur.lastField = i; }
  });
  return { gates, errors };
}

function runGate(gate) {
  if (gate.check === null) return { ok: false, why: "no CHECK line" };
  const expect = (gate.expect === null ? EXPECT_DEFAULT : gate.expect).trim();
  if (gate.expect !== null && expect === "") {
    return { ok: false, why: "blank EXPECT — EXPECT: EXIT N, MATCHES <regex>, or text; not empty" };
  }
  if (expect.startsWith("EXIT")) {
    if (!/^EXIT \d+$/.test(expect)) {
      return { ok: false, why: `malformed EXPECT "${expect}" — EXIT takes a number, or use MATCHES / plain text` };
    }
  }
  const r = spawnSync("sh", ["-c", gate.check], { cwd: ROOT, encoding: "utf8", maxBuffer: 64 * 1024 * 1024, timeout: 60_000 });
  if (r.error || r.status === null) {
    return { ok: false, why: `CHECK failed (${r.error?.code ?? "killed"})`, evidence: `${r.error?.code ?? "killed"} — partial output not trusted` };
  }
  const code = r.status ?? "?";
  const combined = (r.stdout ?? "") + "\n" + (r.stderr ?? "");
  const ok = matchExpect(expect, r.stdout ?? "", r.stderr ?? "", r.status ?? -1);
  const out = combined.split("\n").map((l) => l.trim()).find((l) => l !== "");
  return {
    ok,
    why: ok ? null : `EXPECT "${expect}" not matched (exit ${code})`,
    evidence: out === undefined ? `exit ${code}` : out.slice(0, 200),
  };
}

let dry = false;
const files = [];
for (const a of process.argv.slice(2)) {
  if (a === "--dry") dry = true;
  else files.push(a);
}
if (files.length === 0) {
  console.error(usage);
  process.exit(2);
}

let allOk = true;
for (const file of files) {
  let src;
  try {
    src = readFileSync(file, "utf8");
  } catch {
    console.error(`${file}: not found`);
    allOk = false;
    continue;
  }
  const lines = src.split("\n").map((l) => l.replace(/\r$/, ""));
  const { gates, errors } = parse(lines);
  if (errors.length > 0) {
    for (const e of errors) console.error(`${file}: ${e}`);
    allOk = false;
    continue;
  }
  if (gates.length === 0) {
    console.error(`${file}: no gates found`);
    allOk = false;
    continue;
  }
  let okCount = 0;
  const failures = [];
  for (const gate of gates) {
    const result = runGate(gate);
    if (result.ok && gate.evidence === null && !dry) {
      lines.splice(gate.lastField + 1, 0, `  EVIDENCE: ${result.evidence}`);
      gate.evidence = { line: gate.lastField + 1 };
      gate.lastField++;
      for (const g of gates.slice(gates.indexOf(gate) + 1)) { g.line++; g.lastField++; if (g.evidence) g.evidence.line++; }
    } else if (gate.evidence !== null && !dry) {
      lines[gate.evidence.line] = lines[gate.evidence.line].replace(/EVIDENCE:.*/, () => `EVIDENCE: ${result.ok ? result.evidence : "pending"}`);
    }
    if (result.ok) okCount++;
    else failures.push(`${gate.id}: ${result.why}`);
    if (!dry) {
      lines[gate.line] = lines[gate.line].replace(/^\s*- \[(.)\]/, `- [${result.ok ? "x" : " "}]`);
    }
    console.log(`${result.ok ? "PASS" : "FAIL"}  ${file} ${gate.id}: ${gate.title}`);
    if (!result.ok) console.log(`      ${result.why}`);
    else console.log(`      evidence: ${result.evidence}`);
  }
  if (!dry) {
    try {
      writeFileSync(`${file}.tmp.${process.pid}`, lines.join("\n"));
      renameSync(`${file}.tmp.${process.pid}`, file);
    } catch {
      console.error(`${file}: cannot write`);
      allOk = false;
      continue;
    }
  }
  console.log(`${file}: ${okCount}/${gates.length} gates met`);
  allOk = allOk && okCount === gates.length;
}

process.exit(allOk ? 0 : 1);