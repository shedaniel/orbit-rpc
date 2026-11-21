<img src="assets/icon.svg" width="72" height="72" alt="">

# Orbit

Discord Rich Presence for Android. Orbit shows what you're listening to, watching and playing on your Discord profile, built with Jetpack Compose and Material 3 Expressive (Monet colours included).

- **Media**: any app with a media session (Spotify, YouTube Music, YouTube, Netflix, podcast players and so on). It tells music, video and podcasts apart, so you get *Listening to* or *Watching* accordingly, and you can override this per app.
- **Games and apps**: shows *Playing X* with the app's icon while a game (or any app you pick) is open.
- **Spotify Connect**: optionally shows Spotify playback happening on other devices (speakers, PC, TV).
- **Rules**: everything above is a rule you can reorder, edit or add to. A rule sets the trigger, the activity type, each text line (with `{title}`, `{artist}`, `{album}`, `{app}` and `{device}` placeholders, and `[optional sections]`), the images, timestamps, buttons and what the member list shows. A live preview updates as you edit.
- **Data usage**: see below.

## Install

Use [Obtainium](https://github.com/ImranR98/Obtainium): add `https://github.com/shedaniel/orbit-rpc` as a GitHub source. Releases are signed with the same key every time. Its SHA-256 certificate fingerprint is:

```
D4:43:A6:7D:FE:CA:06:C9:04:7C:34:DA:34:18:DA:46:93:0E:69:49:CB:29:EA:F4:48:47:C6:FC:7F:77:6C:15
```

You can also download the APK from [Releases](https://github.com/shedaniel/orbit-rpc/releases) directly.

### Permissions

| Permission | Why |
| --- | --- |
| Notification access | Lets Orbit read media sessions (what's playing). Notification contents are never read. |
| Usage access | Lets Orbit see which game or app is in the foreground. Optional. |
| Notifications | Shows the persistent "what's shared" notification. |
| Ignore battery optimisations | Stops Android from killing the service. Optional. |

On Android 13+ a sideloaded app may have notification access greyed out. Open *App info → ⋮ → Allow restricted settings* first.

## Data usage

Earlier Android RPC apps keep a full Discord gateway session open all day, receiving every message, typing indicator and presence update from every server you're in. Orbit is built to avoid that:

- **No connection when there's nothing to show.** The gateway connects when an activity appears and disconnects after an idle period (90 s by default).
- **Transport compression** (`zlib-stream`) on the gateway.
- **Lean identify.** It asks Discord for no extra events, and falls back automatically if that's rejected.
- **Resume, don't re-identify.** Reconnects resume the previous session instead of downloading the large READY payload again.
- **No duplicate or rapid updates.** Identical presences are never re-sent, position-only updates are ignored, and bursts are coalesced and rate-limited.
- **Everything is cached.** Album art, app icons and uploaded images each cost at most one lookup ever.
- **Separate Wi-Fi and mobile-data profiles.** Each one decides whether to share at all, and whether to look up album art, upload artwork, resolve app icons, poll Spotify Connect, or stay connected while idle. Orbit can pause while roaming and respects Android's Data Saver.
- **Metering.** Every request is counted by category and network. The *Data* tab shows 14 days of history and compares Orbit's own count with what Android counted for the app.

## Spotify Connect (optional)

1. Create an app at [developer.spotify.com/dashboard](https://developer.spotify.com/dashboard).
2. Add the redirect URI `orbit-rpc://spotify-callback` and enable the Web API.
3. In development mode, add your own Spotify account under *User Management*.
4. Paste the client ID into *Settings → Spotify Connect* and sign in.

## Notes

- Orbit sets your status through your own account, like a second Discord client. Your token is stored encrypted with the Android Keystore and is only ever sent to Discord. Automating a user account is against Discord's Terms of Service, so use Orbit at your own risk.
- Activities use a widely used public application ID by default. You can set your own (create an application in the Discord developer portal) in *Settings → Discord → Application ID*.

## Building

```
./gradlew :app:assembleDebug
```

Pushing a `v*` tag builds a signed release APK and publishes it to GitHub Releases. The version name comes from the tag and the version code from the commit count. Signing uses the `ORBIT_*` repository secrets, or a local `keystore.properties`.

Google Sans Flex is used under the SIL Open Font License (see `licenses/`).
