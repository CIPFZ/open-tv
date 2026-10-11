# Open TV Android / Android TV shared layer

This directory contains the first native-client contract layer. It is deliberately
independent of Compose, Media3, and Android framework UI so the same data rules can
be used by the phone, tablet, Android TV, and Google TV clients.

The Kotlin data classes mirror [`schemas/open-tv-config.v1.schema.json`](../../schemas/open-tv-config.v1.schema.json)
and the TV key map mirrors the PC keyboard semantics:

| TV input | Shared action |
| --- | --- |
| D-pad Up / Down | Previous / next channel |
| D-pad Left / Right | Volume down / up |
| Play/Pause | Play or pause |
| Back | Close an overlay, then leave fullscreen |
| Menu | Open the channel list or settings overlay |

The Android Studio module is now present under `app/`. It uses Compose Compiler
plugin `2.3.21`, Compose BOM `2026.08.00`, Media3 `1.11.1`, AGP `9.4.0`, and
`compileSdk 37`. The machine does not ship with a system Android SDK or Gradle
installation; the project has now been verified locally with a temporary JDK 17,
Android SDK 37, and Gradle 9.6 toolchain, and `:app:assembleDebug` also runs on
the Android CI host defined in the repository workflow. The first screen is intentionally a TV-first playback shell
with a focusable bottom control dock, volume percentage, mute state, and the same
D-pad action semantics as the desktop app. The v1 JSON codec/store now persists
playlists, channels, source status, playback preferences, and the last channel;
`M3uParser` and `AndroidPlaylistLoader` mirror the desktop HTTPS/HLS/metadata,
redirect, timeout, and source-health rules. The first channel side panel and centered settings modal
are now connected to startup restore, channel switching, remote source editing, and
the system document picker for local M3U/M3U8 import, plus refresh/enable/disable/delete
actions for saved sources; Media3 buffering/playback/error callbacks are wired into
the TV status line, and Activity stop/start pauses and restores the selected stream.
If the portable configuration includes an XMLTV URL, `AndroidEpgLoader` fetches it
under the same HTTPS, redirect, timeout, and size limits and the shell shows current
and next programmes only when matching data exists.
Settings can export and strictly import the portable v1 JSON configuration;
malformed or unknown-version files are rejected without replacing the current state.
The CI job produces a debug APK and catches Kotlin, Compose,
Media3, resource, and Manifest regressions; lifecycle tests and target-device
validation remain the next milestone.
When no user configuration exists, the Android client uses the same clearly marked
development sample source as the PC client; release builds must review or remove it
according to source authorization and regional availability.

The version choices follow the official [Compose setup guide](https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler),
[Media3 ExoPlayer guide](https://developer.android.com/media/media3/exoplayer/hello-world),
[Android TV Compose guide](https://developer.android.com/training/tv/playback/compose),
and [AGP compatibility table](https://developer.android.com/build/releases/about-agp).
