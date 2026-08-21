# WS-D Rendered Evidence

Captured on an API 35 emulator (`TwentyGoApi35`, 1080x2400, 411 dp portrait) with the debug build,
Ownkey serving Chrome's omnibox. Dictation runs in debug mock mode, so the microphone reports no
measured input; the waveform correctly stays at the silence baseline instead of faking activity.

| File | Shows |
| --- | --- |
| `t016-hub-voice-card-selection-dark-portrait.png` | Pinned `Tell Ownkey what to change` card above the scrollable two-column preset grid, with the live selection count and the host selection retained. |
| `t016-recovery-rewrite-provider-missing-dark-portrait.png` | Preflight recovery naming the missing prerequisite (`Set up rewrite first`) with the resolved scope label and a fixed `Open AI settings` / `Close` rail. |
| `t016-recovery-nothing-to-rewrite-dark-portrait.png` | Empty-field recovery (`Nothing to rewrite`) with no microphone or provider work started, and the no-selection card copy. |
| `t016-shared-recording-row-dark-portrait.png` | Shared recording row: elapsed timer, centred measured waveform at the silence baseline, 48 dp pause and cancel, orange solid-square stop in the sticky dictation-key position, and the space-bar recognition-language cue as readable text. |
| `t016-shared-recording-row-paused-dark-portrait.png` | Paused state: amber dot, dim baseline waveform, resume control, subdued stop treatment, elapsed time frozen. |
| `t016-hub-landscape-split-dark.png` | Short-landscape split layout: the pinned card spans both preset columns, the grid stays two-column and scrolls, no clipped copy. |
| `t016-hub-font-scale-1_3-dark-portrait.png` | 1.3x font scale: card title and supporting copy stay unclipped and preset labels wrap inside their cards without losing their touch targets. |

## Not covered here

Light/custom theme rendering, tablet width, TalkBack focus order, announcements and custom actions,
and real-microphone amplitude calibration remain with T-018's representative device, editor and
accessibility matrix, which the delivery plan already assigns as the release gate.
