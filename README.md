# Altair GNSS

Android app that shows what the phone's satellite receiver sees — satellites,
signal levels, the fix and its accuracy — records where you went as a track
in three dimensions, and adds a barometric altimeter that is calibrated once
and then works without any sky, for example to read depth in a cave. A modern
take on the classic GPS test apps. Android 16+ only; the interface speaks ten
languages.

<p>
  <img src="docs/screenshots/status.png" width="150" alt="Status: the fix, the tools and the signal bars coloured by strength">
  <img src="docs/screenshots/record.png" width="150" alt="Activity record: live figures, the Record and Stop buttons and the recordings">
  <img src="docs/screenshots/track.png" width="150" alt="A recording: its track in three dimensions on the map, coloured by speed, and the height chart">
  <img src="docs/screenshots/track-full.png" width="150" alt="The track full screen in the dark theme, with the figures of a tapped point">
  <img src="docs/screenshots/altimeter.png" width="150" alt="Altimeter: an aircraft-style dial with a drum counter">
  <img src="docs/screenshots/instruments.png" width="150" alt="Instruments: compass, speedometer, clocks, the sun and a daylight map">
</p>

The screenshots were taken indoors on a phone without a barometer, before
the receiver had a fix, so the position fields and the altimeter are empty.
The track on them is made up and lies in the Alps.

## Contents

