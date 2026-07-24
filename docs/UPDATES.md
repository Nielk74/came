# App updates and releases

came checks its own public GitHub Releases feed, using the repository compiled into
`BuildConfig.GITHUB_REPO`. Automatic checks are persisted and throttled to once every 24 hours;
the manual check API always bypasses that throttle.

## App integration

`AppUpdateViewModel` is the lifecycle-aware entry point. It starts the throttled automatic check
when created and exposes:

- `updateStatus: StateFlow<UpdateStatus>` for idle, checking, up-to-date, available, and failure UI.
- `downloadState: StateFlow<DownloadState>` for download progress, verification, installer handoff,
  and errors.
- `checkForUpdates()` for an unthrottled user-requested check.
- `downloadAndInstall()` when `updateStatus` is `UpdateStatus.Available`.
- `dismissUpdate()` to hide an available update for the current process.

The release checker requests
`/repos/{BuildConfig.GITHUB_REPO}/releases?per_page=100&page=N`, follows GitHub's `Link` header while
it contains `rel="next"`, ignores drafts, prereleases, invalid semantic versions, and releases with
no downloadable APK, then selects the highest installable semantic version across every page.

The APK's checksum is optional. When a release includes an asset whose name is exactly the APK
filename plus `.sha256`, the download is rejected unless its SHA-256 matches. The downloader uses
GitHub API/media headers and hands a verified cache file to Android's package installer through the
app's FileProvider; installation always requires Android and user approval.

## Publishing a release

The `Release` workflow runs for `v*` tags. Configure these Actions secrets before tagging:

- `KEYSTORE_B64`: base64-encoded Android signing keystore.
- `STORE_PASSWORD`: keystore password.
- `KEY_ALIAS`: release key alias.
- `KEY_PASSWORD`: release key password.

For a tag such as `v1.2.3`, the workflow validates/tests/lints the app, derives version `1.2.3`,
builds signed APK and AAB artifacts, verifies the APK signature, generates the exact companion
`*.apk.sha256` asset, and publishes all three artifacts to a GitHub Release. It passes
`${{ github.repository }}` into `BuildConfig.GITHUB_REPO`, so forks update from their own releases.
