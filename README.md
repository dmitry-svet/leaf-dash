# LeafDash

Simple Android dashboard for a **Nissan Leaf (AZE0, 24/30 kWh)** reading live
data from an **ELM327 Bluetooth-Classic** OBD2 dongle.

Clean-room build. It uses the car's public/open-source CAN protocol only — no
LeafSpy code, assets, or branding.

## Features

Single screen (landscape puts live tiles beside a compact energy table —
one row per window, legend on top):

- **Live**: SOC %, kWh remaining, SOH %, pack Ah/Hx, speed, pack volts,
  pack amps, battery + ambient temp, odometer (km/mi toggle).
- **Energy economy — 4 windows** shown together: lifetime, since last charge,
  since car on, and a resettable trip. Each shows km, kWh, kWh/100 km, and a
  range prediction. All windows count **app-connected distance only**
  (per-session odometer deltas; driving without the app is never counted).
  Range prediction is hidden (`--`) until a window has its first km, then uses
  that window's own measured efficiency (clamped 5–60 kWh/100), applied to
  the remaining energy minus the "Unusable capacity" setting (default 2 kWh:
  weak cells cut power well above 0% SOC on degraded packs). The "Usable"
  tile shows that same reduced energy.
- **Cell health tile** (next to SOC/Battery, same height): weakest cell voltage over
  min-max cell spread, from LBC group 2102; red when min < 3.15 V (turtle
  imminent) or spread > 200 mV.
  Stationary drain (heater/AC) counts as consumption; charging while parked
  does not go negative. Regen while moving counts.
- **Distance**: smooth km = speed integral bounded to the coarse 0x5C5
  odometer within its integer-km truncation window (never leads/lags by
  more than 1 km). Corrupt/backwards odometer reads are rejected.
- **Robust sessions**: auto-reconnect every 10 s; a watchdog kills a hung
  link (frozen dongle, car turned off) after 30 s so reconnect can take over.
- **Demo mode**: runs the whole app with synthetic data — no car needed.
- **Trip log** (Settings -> Trip log): one row per drive, car on to car off
  (30 min without data ends a trip; BT dropouts stay inside it). Columns:
  date/time, start odo, duration, distance, energy Wh (net battery drain,
  idle included), Wh/km, start/end SOC and kWh, Ah, SOH, Hx, start/end pack
  V, lowest cell V and widest cell spread during the drive, temps. Distance
  and energy are summed per sample within each BT session (like the economy
  windows): a reconnect re-anchors distance on the integer-mile odometer, and
  driving while the link was down is excluded from both. Stored in
  the app's files dir (`trips.csv` + in-progress `trip_current.csv`, which
  survives an app restart); "Export CSV" saves it anywhere via the system
  file picker; the ↻ button flips the screen between portrait and landscape
  while the log is open (sensor orientation restored on leaving). The table
  keeps its header row and date/time column frozen while scrolling (the CSV
  keeps separate Date and Time columns).
- **Data log, LeafSpy format** (Settings, on by default): one row per poll
  cycle in LeafSpy's published CSV layout (151 columns A..EU, same labels
  and order) so LeafSpy log tools can read it: SOC/AHr (x10000), pack V
  (avg cell pair x 96), max/min/avg cell pair mV and diff, all 96 cell pairs,
  pack temps (sensors 1/2/4, F and C), Hx, SOH, 12V, odometer km, ambient F,
  car speed (in the GPS speed column), phone battery, epoch, HVolt1. Fields
  LeafDash can't read (GPS, gids, pack amps, tires, power by consumer,
  charge counters, VIN) are blank. One file per drive start day:
  `Android/data/com.svet.leafdash/files/LOG_FILES/Log_LeafDash_YYMMDD.csv`;
  "Export data log CSV" merges all days into one file.
- **Diagnostic log** (Settings): per-cycle CSV (distance + energy fields:
  soc, gids, Ah, pack V/A, kWh, battery temp) to a local file, and a
  separate "Stream log to PC" checkbox that POSTs the same lines to a URL
  (prefilled with the ngrok endpoint of `tools/serve_log_ngrok.sh`; lines
  are buffered and retried while the endpoint is unreachable).

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
| `0x385` | tire pressures FL/FR/RR/RL, B2..B5 / 4 PSI (timed read, every 5th cycle) |
| `0x5B3` | gids ((B4&3)<<8 \| B5) - intermittent on this AZE0 (LeafSpy too); after 3 misses retried every 20th cycle |

Broadcast reads that may never arrive use `Elm327.readBroadcastTimed` (1.5 s):
a timer CR halts ATMA, and the read handles both "STOPPED" and a bare prompt.

VCM UDS reads (`0x797` -> `0x79A`, `22 DID`, public OVMS/OBDb decodes,
confirmed on the car): every cycle `1146` motor W (x40), `1152` aux (100 W
units), `1151` A/C+PTC (250 W), `1261`/`1262` est. A/C (50 W) / heater
(250 W), `1156` gear, `1183` 12V A (/256), `1103` 12V V (/12.5), `1304`
power switch; every 10th cycle `1203`/`1205` QC / L1-L2 counts, `1234` plug,
`114E` charge mode, `1236` charge W (x100), `115D` outside temp; VIN `21 81`
once. A DID failing 3 times in a row is skipped for the session.

Meter ECU (`0x743` -> `0x763`, flow control block size 1) group `2101`:
odometer in km as the dash shows it (p9-11, past the declared ISO-TP length)
- feeds the Odo tile and logs when read, so no km/mi guess is needed there.

LBC extras: `2161` every 10th cycle = Hx (p2-3 / 102.4) and SOH% (p4-5 / 100,
the LBC's own figure); `2101` p8-11 = pack current (signed / 1024 A, + =
discharge); `2106` = balancing shunts (nibble per byte, OVMS order,
unverified: raw reply logged). Gids `0x5B3` is not broadcast on this car.

Active ISO-TP polling of the LBC (`GroupDecoder`, request `0x79B` / reply
`0x7BB`, groups `2101`–`2106`, verified against a real AZE0): kWh remaining,
SOC, SOH, Ah capacity, Hx, pack temps; group `2102` = 96 cell voltages
(2 bytes each, mV) -> min cell V + spread tiles (weakest cell triggers
turtle long before SOC hits 0 on a degraded pack).

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
