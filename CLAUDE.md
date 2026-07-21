# CLAUDE.md — LeafDash

Android (Compose) dashboard for a Nissan Leaf AZE0 via ELM327 Bluetooth SPP.
Clean-room: public CAN decodings only, no LeafSpy code/assets.

## Build & test

```
export JAVA_HOME=~/tools/jdk-17.0.19+10
export ANDROID_HOME=~/android-sdk
./gradlew --no-daemon :app:assembleDebug :app:testDebugUnitTest
```

APK: `app/build/outputs/apk/debug/app-debug.apk`. adb:
`~/android-sdk/platform-tools/adb install -r <apk>`.

## Conventions

- TDD for all pure logic (`trip/`, `can/`, `poll/`, `obd/`): failing test
  first. UI/BT (Compose, `BtSppTransport`, ViewModel) has no test infra —
  change carefully, keep logic out of it where possible.
- Every user-visible change: bump `versionCode`/`versionName` in
  `app/build.gradle.kts`, rebuild APK after the bump (not before), commit +
  push to origin/main directly (personal repo — no task branches, no BID
  prefix, no Co-Authored-By).
- `TripStore` has a schema version: bump it only when persisted
  `TripSnapshot` fields change shape/meaning (wipes stored trips on update).
- Update README.md feature/CAN-map sections in the same commit as behavior
  changes.

## Architecture notes (non-obvious)

- All economy windows accumulate per-sample deltas of **app-connected
  distance only**; each session rebaselines (`TripTracker.onSessionStart`),
  so off-app driving and reconnect odometer jumps are never counted.
- Distance = speed integral clamped to the 0x5C5 odometer's integer-km
  truncation bounds `[odoDelta-1, odoDelta+1]` (`LeafPoller.updateDistance`).
  Odometer readings that go backwards or jump implausibly are corrupt BT
  reads: rejected, re-anchor only after 3 in a row. Unit (km/mi) toggle
  re-anchors.
- `TripTracker` mirrors that: a single out-of-range odo delta keeps the
  baseline (transient corruption); 2 in a row rebaselines without counting.
- Energy: moving = net drain incl. regen; stationary = positive drain only
  (idle heater/AC counts, charging never goes negative). EMA efficiency
  (`avgKwhPer100`) is fed only by moving samples, clamped 5–60.
- ELM327 reads block forever (BT sockets have no timeout, odometer broadcast
  stops when car is off) — `LeafPoller` runs a watchdog thread that calls
  `stop()` after `stallTimeoutMs` (30 s) of no progress; UI auto-reconnects.
- `sessionJob` in `DashboardViewModel.connect()` must be assigned
  synchronously (before any suspension) — it is the double-connect guard.
- 0x284 speed scaling ((B4<<8|B5)/100) works for odo-bounding but is not
  confirmed as exact; raw 0x5C5 counter scale was unreliable as a distance
  source (see revert ac8d903) — don't integrate distance from it directly.

## Lessons learned

- ELM327 `ATMA` monitor mode drops 0x5C5 on cheap clones; read it via
  hardware filter (`ATCRA5C5` + `ATMA`, the LeafSpy way) instead.
- `IsoTp.reassemble` input is per-frame lines even with `ATCAF1`; consecutive
  frames without their first frame are a partial capture — return empty, the
  declared length is unknown.
