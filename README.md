# Tangible

Physical media → NFC → digital media.

Stick an NFC tag on a vinyl, CD or DVD, write it with Tangible, and scanning it later plays that
album or movie on your speakers or TV. It's a personal Android app (Kotlin, Jetpack Compose) and
isn't published on the Play Store.

## How it works

### Music: Music Assistant

- **Writing a tag:** search for the album (through Music Assistant, so on Spotify and in its
  library) and Tangible writes `tangible://play?spotify_album=<id>` to the tag.
- **Playing:** scanning the tag, or tapping it in the collection, asks
  [Music Assistant](https://music-assistant.io) to play the album on your default player, through
  Home Assistant's `music_assistant.play_media` action. To play on several speakers, create a
  player group in Music Assistant and pick it as the default player.
- **Now Playing:** opens after you start an album. It shows the cover, title and progress, and has
  play/pause, previous/next, seek, volume, and a player switcher that moves the queue to another
  player. A mini player sits above the collection while something is loaded.
- **Media controls:** while an album plays, Android's media controls (quick settings and the lock
  screen) show the track with play/pause, previous/next and seek, and they control the speaker.
  While they're showing, the phone's volume keys set the speaker's volume.
- **Default volume** (optional): the player is set to this volume right before an album starts.

### Movies: streaming apps on a Google TV, through Home Assistant

- **Writing a tag:** search for the movie (Streaming Availability API). Tangible checks where it's
  included in a subscription in Portugal and offers Netflix, Disney+, HBO Max and
  Prime Video, plus Stremio for anything with an IMDb ID. The most preferred one is preselected,
  in that order, and you can pick another. The tag holds that one service:
  `tangible://movie?tmdb=<id>&<service>=<content id>`.
- **Playing:** scanning the tag, or tapping it in the collection, asks Home Assistant to open the
  movie's link on your TV with the
  [Android TV Remote](https://www.home-assistant.io/integrations/androidtv_remote/) integration
  (`media_player.play_media`). The streaming app comes to the front on that movie. On the phone,
  the Google TV app's remote opens at the same time, if the app is installed.

## Setup

1. **Home Assistant:**
   - Music: Music Assistant installed, with your Spotify account added in Music Assistant.
   - Movies: the Android TV Remote integration set up for your Google TV.
2. **In Tangible**, open Settings:
   - Home Assistant → Connection: your Home Assistant address, e.g.
     `http://homeassistant.local:8123`, and a long-lived access token (create one in Home Assistant
     under Profile → Security).
   - Music → Player: pick a player (or a player group), and optionally a default volume.
   - Movies → Player: pick your TV.
3. **Android 15+:** if Tangible shows "NFC tags can't open Tangible", tap **Allow**. Android lets
   you block apps from opening on an NFC scan, and a blocked scan only brings the app to the front
   without playing.

Tags in older formats don't work any more: music tags from before the Music Assistant change, and
movie tags from before the Home Assistant change. Write them again.

## Building and installing

Needs JDK 17+ (Android Studio's bundled JBR works).

The one API key isn't in git. Add it to `local.properties` in the project root (git ignores it), or
set it as an environment variable with the same name:

```properties
STREAMING_AVAILABILITY_API_KEY=...
```

It's compiled into `BuildConfig`. Without it the app builds fine, but movie search finds nothing.

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Over wireless debugging, if adb doesn't find the phone, look it up and connect directly:

```bash
dns-sd -B _adb-tls-connect._tcp local.
adb connect <phone-ip>:<port>
```
