# LeafDash — Privacy Policy

Last updated: 2026-07-27

LeafDash is an Android app that reads live data from a Nissan Leaf via a
Bluetooth ELM327 OBD2 adapter and shows it on a dashboard.

## Data we collect
None. LeafDash does not collect, transmit, or share any personal data.

## Data on your device
- Vehicle readings (state of charge, health, temperatures, odometer, speed)
  are read live from your car over Bluetooth and shown on screen.
- Trip/economy totals and app settings (units, last paired adapter) are stored
  locally on your device only, so they persist between sessions.
- An optional diagnostic log (off by default) writes vehicle values to a file
  in the app's private storage on your device. It is never uploaded.

Nothing is sent to any server. There are no analytics, ads, or third-party SDKs.

## Permissions
- Bluetooth (connect/scan): to talk to the OBD2 adapter. Not used for location.
- Notifications: to show the ongoing "connected" notification while a session
  is active.
- Foreground service: to keep the Bluetooth connection alive while the app is
  in the background during a drive.

## Contact
dmitry.svet (via the GitHub repository: github.com/dmitry-svet/leaf-dash)
