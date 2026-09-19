# Download waiting with connectivity available

## Report and diagnosis

The user reported an indefinite “Waiting for the selected network” state. Device OS,
transport and VPN status have not yet been confirmed. The old request required both
Wi-Fi and `NET_CAPABILITY_NOT_METERED`, despite the UI offering “Wi-Fi only”. It also
inherited `NET_CAPABILITY_NOT_VPN` from `NetworkRequest.Builder`. Ethernet did not
qualify. Thus a working connection could be rejected before any HTTP request.

The job advertised the entire 671,619,800-byte transfer without a minimum resumable
chunk. Android may use that estimate to defer work on lower-bandwidth networks.
The downloader already supports partial-file resume, so the job now declares a
64 KiB minimum chunk.

## Changes

- Default policy accepts Wi-Fi or Ethernet, whether marked metered or unmetered.
- Mobile/other connections remain an explicit opt-in; requests no longer exclude VPN.
- UIDT and WorkManager use the same network request. Older-API worker checks preserve
  the transport guard before opening a connection.
- Waiting UI distinguishes no internet, Wi-Fi/Ethernet required, and Android scheduling.
  It offers Retry and Change download connection and no longer shows an endless
  progress animation while no transfer is running.
- Scheduler/setup failures exit waiting, and an interrupted job returns to a waiting
  state when Android will retry. Existing integrity/storage errors retain their cause.

## Sources

[NetworkRequest defaults](https://developer.android.com/develop/connectivity/network-ops/reading-network-state),
[resumable job chunk](https://developer.android.com/reference/android/app/job/JobInfo.Builder#setMinimumNetworkChunkBytes(long)),
[WorkManager network requests](https://developer.android.com/reference/androidx/work/Constraints.Builder#setRequiredNetworkRequest(android.net.NetworkRequest,androidx.work.NetworkType)).

## Verification

- Debug APK/Android test APK builds pass; release Kotlin compilation passes.
- Full app JVM suite passes: 342 tests, no failures or skips.
- Job-policy instrumentation passes: Wi-Fi/Ethernet default, no implicit unmetered or
  non-VPN requirement, no cellular transport opt-in, and a 64 KiB resumable chunk.
- Real API 36 download passes in 32.158 seconds on Wi-Fi explicitly marked metered.
  The test first proves the old request rejects that same network, then downloads
  and verifies all seven files with the new request. Cloud selection remains unchanged
  and local inference remains unloaded. Original metering settings restored afterward.
- UI check with Wi-Fi off and cellular connected: default consent waits for
  Wi-Fi/Ethernet and shows Cancel, Retry and Change download connection. Selecting
  the explicit any-connection option starts real transfer (observed 2 MB progress).
  Cancel stops it; Wi-Fi was re-enabled afterward.
- Fresh arm64 debug APK: `dist/ownkey-orukeet-network-fix-arm64.apk`, SHA-256
  `6c24107ae25a283037d81b8134f2681e13efc5da7704a091342017fec586c9f3`.
- Physical reported-device confirmation and a live VPN test remain unperformed.
  Earlier release APK/AAB evidence predates this fix; this patch rebuilds the internal
  debug APK and compiles release code without publishing an application release.

Install the replacement over the current debug app. Start the download again to
replace any previously scheduled request carrying the old network constraints.
Partial model data is retained and still checked before use.
