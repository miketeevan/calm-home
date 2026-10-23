# Calm Home

A lightweight native Android launcher for Fire TV, designed for remote control and a quiet, uncluttered home screen.

## Features

- Six-column app grid with native installed-app artwork and 16:9 tiles.
- Subtle focus enlargement, rounded highlights and smooth grid transitions.
- Long press for **Move** or **Remove from Calm Home**. Removal hides a tile without uninstalling the app; hidden tiles can be restored from Menu.
- Full-screen Top Shelf artwork and film-detail links from supported feeds.
- App-provided favourite stations directly above the dock: 16:9 previews, one-third larger than app icons, on a black background.
- Black background while browsing the lower app grid.
- Native Fire TV Settings and a Fire OS escape tile.
- Optional automatic artwork rotation; bounded caches and background image loading.

## Platform and status

Android API 26+; developed and tested on an Android-based Fire OS 8 Stick. The observed device UI renders at 1920×1080 with 4K HDMI output. This does not target Vega OS.

The latest stability pass passed 117 device checks, with affected checks repeated after final fixes. See [the stability report](STABILITY-PASS.md). Real Sounds Like favourites still require that app to implement the [favourites feed contract](FAVOURITES-INTEGRATION.md).

Installing Calm Home does not replace Amazon's system launcher. The included locally modified Home on Fire helper redirects Home and wake events; a short Amazon Home flash can remain. It requires the user's explicit accessibility enablement. The Fire OS escape tile uses a signature-protected helper activity, so sign both apps with the same local key.

## Build

Requires a JDK, Android SDK **platforms;android-30**, and **build-tools;35.0.0**. The SDK command-line tools provide `sdkmanager` for installing these packages.

```sh
export ANDROID_SDK_ROOT=/path/to/android-sdk
# Set JAVA_HOME if your JDK is not already on PATH.
bash scripts/build.sh
bash scripts/build-home-helper.sh
```

Outputs:

- `build/calmhome.apk`
- `build/home-helper/home-on-fire-reviewed.apk`

The first launcher build creates a local signing key under `private/`. Keep that directory private and retain it for future updates. No signing key or password is committed. Both build scripts use that same key. A newly generated key cannot update an installation signed with a different key.

Install using your own device's ADB connection:

```sh
adb connect YOUR_FIRE_TV_IP:5555
adb -s YOUR_FIRE_TV_IP:5555 install -r build/home-helper/home-on-fire-reviewed.apk
adb -s YOUR_FIRE_TV_IP:5555 install -r build/calmhome.apk
```

Configure the Home helper on the TV and choose Calm Home. Menu in Calm Home opens its configuration. Device discovery, account credentials and system-service modifications are not part of these build scripts.

## Tests

```sh
bash scripts/build-move-tests.sh
adb -s YOUR_FIRE_TV_IP:5555 install -r build/calm-move-tests.apk
adb -s YOUR_FIRE_TV_IP:5555 shell am instrument -w -e check stability uk.michael.calmhome.tests/uk.michael.calmhome.MoveAcceptance
adb -s YOUR_FIRE_TV_IP:5555 uninstall uk.michael.calmhome.tests
```

Other test modes include `visibility`, `move`, `home-options`, `inventory`, `favourites`, `performance`, `polish`, `shelf`, `settings`, and `stock-home`. Run on a dedicated test device: these checks navigate the UI, temporarily rearrange/hide tiles, open Settings and use synthetic favourites. Some checks expect the original development device's installed apps and Frame feed. The temporary test provider is not part of the production APK.

## Layout

- `source/`: launcher source, manifest, font and fallback artwork.
- `home-helper-tuned/`: modified Home on Fire helper and its complete upstream licence/source.
- `tests/`: device instrumentation and synthetic favourites provider.
- `scripts/`: local build scripts.

## Third-party material

Home on Fire is based on [toolicious/home-on-fire](https://github.com/toolicious/home-on-fire), pinned at `f629042674f1faa4b7bce0f54b77a5f849202651`; its GPL licence and upstream notices are retained in `home-helper-tuned/`. Local modifications add faster redirect timing, a wake fallback, animation suppression and the signature-protected Fire OS escape.

Inter is bundled under its [SIL Open Font License](source/assets/OFL.txt). Brand artwork retains its owners' rights; [asset provenance](source/assets/heroes/provenance.json) records the sources. No blanket licence for those images is granted by this repository.

Device backups, private settings, credentials, signing keys and SDK binaries are excluded.
