# Telemetry

The web app records what each page load does and uploads it in the background, so a reported problem can be traced and replayed. It is self-hosted: events go to the app's own Postgres, in `telemetry_event`.

## What is recorded

| Kind    | Fields                                                                                                                                                                                                                          |
| ------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `boot`  | app version, screen route, window size, pixel ratio, online, service worker in control                                                                                                                                          |
| `nav`   | the new screen route                                                                                                                                                                                                            |
| `tap`   | screen route and the target: tag, position path, role, `href`, `name`, and a hash (`h`) of its text and (`al`) of its `aria-label` or `title`                                                                                   |
| `input` | the field (as for `tap`), its type, a hash of its label, and its value only for number, date, time, select and decimal fields outside `[data-pii]`; text fields send only their length                                          |
| `api`   | method, path, status, time to answer, trace id, and the server's `db` and `app` milliseconds from `Server-Timing`; a request that never got an answer has status 0 and the error name (`err`)                                   |
| `error` | screen route and the error message (first 200 characters)                                                                                                                                                                       |
| `net`   | per `/api` and `/auth` request and the page load itself: path, protocol (`h3`, `h2`, `http/1.1`), ms in DNS, connect (`cn`, null when the connection was reused) and TLS, `wait` before the request was sent, `ttfb`, and total |
| `vis`   | the tab hidden or shown again (with `away` ms and online), and online/offline; plus the phone's link guess (`et`, `rtt`, `dl`) where the browser shares it                                                                      |

Nothing typed into a text field and no label text leaves the phone. Routes keep only the `date`, `from`, `to`, `tab`, `view` and `branch` query values. The client form is marked `data-pii`, so its age and blood pressure stay out too. Mark any other form that holds personal details the same way.

A problem report carries `Telemetry: <session id>` in its details, so the replay tool finds the page load that sent it.

## Upload and limits

- Events wait in memory and go up in one request at most every 15 s, and at once when the page is hidden or a problem report is sent. Nothing is sent before sign-in or while the upload is on the path of a tap or a save.
- One request holds at most 200 events and about 48 KB. At most 1,000 events wait on the phone; the oldest go first.
- The server stores each event once per session and sequence number, so a resent batch is not doubled. It accepts 10 uploads a minute per person.
- Rows older than 14 days are deleted, and past 500,000 rows the oldest are deleted. The trim runs inside an upload every 10 minutes or 20,000 rows.

## Reading it

```
docker exec <prod postgres> psql -U company_user -d company_app -c \
  "select at, kind, data from telemetry_event where session_id = '<id>' order by seq"
```

## Replaying a problem report

```
tools/replay/replay.sh <feedback id prefix | session id> [--pii] [--keep] [--source prod|local:DBNAME]
```

1. It finds the report and the page load that sent it (the `Telemetry:` line in its details, else the reporter's last page load in the 30 minutes before) and exports that page load's events.
2. It copies the database (telemetry rows left out), starts the copy with this checkout's code on port 18192 (`tools/verify/verify.sh` with `VERIFY_DUMP`), and runs `tools/replay/redact.sql`. Names, contacts, health notes, free text and audit values of those fields are replaced; ids, amounts, dates and business names stay. Every password becomes the dev owner's. The script stops if any client name, phone, address or staff name is left.
3. It plays the events in a browser at the recorded window size, signed in as the reporter, and finds each tap target with the recorder's own `describe()`. Text fields get stand-in text of the recorded length. The recorded sign-in is not played.
4. It prints a verdict: REPRODUCED when every failed call comes back with the same status and every recorded error appears again, NOT REPRODUCED otherwise, or NO FAILURE RECORDED (then read the screenshots). It also prints a call-by-call comparison and saves screenshots and `report.json` under `/tmp/replay/<id>-<time>/`.

`--pii` skips the redaction. Use it only when the problem depends on the real details, and say so in the reply. `--keep` leaves the copy running to try fixes against it. Reports from before telemetry have no events: the replay opens the reported screen at the reported size.

The copy runs the code of this checkout, not the version in the report (printed on the first line). Its data is the database now, not at the time of the report.

## Disk

The table is capped at 500,000 rows (about 175 MB with indexes, estimated from about 350 bytes a row). The nightly backup leaves out its rows (`--exclude-table-data=telemetry_event`). Replay copies live in the dev Postgres until `verify.sh down`.

### Moving bulk data to the unused Oracle storage

The VPS boot volume is 48 GB. The Oracle free tier allows 200 GB of block storage in total, and about 150 GB is unused. Attaching it is a console step for the owner:

1. Oracle Cloud console: Storage, Block Volumes, Create Block Volume. Same availability domain as the instance, size 150 GB, default performance.
2. Compute, Instances, the VPS, Attached block volumes, Attach block volume. Pick the new volume, attachment type Paravirtualized, read/write.
3. On the VPS: `lsblk` shows a new 150 GB disk (for example `sdb`). Then:
    ```
    sudo mkfs.ext4 -L data /dev/sdb
    sudo mkdir -p /mnt/data
    echo 'LABEL=data /mnt/data ext4 defaults,nofail 0 2' | sudo tee -a /etc/fstab
    sudo mount -a && df -h /mnt/data
    ```
4. Point bulky things at it: `REPLAY_OUT=/mnt/data/replay`, Docker's data root, or a longer telemetry retention. Telemetry itself stays in the app database, which is small.
