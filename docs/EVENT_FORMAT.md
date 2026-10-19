# Owned-app event log format (`blackbox-events` v1)

Apps you own (KITT, RetroTV, ...) can write a small structured event log that BLACKBOX imports as
evidence. It is **import-only**: BLACKBOX exposes no receiver, port or exported component. Export the
file from your app (share sheet, `adb pull`, Files), then in BLACKBOX use
**Quick capture → Import app event log (.json)**.

## What an import does
- The original file is copied into app storage as the evidence attachment (unchanged, checksummed).
- One `LOG` evidence record is created with source `INSTRUMENTATION`, a readable rendering of the events
  (capped at 256 KB of text; the full file stays attached) and labels `instrumentation`, `<app>`.
- Notable events (`level` WARN/WARNING/ERROR/FATAL/CRITICAL/ASSERT, or `"mark": true`) are added to the
  investigation timeline, up to 200. Everything else is only in the evidence text.
- Event content is untrusted data. It is never executed or treated as instructions.

## Document form
```json
{
  "format": "blackbox-events",
  "version": 1,
  "app": "KITT",
  "appVersion": "2.3.1",
  "device": "Pixel Fold",
  "session": "drive-2026-10-18",
  "timezone": "America/Chicago",
  "events": [
    {"t": "2026-10-18T21:00:00Z", "level": "info",  "type": "obd.connect", "message": "ELM327 connected"},
    {"t": "2026-10-18T16:01:00",  "level": "error", "type": "obd.write",   "message": "Broken pipe", "data": {"bytes": 0}},
    {"type": "screen.off", "mark": true}
  ]
}
```
All top-level fields except `events` are optional. A bare JSON array of events, or **JSON Lines**
(one event object per line, handy for appending from an app) are also accepted.

## Event fields
| Field | Aliases | Notes |
|---|---|---|
| `t` | `time`, `timestamp` | ISO-8601 with `Z` or offset; epoch **milliseconds** as a number; or a local time, which is resolved only if the document declares `timezone`. Otherwise the time is recorded as **unknown**, never guessed. |
| `type` | `event`, `name` | Short event name. Defaults to `event`. |
| `level` | | Free text; the levels above make the event notable. |
| `message` | `msg` | Up to 4000 characters kept. |
| `data` | | Any JSON value, kept as compact JSON (4000 characters). |
| `mark` | | `true` forces the event onto the timeline. |

## Limits and errors
- 8 MB file, 50,000 events. Larger logs are rejected with a clear message.
- A different `format`, a newer `version`, an unknown `timezone`, an empty log or non-JSON content is rejected and nothing is saved.
- Malformed JSON Lines are skipped with a warning (shown after import, and in the evidence text).

## Writing it from your app (Kotlin sketch)
```kotlin
// Append one line per event to a file in your app's own storage, then share it.
fun logEvent(file: File, type: String, level: String, message: String) =
    file.appendText(JSONObject()
        .put("t", Instant.now().toString()).put("type", type)
        .put("level", level).put("message", message).toString() + "\n")
```
A sample is in [`samples/kitt-events.json`](samples/kitt-events.json).
