# Telemetry

The web app records what each page load does and uploads it in the background, so a reported problem can be traced and replayed. It is self-hosted: events go to the app's own Postgres, in `telemetry_event`.

## What is recorded

| Kind    | Fields                                                                                                                                                                                 |
| ------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `boot`  | app version, screen route, window size, pixel ratio, online, service worker in control                                                                                                 |
| `nav`   | the new screen route                                                                                                                                                                   |
| `tap`   | screen route and the target: tag, position path, role, `href`, `name`, and a hash (`h`) of its text and (`al`) of its `aria-label` or `title`                                          |
| `input` | the field (as for `tap`), its type, a hash of its label, and its value only for number, date, time, select and decimal fields outside `[data-pii]`; text fields send only their length |
| `api`   | method, path, status, time to answer, trace id, and the server's `db` and `app` milliseconds from `Server-Timing`                                                                      |
| `error` | screen route and the error message (first 200 characters)                                                                                                                              |

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