- [Features](#features)
- [Stack](#stack)
- [Project layout](#project-layout)
- [Build](#build)
  - [Local build in Docker](#local-build-in-docker)
  - [Install on a phone](#install-on-a-phone)
- [Download](#download)
- [Design](#design)
- [License](#license)

## Features

Working now:

- **Status** — whether the position is fixed and how many signals take part;
  latitude and longitude (decimal degrees, degrees and minutes, or degrees,
  minutes and seconds), altitude above sea level and above the WGS 84
  ellipsoid, horizontal and vertical accuracy, speed, bearing, PDOP and
  HDOP / VDOP, time to first fix, fix time. Below: a bar per signal coloured
  by its strength from red to green, and a table with the satellite, carrier
  band (L1, L5, E5a, B1I…), C/N0, elevation, azimuth and the almanac /
  ephemeris / used-in-fix flags. Constellations can be hidden; the list is
  sorted by signal, or by ID. GPS, GLONASS, Galileo, BeiDou, QZSS, NavIC and
  SBAS. A *Sky* button next to the fix status opens the sky plot.
- **Position scatter** — fixes collected while the page is open, plotted
  on a target round their mean or round a point you fix (the position now,
  or typed coordinates): how many, how far the latest one is and in which
  direction, the mean distance, the distances half and 95 % of the fixes
  lie within, the farthest, and the spread of height. Round the mean it
  shows how steady the receiver is; round a known point, how accurate.
- **NMEA sentences** — what the receiver reports, as it comes, and a
  recording of every sentence into a text file you save where you like.
- **Sky** — opened from Status: satellites on a polar plot with the zenith
  in the centre. The plot turns with the phone's compass, so its north
  stays over the real one.
  A dot is coloured by signal strength and filled when the satellite is in
  the fix. Counts in view and in the fix, average C/N0.
- **Altimeter** — an aircraft-style dial: the hand goes round once per
  100 m (a figure is tens of metres, a small mark 2 m), a drum counts whole
  metres, and two windows show the current pressure in mbar and mmHg. Below
  it the same in figures — altitude, pressure and sea-level pressure in hPa
  and mmHg — and the vertical speed. The altitude comes from one of three
  sources: GNSS, the barometer alone, or the barometer corrected by GNSS.
  In the barometer mode the receiver is not even started, so the reading
  keeps working where there is no sky — in a cave or a building.
  Calibration takes 20 seconds of GNSS altitude under the open sky,
  weighted by the accuracy each fix claims; or a known altitude is typed
  and the barometer counts from it; or the sea-level pressure is taken
  from the latest weather report of an airport nearby, picked from a list
  with distances — the one way that needs the internet and no sky at all.
  The report's air temperature comes with it and corrects the reading for
  air warmer or colder than the standard atmosphere, which matters more
  the farther above or below the airport you are. After any calibration
  the source switches to the barometer alone. The age of the calibration
  and the error to expect from it are shown: weather moves a barometric
  altitude by about 1.5 m per hour. A phone without a barometer shows the
  GNSS altitude only.
- **Activity record** — a tab with two buttons, *Record* and *Stop*.
  - *Recording.* The altitude is written once a second and, wherever the
    receiver has a fix, the position and the speed. It goes on with the
    screen off, shown by a notification with a stop button. With a GPS
    track wanted, nothing is written until the receiver has held a fix for
    a while (15 seconds, or as set): the button says *Waiting for a fix*
    with an amber dot pulsing, then *Recording* with a red one.
  - *While it runs* the tab shows what is measured now and what the
    recording adds up: the time and the time spent moving, distance,
    speed, the average while moving and the maximum, altitude and vertical
    speed, climb and descent, the lowest and the highest point, and the
    height from the zero, which can be moved to where the phone is now.
    Slower than 3 km/h (or as set) counts as standing, so stops do not
    pull the average down.
  - *Under ground.* Next to the buttons the tab says where the altitude
    comes from and has a *GPS track* switch: off, the recording is the
    altitude alone and the receiver is not kept on.
  - *A finished recording* opens with the track in three dimensions: a
    ribbon standing on the ground and rising to the altitude of every
    point, coloured by the speed there on a scale that runs up to the
    fastest the track was; a stop of half a minute or more carries a pause
    sign. A finger turns and tilts it. Full screen, two fingers zoom and
    move it, and a tap on the line shows the time, the distance, the
    altitude and the speed at that point. Below the track: a chart of
    height over time and the figures.
  - *The map.* With *Map under the track* switched on in Settings (off by
    default) the track stands on OpenStreetMap's map of the place: sharp
    under the track, then more and more blurred as it runs out, and dimmed
    in the dark theme. The tiles are downloaded when a recording is opened
    and kept on the phone; Settings shows how much they take and clears
    them.
  - *Export* to CSV: time, altitude, height from the zero, pressure,
    position and speed. Recordings stay on the phone.
- **Instruments** — a compass and a speedometer side by side: the compass
  points to true north once a position is known (magnetic before that), the
  speedometer's scale grows with the speed (10, 20, 40, 80 km/h and on) and
  carries the course over ground. A tap opens either full screen, with the
  screen kept on: the compass adds the magnetic heading and declination,
  the phone's tilt, and the measured magnetic field against the one
  expected at this place — a way to tell that metal nearby is throwing it
  off; the speedometer adds m/s, acceleration, the maximum, the average
  while moving, distance and moving time of a trip that can be reset. Below: GNSS time in UTC and in the local zone, the phone's own
  clock and its error against GNSS; sunrise, sunset, solar noon and day
  length for the current position — in a polar day or night, the number of
  days until the sun sets or rises again; the phase of the moon. At the
  bottom, a world map with daylight and night as they are now: the night
  side is shaded and lit by cities, the equator and the polar circles are
  drawn across, a sun mark stands where the sun is overhead, a dot where
  you are.
- **Settings** — theme, colour scheme and language; keeping the screen on;
  the tab the app opens on; coordinate format; metres or feet for altitude
  and distance (in feet the altimeter's hand goes round once per 1000 ft
  and the vertical speed reads in ft/min); km/h, mph or knots for speed;
  true or magnetic north; the speed that counts as moving and how long a
  fix has to hold before a recording starts; the map under a track and its
  cache. The position always comes from the satellite receiver alone — no
  Wi-Fi or cell positioning. Assistance data cannot be switched off by an
  app, but it can be refreshed (time and predicted orbits, for a faster
  first fix) or cleared for a cold start.
- **Backup** — *Save a backup* in Settings writes every recording and the
  settings into one file, wherever you choose; with a password the file is
  encrypted (AES-256-GCM, the key derived from the password), without one
  it is a plain zip. *Restore from a backup* adds the recordings of a file
  to the ones on the phone; a recording already there, and the settings,
  are replaced only if you say so.
- **App updates** — a tap on *Version* asks GitHub for the latest release,
  shows what changed and can download the APK and pass it to the system
  installer, which checks the signature and asks for confirmation. A weekly
  background check with a notification is off by default.
- **Network** — used for three things only, each started by you or switched
  on by you: the airport reports (the request names an area a few hundred
  kilometres wide, not your position), the check for a new version, and
  the map under a track — its requests tell OpenStreetMap's tile server
  which tiles, and so where the track is.
- **Location permission** — the satellite tabs explain why the precise
  permission is needed and ask for it; after a refusal they lead to the app's
  system settings. The receiver runs only while one of these tabs is open,
  or a recording with a GPS track is running.
- **Look** — light, dark or system theme, and two colour schemes: the neutral
  classic default with a blue accent and a warm one. Tabs and the pages
  opened from them scroll under a frosted title bar; the frosted tab bar
  slides away while a page scrolls down and returns when it scrolls up.
- **Languages** — English (default), Belarusian, German, Spanish, French,
  Italian, Hebrew (right to left), Polish, Russian and Serbian. Pick one in
  Settings or in the system *Settings → Apps → Altair GNSS → App language*;
  both share one value.

## Stack

| Area | Choice |
|---|---|
| Language / build | Kotlin 2.4, AGP 9.4 (built-in Kotlin), Gradle 9.8, JDK 25 |
| SDK | `minSdk 36` (Android 16), `compileSdk`/`targetSdk 37` |
| UI | Jetpack Compose (BOM 2026.09), Material 3, Navigation 3 |
| Storage | Room 3 (KSP, bundled SQLite) for recordings, DataStore for settings |
| Network | `HttpsURLConnection`, kotlinx.serialization for JSON |

All versions live in [`gradle/libs.versions.toml`](gradle/libs.versions.toml).

## Project layout

```
app/src/main/kotlin/dev/glowcow/altairgnss/
  gnss/         signal model, carrier bands, NMEA and coordinate formats;
                GnssMonitor turns the platform callbacks into one state
  altimeter/    barometric formula, calibration, drift and blending;
                PressureMonitor (the sensor) and Altimeter (the sources)
  instruments/  sunrise equation, the sun's place over the Earth, moon phase,
                compass points, trip statistics;
                CompassMonitor (the rotation sensor)
  recording/    recordings of a track: the Room database, the recorder, the
                foreground service that keeps it going, the figures of a
                track, its layout for the 3D view, stops and CSV
  backup/       the backup archive, its password encryption, save and restore
  maps/         map tiles: their arithmetic, the cache on the phone, the two
                layers of ground put together for a track
  airports/     airport weather reports: parsing, distances, the request
  update/       check for a new release, download, the weekly job
  data/         settings and what the altimeter remembers
  ui/recording/ the Activity record tab, a recording's page, the track in
                three dimensions and the height chart
  ui/tools/     position scatter and the NMEA page, opened from Status
  ui/           theme + icons, shared components (tab page with the frosted
                bars, grouped rows and sheets, stats, the signal colour
                scale, the screen heading, the location permission gate),
                navigation root, one package per screen
app/schemas/    the database schema of each version, for migrations
app/src/test/   unit tests of the plain JVM code in gnss, altimeter,
                instruments, recording, backup, maps, airports and update
docs/           screenshots
```

## Build

The host needs only Docker: JDK, Android SDK and the Gradle distribution live
in the `glowcow/android-sdk` image on Docker Hub (tag =
`<build-tools>-gradle<gradle>`), so a build downloads only the app's own
dependencies. Keep the tag in step with `buildToolsVersion` and the Gradle
wrapper.
The build runs as `linux/amd64` (Google ships `aapt2` for x86-64 Linux only).
On Apple Silicon turn on *Use Rosetta for x86_64/amd64 emulation* in Docker
Desktop and give the VM ~12 GB of memory.

### Local build in Docker

Run Gradle in the toolchain image with the sources mounted and a persistent
dependency cache:

```sh
IMAGE=glowcow/android-sdk:37.0.0-gradle9.8.0
RUN="docker run --rm --platform linux/amd64 -v $PWD:/src -w /src -v altair-gnss-gradle:/root/.gradle/caches -v altair-gnss-keys:/root/.android $IMAGE"

# debug APKs + unit tests
$RUN ./gradlew testDebugUnitTest assembleDebug

# lint + release APKs, unsigned; -PappVersion sets the version
$RUN ./gradlew -PappVersion=0.1.0 lintRelease assembleRelease
```

The APKs land in `app/build/outputs/apk/<type>/`, one per ABI:
`app-arm64-v8a-<type>.apk` for phones and `app-x86_64-<type>.apk` for the
emulator. The `altair-gnss-keys` volume keeps the debug keystore, so a rebuilt
debug APK installs over the previous one.

### Install on a phone

Enable *Developer options → Wireless debugging* on the phone, then use `adb`
from the same image (the volume keeps the adb pairing keys):

```sh
docker run --rm -it --platform linux/amd64 -v altair-gnss-adb:/root/.android -v "$PWD/app/build/outputs/apk":/apk \
  glowcow/android-sdk:37.0.0-gradle9.8.0 sh -c 'adb pair <ip>:<pair-port> && adb connect <ip>:<port> && adb install -r /apk/debug/app-arm64-v8a-debug.apk'
```

Or copy the APK to the phone and open it.

## Download

Signed APKs are attached to every
[release](https://github.com/glowcow/altair-gnss/releases). Phones take
`altair-gnss-<version>-arm64-v8a-release.apk`; the `x86_64` one is for the
emulator. There is no store listing: allow your browser or file manager to
install apps, then open the file. Later versions can be installed from inside
the app: *Settings → Version*.

Releases are signed with the certificate `CN=Anton Sediuk, O=glowcow`,
SHA-256
`7E:63:EC:86:6B:13:A8:23:B4:1B:4F:07:05:75:50:13:8F:B6:65:C3:50:B8:0D:41:CF:CF:B2:19:CB:98:C5:67`.
Check a downloaded file with `apksigner verify --print-certs <file>.apk`.

## Design

The look is shared with [stackd](https://github.com/glowcow/stackd). The
default *classic* scheme is neutral grey — light `#FAFAFA` / dark `#161616`
backgrounds, accent `#1F6FEB`; the *warm* scheme has `#FAF9F5` / `#1A1A18`
and accent `#D97757`. UI font: Arimo. The launcher icon and the splash
screen use the brand palette: navy `#0B1630`, gold `#FFD36B`, ice `#9FB7D9`.

## License

[GNU General Public License v3.0 or later](LICENSE) © Anton Sediuk. A
modified version you distribute has to stay open under the same licence.

Arimo is bundled under the SIL Open Font License 1.1 — see
[`licenses/Arimo-OFL.txt`](licenses/Arimo-OFL.txt).

The world map uses NASA's Blue Marble and Black Marble images (NASA Earth
Observatory), which are in the public domain. Airport reports come from
the Aviation Weather Center's public data API.
