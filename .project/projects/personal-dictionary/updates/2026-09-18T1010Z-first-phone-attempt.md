---
timestamp: 2026-09-18T10:10:13Z
status: blocked
task: T-005
stream: WS-A
---

# Progress Update

## Completed
- First physical arm64 attempt: on a Galaxy Z Fold7 the debug build downloaded the model, but Activate stayed on "Loading local model" and Orukeet did not become active. No device log was available, so the cause is not known yet.
- The same build activates on the API 35 x86_64 emulator, both with a side-loaded model and after a download through the app. The model loads in about 1.5 s there.
- Killing the inference process on the emulator reproduced the screen from the phone. The card showed only the generic transcription message afterwards and the service logged nothing.
- The card now names load failures (process stopped, timeout, runtime error) and internal builds add a technical detail line. The inference service and the connection log failures and load timing under the tag `OwnkeyAsr`.
- The dictionary screen now says when system voice input is selected, because that backend never uses the dictionary.

## Blockers
- T-005 stays blocked until the phone reports the technical detail line or a log from a new attempt.

## Next
- Install the new arm64 debug build on the phone, press Activate and read the detail line under the error.
