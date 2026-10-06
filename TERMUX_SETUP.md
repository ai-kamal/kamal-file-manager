# Run DPM Command Without a PC — Termux Method

No PC needed at all. Everything done on your Android phone.

---

## Step 1 — Install Termux

Download from F-Droid (NOT Play Store — Play Store version is outdated):
https://f-droid.org/packages/com.termux/

---

## Step 2 — Install ADB inside Termux

Open Termux and run:

```bash
pkg update && pkg upgrade -y
pkg install android-tools -y
```

This installs ADB directly on your phone. Takes ~1 minute.

---

## Step 3 — Enable Wireless Debugging on YOUR OWN phone

Go to:
  Settings → Developer Options → Wireless Debugging → Toggle ON

Then tap "Wireless Debugging" text (not the toggle) and choose:
  "Pair device with pairing code"

Note down the:
- IP address + port shown (e.g. 192.168.1.5:37000)
- 6-digit pairing code

---

## Step 4 — Pair ADB to your own phone (from Termux)

In Termux, run (replace with your actual IP:PORT and CODE):

```bash
adb pair 192.168.1.5:37000 123456
```

It will say: "Successfully paired"

---

## Step 5 — Connect ADB

Back in Wireless Debugging screen, note the "IP address & port" shown at the top.
Run in Termux:

```bash
adb connect 192.168.1.5:5555
```

(Use the port shown at top of Wireless Debugging screen, not the pairing port)

---

## Step 6 — Run the Device Owner Command

```bash
adb shell dpm set-device-owner com.kovak.kamal/.DhizukuAdmin
```

Expected output:
  Active admin set to component {com.kovak.kamal/com.kovak.kamal.DhizukuAdmin}
  Active admin is now the device owner

---

## Done!

Open Kamal File Manager. It connects automatically.
No wireless debugging needed ever again after this.
