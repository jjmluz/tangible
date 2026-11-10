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
  - Movies: `tangible://movie?tmdb=<TMDB id>&<service key>=<content id>`
- Scanning a tag (or tapping it in the collection) plays it on the player or TV set in Settings.

## How it works today

Everything plays through Home Assistant's REST API with a long-lived token
(`data/HomeAssistantRepository.kt`). The URL and token are in Settings → Home Assistant.

### Music (Music Assistant only)

- Album search uses Discogs; the Spotify Web API (client credentials) only maps a Discogs result
  to a Spotify album ID when writing a tag.
- Playback uses the Music Assistant integration:
  - Players: `POST /api/template` with `integration_entities('music_assistant')`.
  - Play: `music_assistant.play_media` with `media_id: spotify://album/<id>`, `enqueue: replace`.
  - Controls: `media_player.*` services; switching players: `music_assistant.transfer_queue`.
  - State: `GET /api/states/<entity>` (title, artist, artwork, position, volume).
- Every music tag plays on the default player (Settings → Music → Player). There are no per-tag
  outputs, no play buttons and no Edit tag for music: a tap plays and opens Now Playing.
- `NowPlayingController` polls the player every 2 s while the app is visible and sends controls.
- There's no Sonos, Apple Music, YouTube Music, Spotify App Remote or local playback any more.

### Movies (Android TV Remote only)

- Movie search uses TMDB. The Streaming Availability API (region fixed to PT) gives each service's
  link when writing a tag; `MovieService` (`data/MovieTag.kt`) pulls the content ID out of it.
- Services, in preference order: Netflix (`nf`), Disney+ (`dp`), HBO Max (`mx`), Prime Video
  (`pv`), Stremio (`sm`, IMDb ID, always offered). Only subscription availability counts, not
  rent, buy or add-on channels.
- A tag holds one service, picked on the write screen (the most preferred is preselected).
- Playback: `media_player.play_media` with `media_content_type: url` on the TV picked in
  Settings → Movies → Player. TVs are listed with `integration_entities('androidtv_remote')`.
- Links that open on the TV (tested on a Google TV Streamer):
  - Netflix: `https://www.netflix.com/title/<id>?source=30` (without `source=30` it doesn't open the title)
  - Disney+: `https://www.disneyplus.com/browse/entity-<uuid>`
  - HBO Max: `https://play.hbomax.com/movie/<uuid>`
  - Prime Video: `https://app.primevideo.com/detail?gti=amzn1.dv.gti.<uuid>`
  - Stremio: `stremio:///detail/movie/<imdb id>/<imdb id>`
- At the same time the phone opens the Google TV app's remote (`com.google.android.videos`, action
  `com.google.android.apps.googletv.ACTION_VIRTUAL_REMOTE`, an undocumented intent found with
  `dumpsys package`). It's skipped quietly when the app isn't installed.
- Like music, a tap plays: no per-tag outputs, no play buttons, no Edit tag.
- There's no ADB, no network discovery and no playback on the phone any more. Android TV Remote
  doesn't report playback state, so there's no Now Playing for movies.

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
- A scan outside the app is launched by the NFC service in its own task with `CLEAR_TASK`. The
  activity is `singleTask` so the one instance (and its `NowPlayingController`) gets the scan as
  `onNewIntent`; with `singleTop` each scan made a new activity that knew nothing about what was
  playing.
- A phone held on a tag reads it again about a second later. `ScanDebouncer` ignores the repeat;
  without it the album was requested twice and restarted a few seconds in.
- `music_assistant.play_media` takes about 5 s to answer on the Sonos; the album starts right after.
- On Android 15+, the per-app "launch via NFC" setting can block Tangible. A blocked scan opens the
  launcher activity without the tag data. The app warns about it (`NfcAdapter.isTagIntentAllowed`).

## Known issues

- The Home Assistant token is stored in plain SharedPreferences.
- Old settings from removed features (Sonos, Qobuz, selected service, Chromecast, region, movie
  providers) are left in SharedPreferences and are unused.
- `MainNavigationTest` (instrumented) is out of date and isn't run.

## Secrets

API keys (Discogs, TMDB, Streaming Availability, Spotify client ID/secret) live in
`local.properties` (git-ignored) and reach the code through `BuildConfig` (see
`app/build.gradle.kts`). Never hard-code keys or tokens in tracked files.

## Development

- Work iteratively and don't try to do everything at once. I'll guide what we work on next.
- Adhere to current Android design guidelines. The app should be pretty and fun to use.
- Make it easy for me to test your changes: build, run the unit tests, and install on my phone
  (Pixel 8 Pro, wireless adb).
- Gradle needs JDK 17+: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`.
- Unit tests use JUnit, Robolectric, Compose UI tests, MockWebServer and MockK. In coroutine tests,
  launch on the test scope, not `backgroundScope` (`advanceUntilIdle` doesn't run background work).
