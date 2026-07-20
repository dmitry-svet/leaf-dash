# LeafDash — design

Simple Android dashboard for a Nissan Leaf (AZE0, 24/30 kWh) reading live data
from an ELM327 Bluetooth-Classic OBD2 dongle.

Clean-room build. The CAN protocol is the car's (public / open-source
documented). No LeafSpy code, assets, or branding are used — only the car's
protocol and open references.

## Scope (MVP)

Single dashboard screen showing:

- **Live battery/power** tiles: SOC %, gids (→ kWh remaining / range est),
  live power (kW), speed, battery temp, gids. (Pack volts / pack amps are
  decoded — needed to compute power — but not shown as tiles for now.)
- **Trip economy — 3 windows** shown together on the same screen:
  - since last charge
  - since car on (this session)
  - trip (user-resettable)
  Each window: km driven, kWh spent, kWh/100 km. Rendered as legend-left /
  value-right rows, the value at the same size as the live tiles.

UI notes:
- Screen stays awake (`FLAG_KEEP_SCREEN_ON`) while connected — driving use.
- Permissions (target SDK 34): `BLUETOOTH_CONNECT` (required),
  `BLUETOOTH_SCAN` (optional, `neverForLocation`; only for `cancelDiscovery`,
  which is best-effort). No location permission needed.

Out of scope for MVP (phase 2): SOH %, pack Ah capacity, individual cell
voltages. These need **active ISO-TP diagnostic** queries (0x79B → 0x7BB); MVP
uses **passive broadcast frames only**. gids is used as the battery-health /
capacity proxy for now.

## Hardware / target

- Android (Kotlin, Jetpack Compose).
- ELM327 **Bluetooth Classic (SPP)** → Android RFCOMM socket.
- Nissan Leaf AZE0 24/30 kWh.

## Architecture

```
UI (Compose)                dashboard: live tiles + 3 trip cards + Reset
  |  StateFlow
ViewModel / State           LeafState, TripStats x3
  |
Domain
  TripTracker               3 windows, reset logic, persistence
  EnergyCalc                integrate power->kWh, speed->km, kWh/100km
  |
Data / Protocol
  LeafPoller                coroutine loop: read -> decode -> emit
  CanDecoder                frame bytes -> typed values
  Elm327                    AT init, CAN filter, ATMA monitor, line parse
  |
Transport (interface)
  BtSppTransport            RFCOMM to real dongle
  MockTransport             replays recorded frames (tests / off-car dev)
```

**Transport is an interface** so all logic above it runs and is unit-tested
without a car. `MockTransport` feeds recorded/synthetic frames.

**Data flow**: `LeafPoller` loop → `Elm327` reads hex lines from transport →
`CanDecoder` turns frame bytes into values → merges into `LeafState`
(StateFlow) → `TripTracker` folds each sample into the 3 windows → UI observes.

## CAN protocol map (AZE0, broadcast)

Decoded by `CanDecoder`. **Byte formulas below are approximate (from public
docs / memory); exact scaling is verified against the real car during
on-car step.**

| CAN ID  | Data                | Decode (approx)                              |
|---------|---------------------|----------------------------------------------|
| `0x1DB` | pack volts, amps    | V = (b2<<2 \| b3>>6)/2 ; A = signed(b0,b1)/2  |
| `0x55B` | SOC %               | SOC = (b0<<2 \| b1>>6)/10                     |
| `0x5BC` | gids                | gids = (b0<<2 \| b1>>6) ; kWh ≈ gids*0.08     |
| `0x5C0` | battery temp        | from b2 (scaling TBD)                         |
| `0x1DA` | motor rpm / power   | (optional live)                              |
| `0x284`/`0x285` | vehicle/wheel speed | for km integration                     |

Derived:
- **Live power kW** = packV * packA / 1000  (+discharge / −regen)
- **kWh spent** = ∫ power dt   (trapezoid per sample)
- **km** = ∫ speed dt
- **kWh/100 km** = kWh_spent / km * 100  (per window)

## TripTracker (3 windows)

Each window holds `{km, kWh, startTime}`.

| Window            | Reset trigger                                   |
|-------------------|-------------------------------------------------|
| Since last charge | charge detected, then driving resumes           |
| Since car on      | new connection / ignition-on (new session)      |
| Trip              | user taps Reset                                 |

Per poll sample (~0.5–1 s): `dt` since previous → `kWh += power*dt`,
`km += speed*dt`; fold into all 3 windows.

**Charge detection (passive):** gids rises steadily while stationary →
charging; gids stops rising + car moves → charge ended → reset "since last
charge". Heuristic; tuned on real data.

**Persistence:** DataStore — accumulators survive app kill / BT disconnect,
restored on launch.

## Key risk

Cheap ELM327 clones can drop frames under Leaf bus load in passive monitor
mode (`ATMA`). Mitigation: set CAN filter/mask (`ATCF`/`ATCM`) to pass only
the ~5 needed IDs. **First on-car task = confirm the dongle streams these
frames reliably.** Fallback if not: active polling (re-adds ISO-TP layer).

## Build plan

1. Scaffold Android project (Compose/Kotlin/Gradle) → builds empty app
2. Transport interface + MockTransport → test: emits frames
3. CanDecoder + tests → bytes → values
4. Elm327 (AT init, filter, ATMA) → parses live stream
5. LeafPoller + LeafState StateFlow → mock drives state
6. EnergyCalc + TripTracker + persistence → test: known drive → kWh
7. Compose dashboard → renders mock state
8. BtSppTransport (RFCOMM) + picker + permissions → connects real dongle
9. On-car verify → confirm frames, tune byte formulas

Steps 1–7 need no hardware. TDD on pure-Kotlin logic (decoder, energy,
triptracker). Steps 8–9 use the real Leaf + dongle.

## Legal note

Reverse-engineering the car's CAN protocol for interoperability is the basis
here; protocol facts are public / open-source. No LeafSpy source, layout
assets, or branding are copied.
