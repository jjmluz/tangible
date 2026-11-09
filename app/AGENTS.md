# Tangible: NFC Orchestrator

## Intro

Tangible is an NFC tag media orchestrator. It preserves legacy physical media (vinyl, CDs, DVDs)
while bringing it to modern digital convenience: scan the tag on a record and the album plays on
your speakers.

It's a personal project. It won't be published on the Play Store, so backwards compatibility
(old tag formats, old settings) isn't needed.

## Main use case

- Users stick NFC tags to their physical media.
- They use the app to write what that media is to the tag:
  - Music: `tangible://play?spotify_album=<spotify album id>`
  - Movies: `tangible://movie?tmdb_id=<id>&r=<region>&<service key>=<type>:<id>…`
- Scanning a tag (or tapping it in the collection) plays it on the default output set in Settings.

## How it works today

### Music (Music Assistant only)

- Album search uses Discogs; the Spotify Web API (client credentials) only maps a Discogs result
  to a Spotify album ID when writing a tag.
- Playback goes through Home Assistant's REST API with a long-lived token
  (`data/HomeAssistantRepository.kt`):
  - Players: `POST /api/template` with `integration_entities('music_assistant')`.
  - Play: `music_assistant.play_media` with `media_id: spotify://album/<id>`, `enqueue: replace`.
  - Controls: `media_player.*` services; switching players: `music_assistant.transfer_queue`.
  - State: `GET /api/states/<entity>` (title, artist, artwork, position, volume).
- Every music tag plays on the default player (Settings → Music Assistant). There are no per-tag
  outputs, no play buttons and no Edit tag for music: a tap plays and opens Now Playing.
- `NowPlayingController` polls the player every 2 s while the app is visible and sends controls.
- There's no Sonos, Apple Music, YouTube Music, Spotify App Remote or local playback any more.

### Movies (next to refactor)

- TMDB + Streaming Availability build the tag; playback is on this device or a Google TV
  (`ChromecastDiscovery`, launched over ADB with `AdbRepository`).
- Movie tags still have Edit tag (per-tag service/output) and a play button.

## Music Assistant / Sonos quirks (learned the hard way)

- HA accepts a command before the speaker acts on it (resuming a Sonos takes a second or two).
  Controls show their result immediately and hold it until the player reports the same thing
  (see `Expectation` in `NowPlayingController`), otherwise the UI flickers.
- A paused Sonos is reported as **`idle`**, not `paused`. Idle with a track loaded counts as paused.
- About a second after pausing, MA reports the position where playback last *started*, with a
  fresh timestamp. Tangible holds the stop position itself (`StoppedAt`) and resumes with
  seek + play from there; MA's own resume goes back a second or two.
- `media_position` is in whole seconds and only refreshed now and then; add the time since
  `media_position_updated_at` while playing.
- On Android 15+, the per-app "launch via NFC" setting can block Tangible. A blocked scan opens the
  launcher activity without the tag data. The app warns about it (`NfcAdapter.isTagIntentAllowed`).

## Known issues

- API keys and secrets (Discogs, TMDB, Streaming Availability, Spotify client ID/secret) are
  hard-coded in `MainActivity.kt`. Move them to `local.properties` / `BuildConfig` and rotate them.
- The Home Assistant token is stored in plain SharedPreferences.
- Old settings from removed features (Sonos, Qobuz, selected service) are left in
  SharedPreferences and are unused.

## Development

- Work iteratively and don't try to do everything at once. I'll guide what we work on next.
- Adhere to current Android design guidelines. The app should be pretty and fun to use.
- Make it easy for me to test your changes: build, run the unit tests, and install on my phone
  (Pixel 8 Pro, wireless adb).
- Gradle needs JDK 17+: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`.
- Unit tests use JUnit, Robolectric, Compose UI tests, MockWebServer and MockK. In coroutine tests,
  launch on the test scope, not `backgroundScope` (`advanceUntilIdle` doesn't run background work).
