# LeafDash

Simple Android dashboard for a **Nissan Leaf (AZE0, 24/30 kWh)** reading live
data from an **ELM327 Bluetooth-Classic** OBD2 dongle.

Clean-room build. It uses the car's public/open-source CAN protocol only — no
LeafSpy code, assets, or branding.

## Features (MVP)

Single screen:

- **Live**: SOC %, gids → kWh remaining, live power (kW), speed, pack volts,
  pack amps, battery temp.
- **Energy economy — 3 windows** shown together: since last charge, since car
  on, and a resettable trip. Each shows km, kWh, and kWh/100 km.
- **Demo mode**: runs the whole app with synthetic data — no car needed.

Not yet: SOH %, pack Ah, cell voltages (need active ISO-TP queries — phase 2).

## Build & run

### Command line (no Android Studio)

Toolchain already installed on this machine:

- JDK 17: `~/tools/jdk-17.0.19+10`
- Gradle 8.9: `~/tools/gradle-8.9`
- Android SDK: `~/android-sdk` (platform-34, build-tools 34.0.0, platform-tools)
- `local.properties` points at the SDK.

Build the debug APK and run tests:

```
export JAVA_HOME=~/tools/jdk-17.0.19+10
export ANDROID_HOME=~/android-sdk
~/tools/gradle-8.9/bin/gradle --no-daemon :app:assembleDebug testDebugUnitTest
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

Pure-logic tests (no device/emulator): `CanDecoderTest`, `Elm327Test`,
`TripTrackerTest`, `LeafPollerTest`. The decoder tests assert the code
implements the documented CAN formulas; they do **not** prove the formulas
match your car — see below.

## Architecture

```
UI (Compose)        DashboardScreen + DashboardViewModel
Domain              TripTracker (3 windows), TripStore (DataStore persist)
Data/Protocol       LeafPoller -> Elm327 -> CanDecoder -> LeafState
Transport (iface)   BtSppTransport (RFCOMM) | DemoTransport | MockTransport
```

`Transport` is an interface, so all logic runs and is tested without a car.

## CAN map (AZE0 broadcast) — APPROXIMATE

Byte formulas in `CanDecoder` are community/documented decodings and may need
tuning per car. Verified for **self-consistency** only.

| CAN id  | value               |
|---------|---------------------|
| `0x1DB` | pack volts + amps   |
| `0x55B` | SOC %               |
| `0x5BC` | gids                |
| `0x5C0` | battery temp (muxed)|
| `0x284` | vehicle speed (scaling UNCONFIRMED) |

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
