# Official APK test fixture

Source: https://github.com/pengchujin/aerial-tv/releases/tag/v1.73
Metadata: official GitHub releases API, snapshot retained as aerial-release.json.
APK: aerial-standard.apk, package app.orchard.tv, versionName 1.73, versionCode 93, minSdk 21.
SHA-256: 21be2cef113af3c1f55c908116c12e310a57bfbdd32ebf3de58b3203691a3a14

The unmodified upstream APK was used locally as an optional QA fixture. It is NOT inside LinkDeck's installable APK or the source bundle. To reproduce these optional native tests, download the exact standard asset from the pinned release above (or the official asset API URL in aerial-release.json with Accept: application/octet-stream), verify the SHA-256 above, and place it in this directory. Upstream license: https://github.com/pengchujin/aerial-tv/blob/main/LICENSE . Test screenshots with a “测试电视（预览，未连接）” model are compatibility previews, not a connection to the user's television.
