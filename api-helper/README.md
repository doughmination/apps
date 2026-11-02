# Device Reporter

A little Android app that quietly tells your own API how your phone is doing — battery,
charging, low-power mode, Wi-Fi, Bluetooth earbuds, and roughly where it is. It's the
free, self-hosted answer to those paid "battery status" apps, built for one phone: mine
(currently a Galaxy S26 Ultra on One UI 9, but it's plain Android 8+).

Under the hood it just POSTs to your endpoint with the fields in the query string and your
secret key in a header (no request body):

```
POST https://doughmination.uk/v2/devices?device=phone&level=25&charging=1&lpm=0&wifi=Home
X-Battery-Key: <your key>
```

Your API only touches the fields it actually receives, so the app is happy to send a full
picture every time without clobbering anything it didn't mean to.

## When it phones home

It reports the moment something interesting happens, plus a heartbeat every five minutes:

- You join or leave a Wi-Fi network, or hop between networks → sends the SSID (or `wifi=0`
  when you're off Wi-Fi).
- You plug in or unplug the charger.
- Battery Saver flips on or off.
- Your chosen Bluetooth earbuds connect or disconnect.
- Every 5 minutes regardless, as a battery check — and this one also includes your location.
  It's an alarm, so it fires even while the phone is asleep in your pocket.

## What each field means

- **device** — whatever name you set in the app.
- **level** — battery percentage, 0–100.
- **charging** — `1` plugged in, `0` on battery. This is "charger attached", not "battery
  filling up", so Samsung's battery protection (stop at 80/85%) still reads as `1`.
- **lpm** — `1`/`0` from Battery Saver (One UI calls it Power saving).
- **wifi** — the network name when connected, or `0` when you're not.
- **watch** — a manual switch. An Apple Watch can't pair with Android, so there's nothing to
  detect automatically; flip it yourself if you ever want `watch=1`.
- **airpods** — `1`/`0` based on a Bluetooth device you choose. Tap **Select AirPods device**,
  pick your buds from the paired list, and the app remembers them and reports when they
  connect or drop.
- **location** — sent on the 5-minute heartbeat only. The app takes your last-known fix
  (asking for a fresh one if that's over 10 minutes old) and turns it into readable text
  like `London, England, United Kingdom` (never raw coordinates). If it can't get a
  location, it just leaves the field out.

Every value is URL-encoded, so spaces, commas, and symbols in a network name or place name
travel safely.

## The settings screen

Base URL (defaults to `https://doughmination.uk/v2`), your **X-Battery-Key**, and a device
name (defaults to `phone`; set it to whatever your API expects). Below that: the manual
**watch** switch, an **AirPods** toggle with a device picker, a **Background** panel that
shows which of the three keep-alive settings below are still missing (with a button for
each), and buttons to **Start**/**Stop** reporting, **Save**, and **Send test** — the test
shows you the exact URL it sent and whether the server liked it. Your key lives only on the
phone; it's never baked into the APK.

It wears the same colours and IBM Plex Sans as doughmination.gay, and its icon is the
doughnut family's: bat-winged doughnut over a phone sending signal. The source art is
`art/icon.svg`; run `./art/render-icons.sh` (needs `rsvg-convert`) after changing it.

## Permissions, and why

- **Location (fine)** — Android won't hand over the Wi-Fi name or your location without it.
  Location services need to be switched on, too.
- **Background location ("Allow all the time")** — so reports sent while the app is closed
  can still read the Wi-Fi name and your location. Tap **Allow** next to *Location* and pick
  all-the-time on the screen Android opens.
- **Notifications** — the background service shows one quiet, permanent notification. Android
  requires it, and it doubles as a handy "last report was OK at 14:32" status line.
- **Bluetooth** — to list your paired devices and notice when the earbuds come and go.
- **Battery** — tap **Allow** next to *Battery* to make the app **Unrestricted**, or One UI
  will freeze it once the screen's been off a while. Samsung also keeps its own sleeping-app
  lists: check Settings → Battery → Background usage limits and make sure Device Reporter
  isn't under *Sleeping apps* or *Deep sleeping apps* (adding it to *Never auto sleeping
  apps* is safest).
- **Alarms & reminders** — tap **Allow** next to *On-time heartbeat*. Without it Android
  still sends the heartbeat, but while the phone is idle the gap can stretch to ~10 minutes.

## Getting the APK

There's no Android Studio needed — GitHub builds it for you. Device Reporter lives in the
[doughmination/apps](https://github.com/doughmination/apps) monorepo, and its workflow is
`.github/workflows/device-reporter.yml`.

**Quick test build:** every push to `main` that touches `api-helper/` runs the workflow and
uploads an APK. Open the run under the **Actions** tab and download the
**device-reporter-…** artifact. Good for trying things out.

**Proper signed release:** go to the **Actions** tab → **Device Reporter** → **Run workflow**,
type a version like `v2.1.0` in the box, and run it. GitHub builds a signed APK, creates the
`device-reporter-v2.1.0` tag, and publishes it as a Release. This is the one to actually keep
on your phone. (Pushing a `device-reporter-v2.1.0` tag from your terminal does the same.)

A minute later the repo's **Releases** page has `device-reporter-2.1.0.apk`. Download it
straight onto the phone. Version codes, tags and the signing secrets
(`DEVICE_REPORTER_*`) are covered in [docs/releasing.md](../docs/releasing.md).

Keep the keystore safe and out of git — losing it means you can't ship signed updates that
install over an existing copy.

### Building locally instead

From the repo root:

```bash
./gradlew :api-helper:app:assembleDebug     # api-helper/app/build/outputs/apk/debug/app-debug.apk
./gradlew :api-helper:app:assembleRelease   # falls back to the debug key if no SIGNING_* variables are set
```

## Installing on the phone

Copy the APK over (or download it directly on the phone), open it, and allow installing from
unknown sources if prompted. Launch **Device Reporter**, put in your key and device name,
say yes to the location, notification, and Bluetooth prompts, then tap **Allow** on anything
in the *Background* panel that isn't green. Hit **Start reporting**, then **Send test** to
make sure the server hears it. Reporting comes back on its own after a reboot or an update,
unless you stopped it.

## How it's laid out

```
app/src/main/java/uk/doughmination/devicereporter/
  MainActivity.kt      settings screen, Bluetooth picker, permission prompts
  Prefs.kt             saved configuration
  DeviceState.kt       reads battery / charging / lpm / wifi / location
  Reporter.kt          builds the URL and sends it (with the X-Battery-Key header)
  ReporterService.kt   the always-on service: event triggers + 5-minute alarm
  BootReceiver.kt      brings the service back after a reboot or app update
art/icon.svg                  launcher icon source; render-icons.sh rebuilds the PNGs
```

Gradle and its version catalog are shared with the other apps from the repository root.
