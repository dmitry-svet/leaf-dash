# LeafDash

Simple Android dashboard for a **Nissan Leaf (AZE0, 24/30 kWh)** reading live
data from an **ELM327 Bluetooth-Classic** OBD2 dongle.

Clean-room build. It uses the car's public/open-source CAN protocol only — no
LeafSpy code, assets, or branding.

## Features

Single screen:

- **Live**: SOC %, kWh remaining, SOH %, pack Ah/Hx, speed, pack volts,
  pack amps, battery + ambient temp, odometer (km/mi toggle).
- **Energy economy — 4 windows** shown together: lifetime, since last charge,
  since car on, and a resettable trip. Each shows km, kWh, kWh/100 km, and a
  range prediction. All windows count **app-connected distance only**
  (per-session odometer deltas; driving without the app is never counted).
  Range prediction is hidden (`--`) until a window has its first km, then uses
  that window's own measured efficiency (clamped 5–60 kWh/100).
  Stationary drain (heater/AC) counts as consumption; charging while parked
  does not go negative. Regen while moving counts.
- **Distance**: smooth km = speed integral bounded to the coarse 0x5C5
  odometer within its integer-km truncation window (never leads/lags by
  more than 1 km). Corrupt/backwards odometer reads are rejected.
- **Robust sessions**: auto-reconnect every 10 s; a watchdog kills a hung
  link (frozen dongle, car turned off) after 30 s so reconnect can take over.
- **Demo mode**: runs the whole app with synthetic data — no car needed.

## Build & run

### Command line (no Android Studio)

Requirements: a JDK 17 and the Android SDK. Point at them via `JAVA_HOME` and a
`local.properties` with `sdk.dir=/path/to/android-sdk` (or `ANDROID_HOME`).

Build the debug APK and run tests with the bundled wrapper:

```
export JAVA_HOME=/path/to/jdk-17
./gradlew :app:assembleDebug testDebugUnitTest
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

Install on a connected phone (USB debugging on):

```
~/android-sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Then tap **Demo** for synthetic data, or **Connect** for a real dongle (pair
the ELM327 in Android Bluetooth settings first).

### Android Studio (alternative)

Open the folder; on first sync it fetches Gradle and regenerates the wrapper
jar automatically. Run the `app` config on an API 26+ device.

### Unit tests only

```
~/tools/gradle-8.9/bin/gradle --no-daemon testDebugUnitTest
```

Pure-logic tests (no device/emulator): `CanDecoderTest`, `GroupDecoderTest`,
`Elm327Test`, `TripTrackerTest`, `LeafPollerTest`. The decoder tests assert
the code implements the documented CAN formulas; they do **not** prove the
formulas match your car — see below.

## Architecture

```
UI (Compose)        DashboardScreen + DashboardViewModel
Domain              TripTracker (3 windows), TripStore (DataStore persist)
Data/Protocol       LeafPoller -> Elm327 -> CanDecoder -> LeafState
Transport (iface)   BtSppTransport (RFCOMM) | DemoTransport | MockTransport
```

`Transport` is an interface, so all logic runs and is tested without a car.

## CAN map

Passive broadcast (`CanDecoder`, demo/monitor mode) — community decodings,
verified for **self-consistency** only:

| CAN id  | value               |
|---------|---------------------|
| `0x1DB` | pack volts + amps   |
| `0x55B` | SOC %               |
| `0x5BC` | gids                |
| `0x5C0` | battery temp (muxed)|

Broadcast ids read via hardware filter in active mode (`LeafPoller`):

| CAN id  | value               |
|---------|---------------------|
| `0x5C5` | odometer count (B1..B3; km or mi per car) |
| `0x284` | vehicle speed ((B4<<8\|B5)/100 km/h) |
| `0x510` | ambient temp (B7*0.5 - 40 C) |

Active ISO-TP polling of the LBC (`GroupDecoder`, request `0x79B` / reply
`0x7BB`, groups `2101`–`2106`, verified against a real AZE0): kWh remaining,
SOC, SOH, Ah capacity, Hx, pack temps.

## On-car checklist (phase 2 — do on the Leaf)

1. Pair the ELM327 in Android Bluetooth settings (PIN usually `1234`/`0000`).
2. **Connect** and confirm frames arrive (SOC looks right).
   - Cheap ELM327 clones may drop frames under bus load in `ATMA` monitor
     mode. If data is missing/laggy, add a CAN filter (`ATCF`/`ATCM`) in
     `Elm327.init()` or fall back to active polling.
3. Validate/adjust byte scaling in `CanDecoder` against known-good values
   (dash SOC, GOM range). Especially **speed** (`0x284`) — confirm the field
   and divisor.
4. Tune `LeafState.GID_WH` (Wh per gid) for your pack.

See `docs/plans/2026-07-13-leaf-dash-design.md` for the full design.
