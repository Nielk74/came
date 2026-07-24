# camé

An intentionally quiet Android camera. The viewfinder is the interface.

When camé opens, there is no toolbar, mode dial, or shutter button over the image. Tap the
viewfinder to take a photograph. Swipe vertically to move through film looks; the current stock
and a rotating settings gear appear briefly, then get out of the way again.

<p align="center">
  <img src="docs/screenshots/viewfinder.png" width="300" alt="camé control-free viewfinder" />
  &nbsp;&nbsp;
  <img src="docs/screenshots/menu.png" width="300" alt="camé full-screen camera menu" />
</p>

## Interaction

- **Tap anywhere** — take a photograph, immediately or after the configured delay.
- **Swipe up / down** — move through enabled film looks.
- **Tap the transient gear** — open the full-screen camera menu.
- **Volume key** — take a photograph without touching the screen.

The menu uses a high-contrast, Fujifilm-inspired information hierarchy. It controls film grain,
which looks are in the swipe rotation, the self-timer, and app updates.

## Film looks

The profiles are adapted from the scene-aware film work in
[`Nielk74/ricoh-gr3-android`](https://github.com/Nielk74/ricoh-gr3-android). camé keeps their stock
identity while using a lightweight preview transform for immediate feedback and a deterministic
full-resolution renderer for the saved photograph. Grain varies with tone, not neighbouring
detail, so focus and subject texture do not change its physical character.

Included at launch: Portra 400, Portra 800, Gold 200, Ektar 100, Superia 400, CineStill 800T,
Vision3 250D, Vision3 500T, Eterna Cinema, Tri-X 400, and HP5 Plus.

## Privacy

camé has no account, ads, analytics, or network upload. Photographs stay in Android's media
library. Network access is used only to check this repository's public GitHub Releases feed and
download an update after you accept it.

## Build

Requirements: JDK 17 and an Android SDK containing API 34.

```sh
./gradlew test lint assembleDebug
```

The debug APK is written under `app/build/outputs/apk/debug/`.

## Releases and in-app updates

Tags matching `v*` run the release workflow. CI tests and lints the source, builds a signed APK
and AAB, verifies the APK signature, produces a SHA-256 checksum, and publishes all three assets
to GitHub Releases. The app discovers installable releases across the paginated public feed,
verifies the checksum when present, and hands the APK to Android's normal installer confirmation.

Repository maintainers must configure `KEYSTORE_B64`, `STORE_PASSWORD`, `KEY_ALIAS`, and
`KEY_PASSWORD` as GitHub Actions secrets before pushing a release tag.

## License

[MIT](LICENSE)
