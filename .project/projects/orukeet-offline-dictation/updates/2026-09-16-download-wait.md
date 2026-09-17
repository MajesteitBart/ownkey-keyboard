---
timestamp: 2026-09-15T22:34:27Z
status: review
task: T-011
stream: WS-C
---

# Download wait correction

## Completed

Fixed unintended unmetered/non-VPN constraints, accepted Ethernet alongside Wi-Fi,
and declared resumable chunks. Waiting text now names the actual condition and offers
retry/network choice. [Regression evidence](../research/network-wait-fix.md) includes
a full metered-Wi-Fi download and a cellular consent/recovery UI check. Replacement
arm64 debug APK is ready; no application release was published.

## Blockers

Exact reported-device OS/network configuration has not been confirmed. The reproduced
metered-Wi-Fi case is fixed. Physical release qualification remains in T-009.

## Next Actions

Install the replacement internal APK and start the requested download again.
