# Instagram Downloader — Android App: Full Build Spec & Task List

> Jetpack Compose, single-module, MVVM + Clean Architecture. Two screens (Home, Downloads).
> Production-ready: key protection, SSL pinning, resumable downloads, scoped storage.

---

## 1. Architecture

**Pattern:** MVVM + Clean Architecture (Presentation → Domain → Data), single Gradle module,
package-by-layer. This is the "standard architecture" Google recommends for
[Now in Android](https://github.com/android/nowinandroid)-style apps, right-sized for a 2-screen app.

```
com.pranavkd.igdownloader
├── app/                      # Application class, MainActivity, Navigation host
├── di/                       # Hilt modules
├── presentation/
│   ├── home/                 # HomeScreen, HomeViewModel, HomeUiState
│   ├── downloads/            # DownloadsScreen, DownloadsViewModel
│   ├── components/           # Shared composables (ProgressBar, ThumbnailCard, etc.)
│   ├── theme/                # Color.kt, Type.kt, Theme.kt
│   └── navigation/           # NavGraph, Screen sealed class
├── domain/
│   ├── model/                # MediaInfo, DownloadItem, DownloadStatus (pure Kotlin, no Android deps)
│   ├── repository/           # MediaRepository, DownloadRepository interfaces
│   └── usecase/              # FetchMediaInfoUseCase, StartDownloadUseCase, PauseDownloadUseCase,
│                              # ResumeDownloadUseCase, DeleteDownloadUseCase, ObserveDownloadsUseCase
└── data/
    ├── remote/
    │   ├── api/               # InstagramApiService (Retrofit interface)
    │   ├── dto/                # Raw API response DTOs
    │   └── interceptor/        # AuthHeaderInterceptor (injects key server-side or from proxy)
    ├── local/
    │   ├── db/                 # AppDatabase (Room)
    │   ├── dao/                # DownloadDao
    │   └── entity/              # DownloadEntity
    ├── download/
    │   ├── DownloadService.kt    # Foreground Service — owns all active download jobs
    │   ├── DownloadEngine.kt     # OkHttp Range-request based downloader
    │   └── DownloadNotifier.kt   # Notification channel + per-item progress notification
    ├── repository/               # MediaRepositoryImpl, DownloadRepositoryImpl
    └── mapper/                    # DTO ↔ Domain ↔ Entity mappers
```

**Key libraries:** Jetpack Compose (Material 3), Hilt, Retrofit + OkHttp, kotlinx.serialization or Moshi,
Room, WorkManager (not for the download itself — see §5 — but useful for cleanup/retry jobs),
Coil (thumbnails), kotlinx.coroutines + Flow, DataStore (lightweight settings, if needed).

---

## 2. ⚠️ Security Foundation — Read Before Writing Any Code

Your RapidAPI key `x-rapidapi-key` is a **paid, billable secret**. Anything shipped inside an
APK — a string resource, `BuildConfig` field, or even a native `.so` — **can be extracted** by
anyone with the APK and enough patience (decompile, Frida hook, memory dump, MITM proxy). There
is no client-side trick that makes a key shipped to millions of devices un-extractable. Client-side
"protection" only raises the cost of extraction; it does not eliminate it.

**Recommended approach for a production app (do this):**

Build a thin backend proxy that holds the RapidAPI key server-side, and have the Android app call
*your own* domain only. The app never sees the RapidAPI key at all.

- You already operate a Cloudflare Workers proxy for Instagram reel downloading — reuse or extend
  that Worker as this proxy, or stand up a new lightweight Worker/Cloudflare Function.
- Proxy contract: `POST https://api.yourdomain.dev/v1/resolve` with `{ "url": "<instagram_link>" }`
  in the body → Worker attaches `x-rapidapi-key` server-side, calls RapidAPI, returns the JSON
  to the app.
- Add your own lightweight auth on the Worker (e.g. a per-install anonymous token issued on first
  launch, or a static app-secret header + rate limiting by IP/device) so the proxy itself isn't an
  open relay that burns your RapidAPI quota.
- Add server-side rate limiting (Cloudflare Workers KV or Durable Objects) per device/IP to protect
  your RapidAPI billing from abuse.
- This is the path the rest of this document assumes (`InstagramApiService` below points at your
  proxy, not directly at `instagram-all-in-one-downloader.p.rapidapi.com`).

**Fallback only if you truly must call RapidAPI directly from the device** (not recommended for
production — documented here so the trade-off is explicit, not because it's advised):

- Never put the key in `strings.xml`, `local.properties → BuildConfig`, or any Kotlin string
  literal — these are trivially grep-able after `apktool`/`jadx` decompilation.
- Store obfuscated key fragments in a native library (NDK/C++, reconstructed via JNI at runtime)
  to defeat casual static `strings` scanning — this does **not** defeat dynamic extraction
  (Frida/Xposed hooking the JNI call, or a rooted-device MITM).
- Enable R8 full mode + resource shrinking; treat it as a minor speed bump, not real protection.
- Set a hard monthly quota cap and usage alerts in the RapidAPI dashboard so a leaked key has a
  bounded blast radius.
- SSL pinning (§8) still matters here even without a proxy — it stops a MITM tool from reading the
  key off the wire in transit, though it doesn't stop static/dynamic extraction from the APK itself.

---

## 3. Data Model

### 3.1 Domain model — confirmed against real API response

⚠️ **Critical detail the confirmed response reveals:** for a reel, `medias` contains **two
separate tracks** — one `type: "video"` entry (video-only, no audio) and one `type: "audio"`
entry. Downloading just the video track alone produces a **silent file**. The app must download
both tracks and **mux them into one playable file** (see Phase 5.8). Image posts are expected to
return a single `type: "image"` entry with no audio counterpart.

```kotlin
data class MediaInfo(
    val sourceUrl: String,
    val author: String?,
    val title: String?,             // caption text
    val thumbnailUrl: String?,
    val likeCount: Long?,
    val viewCount: Long?,
    val tracks: List<MediaTrack>    // e.g. [video, audio] for a reel, [image] for a photo post
)

data class MediaTrack(
    val id: String,
    val type: MediaTrackType,       // VIDEO, AUDIO, IMAGE
    val quality: String?,           // "716x1274p" for video, "65kbps" for audio
    val resolution: String?,        // "716x1274", null for audio
    val extension: String,          // "mp4" | "m4a" | "jpg"
    val downloadUrl: String
)

enum class MediaTrackType { VIDEO, AUDIO, IMAGE }
```

### 3.2 Room entities

Two tables, because a single user-facing download (one reel) can be made of multiple underlying
network transfers (video track + audio track) that need to be individually pausable/resumable
*and* combined into one final file. `DownloadGroupEntity` is what the Downloads screen renders
one row per; `DownloadTrackEntity` is what `DownloadEngine` actually pulls bytes for.

```kotlin
@Entity(tableName = "download_groups")
data class DownloadGroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceUrl: String,
    val title: String?,             // caption, shown as the row's label
    val thumbnailUrl: String?,
    val outputFileName: String,
    val outputMimeType: String,     // "video/mp4" | "image/jpeg"
    val requiresMux: Boolean,       // true when there's a separate video + audio track
    val status: String,             // DownloadStatus.name — derived from child tracks, see 5.8
    val mediaStoreUri: String?,     // set once the final (possibly muxed) file is finalized
    val errorMessage: String? = null,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "download_tracks",
    foreignKeys = [ForeignKey(
        entity = DownloadGroupEntity::class,
        parentColumns = ["id"], childColumns = ["groupId"], onDelete = ForeignKey.CASCADE
    )]
)
data class DownloadTrackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val groupId: Long,
    val trackType: String,          // MediaTrackType.name
    val remoteUrl: String,
    val stagingFilePath: String,    // private app-storage path used during transfer
    val totalBytes: Long = -1,      // -1 until server reports Content-Length
    val downloadedBytes: Long = 0,
    val status: String              // DownloadStatus.name, per-track
)

enum class DownloadStatus { QUEUED, DOWNLOADING, PAUSED, MUXING, COMPLETED, FAILED, CANCELLED }
```

`DownloadItem` (domain model shown on screen) is a mapped, flattened view of a group plus its
child tracks' summed `downloadedBytes`/`totalBytes` for a single combined progress bar.

### 3.3 API DTO — confirmed against a real response

Verified against a live call to `instagram-all-in-one-downloader`'s `/download` endpoint for a
reel URL. Field names below are exact.

```kotlin
@Serializable
data class MediaResolveResponseDto(
    val status: Boolean,
    val author: String? = null,
    @SerialName("view_count") val viewCount: Long? = null,
    @SerialName("like_count") val likeCount: Long? = null,
    val title: String? = null,
    val thumbnail: String? = null,
    val medias: List<MediaAssetDto> = emptyList(),
    val error: String? = null       // populated (status=false) on failure — see Task 10.1
)

@Serializable
data class MediaAssetDto(
    val id: String,
    val type: String,               // "video" | "audio" | "image" (observed so far)
    val quality: String? = null,    // "716x1274p" for video, "65kbps" for audio
    val resolution: String? = null, // "716x1274"; null for audio tracks
    val extension: String,          // "mp4" | "m4a" | "jpg"
    val url: String
)
```

Sample confirmed response (reel, video + separate audio track):
```json
{
  "status": true,
  "author": "...",
  "view_count": null,
  "like_count": 24,
  "title": "...",
  "thumbnail": "https://instagram.<cdn>/.../thumb.jpg?...",
  "medias": [
    { "id": "...", "type": "video", "quality": "716x1274p", "resolution": "716x1274", "extension": "mp4", "url": "https://instagram.<cdn>/.../video.mp4?..." },
    { "id": "...", "type": "audio", "quality": "65kbps", "resolution": null, "extension": "m4a", "url": "https://instagram.<cdn>/.../audio.mp4?..." }
  ],
  "error": null
}
```

Notes worth designing around:
- `view_count` is `null` for this reel — treat all count fields as nullable/optional in the UI.
- `status: false` responses carry a human-readable `error` string and empty/absent `medias` —
  surface `error` directly rather than a generic failure message (Task 10.1).
- Media CDN URLs (`instagram.*.fbcdn.net`) are **signed and time-limited** (`oe=` query param is
  an expiry timestamp) — the app must start the download promptly after resolving, and a `resume`
  attempted long after the initial fetch may need to re-resolve the link rather than reuse a stale
  URL. Add expiry handling to Task 4.4/5.1 (detect a 403/expired-signature response mid-download,
  re-call `resolve`, and restart the affected track from the new URL rather than failing outright).
- Image posts are assumed (not yet confirmed) to return a single `type: "image"` entry — validate
  this with a real image-post URL during Task 4.1 before writing the "no mux needed" branch.

---

## 4. Task List

### Phase 0 — Project Setup
- [ ] 0.1 Create new Android Studio project, min SDK 26 (Android 8, needed for scoped
  notification channels + reasonable Compose baseline), target/compile SDK latest stable.
- [ ] 0.2 Add Compose BOM, enable Compose in `build.gradle`.
- [ ] 0.3 Add dependencies: Hilt, Retrofit, OkHttp (+ logging-interceptor for debug builds only),
  kotlinx.serialization, Room + KSP, Coil-Compose, Navigation-Compose, kotlinx.coroutines.
- [ ] 0.4 Set up `debug`/`release` build variants; confirm no secrets in either `build.gradle` or
  `local.properties` are referenced via `BuildConfig` (per §2, the app talks to your own proxy,
  which needs no client secret beyond a non-sensitive app identifier if you choose to add one).
- [ ] 0.5 Set up Git repo, `.gitignore` (exclude `local.properties`, keystores, `google-services.json`
  if used), README.

### Phase 1 — Backend Proxy (do this before any Android networking code)
- [ ] 1.1 Stand up / extend the Cloudflare Worker: `POST /v1/resolve { url }` → calls RapidAPI
  `GET /download?url=<encoded>` with the three required headers, returns the raw JSON body.
- [ ] 1.2 Store the RapidAPI key as a Worker **secret** (`wrangler secret put RAPIDAPI_KEY`),
  never in Worker source.
- [ ] 1.3 Add basic abuse protection: per-IP or per-device rate limit (Workers KV counter or
  Durable Object), reject if exceeded.
- [ ] 1.4 Add CORS/host checks if relevant; add a simple app-identifier header check (not a real
  secret — just enough to stop drive-by scraping of your proxy URL).
- [ ] 1.5 Deploy, confirm the Worker's TLS certificate (needed for SSL pinning in §8 — you'll pin
  *this* domain, not RapidAPI's).
- [ ] 1.6 Manually curl the Worker end-to-end with a real Instagram reel URL, save the raw JSON
  response — this becomes the input for Task 4.1.

### Phase 2 — Core Infrastructure (DI, Networking, Database)
- [ ] 2.1 `NetworkModule` (Hilt): OkHttpClient with timeouts, `CertificatePinner` (stub for now,
  filled in Phase 8), Retrofit instance pointed at your proxy base URL.
- [ ] 2.2 `DatabaseModule`: Room `AppDatabase`, `DownloadDao` with `Flow`-returning queries
  (`getAll()`, `getById()`, insert/update/delete).
- [ ] 2.3 `AppModule`: provide `CoroutineDispatchers` wrapper (IO/Default/Main) for testability.
- [ ] 2.4 Set up `Application` class with `@HiltAndroidApp`.

### Phase 3 — Domain Layer
- [ ] 3.1 Define `MediaInfo`, `MediaTrack`, `MediaTrackType`, `DownloadItem`, `DownloadStatus` (pure
  Kotlin, zero Android/Retrofit/Room imports).
- [ ] 3.2 Define repository interfaces: `MediaRepository.resolve(url: String): Result<MediaInfo>`,
  `DownloadRepository` with `observeAll(): Flow<List<DownloadItem>>`, `enqueue()`, `pause()`,
  `resume()`, `delete()`, `retry()`.
- [ ] 3.3 Use cases: `FetchMediaInfoUseCase`, `StartDownloadUseCase`, `PauseDownloadUseCase`,
  `ResumeDownloadUseCase`, `DeleteDownloadUseCase`, `ObserveDownloadsUseCase`. Each is a
  single-purpose `operator fun invoke(...)` class.
- [ ] 3.4 Basic input validation: `IsInstagramUrlUseCase` — regex/host check that the pasted link
  is an `instagram.com` post/reel/story URL before calling the network.
- [ ] 3.5 `StartDownloadUseCase` must decide `requiresMux` from the resolved `MediaInfo.tracks`:
  `true` when both a `VIDEO` and an `AUDIO` track are present, `false` for a lone `IMAGE`
  (or a lone `VIDEO`, if that ever occurs) — this flag drives Phase 5.8.

### Phase 4 — Data Layer
- [ ] 4.1 Implement `MediaResolveResponseDto`/`MediaAssetDto` exactly per the confirmed schema in
  §3.3. Still worth one live call against an **image post** URL to confirm the `type: "image"`
  assumption before relying on it in the mux-decision logic.
- [ ] 4.2 `InstagramApiService` Retrofit interface: `@POST("v1/resolve")` (or `GET` if you kept the
  proxy's shape as GET passthrough) → returns `MediaResolveResponseDto`. If `status == false`,
  map to a failure using the DTO's `error` string rather than throwing a generic exception.
- [ ] 4.3 Mappers: `MediaAssetDto.type` string → `MediaTrackType` enum (unknown/unexpected type
  values should map to a safe fallback + logged warning, not a crash); DTO → `MediaInfo`
  (domain); on download start, `MediaInfo` → one `DownloadGroupEntity` + one
  `DownloadTrackEntity` per track.
- [ ] 4.4 `MediaRepositoryImpl`: calls API service, maps errors (no internet, 4xx from proxy,
  `status:false` with `error` message from the DTO, RapidAPI quota exceeded surfaced by your
  proxy as a specific status code) into typed domain errors (`sealed class MediaError`).
- [ ] 4.5 `DownloadRepositoryImpl`: wraps `DownloadDao` (groups + tracks), exposes
  `Flow<List<DownloadItem>>` mapped from group+child-track joins (summed progress, derived
  status); delegates actual byte transfer to `DownloadEngine`/`DownloadService` (Phase 5).

### Phase 5 — Download Engine (pause/resume/delete, mux, foreground service)
- [ ] 5.1 `DownloadEngine`: OkHttp-based, operates **per track** (`DownloadTrackEntity`), supports
  HTTP `Range: bytes=<offset>-` requests so a paused/interrupted track can resume from
  `downloadedBytes` instead of restarting.
- [ ] 5.2 Each track writes to its own private staging file (app-specific external files dir,
  e.g. `<groupId>_video.mp4`, `<groupId>_audio.m4a`); nothing is written to public storage
  until the group finishes (mux step or direct copy — see 5.6/5.8).
- [ ] 5.3 `DownloadService` (foreground service, `dataSync` or `specialUse` type per current
  Android foreground-service-type requirements): owns a `Map<Long, Job>` of active **track**
  downloads, launched via `CoroutineScope(SupervisorJob() + Dispatchers.IO)`.
  - `enqueue(groupId)`: launches one coroutine per child track, updates each track's Room row
  (`downloadedBytes`) on a throttled interval (e.g. every 200ms or every 256KB) — not on
  every chunk, to avoid hammering Room/Compose recomposition. The group's derived progress
  (Phase 4.5) is the sum across its tracks.
  - `pause(groupId)`: cancels every active `Job` for that group's tracks (keeps partial bytes
  on disk + `downloadedBytes` in Room, sets track statuses `PAUSED`; group status derives
  to `PAUSED`).
  - `resume(groupId)`: re-launches each non-completed track with a `Range` header starting at
  its own stored `downloadedBytes`.
  - `delete(groupId)`: cancels jobs if running, deletes all staging files, the final
  `MediaStore` entry if one exists, and the group row (tracks cascade-delete per the FK).
- [ ] 5.4 Persistent notification (`DownloadNotifier`) showing: current file, **group-level**
  progress % (summed across tracks), speed (computed from bytes delta / time delta), and
  Pause/Cancel actions directly on the notification (PendingIntents → broadcast receiver →
  service).
- [ ] 5.5 Handle process death mid-download: on `DownloadService` restart, query Room for any
  track rows stuck in `DOWNLOADING` and flip them (and their parent group) to `PAUSED` (never
  silently resume without user intent, and never leave a phantom "downloading" row with no
  active job).
- [ ] 5.6 Storage: use `MediaStore.Downloads` (API 29+) via `ContentResolver` for the **final**
  output file only (post-mux, or the raw file directly for a non-mux image download) — do
  **not** request broad `WRITE_EXTERNAL_STORAGE` on modern targets; scoped storage is
  mandatory for Play Store compliance. For API 26–28 fallback, request
  `WRITE_EXTERNAL_STORAGE` runtime permission and write directly to
  `Environment.DIRECTORY_DOWNLOADS`.
- [ ] 5.7 Verify each track's integrity post-transfer: compare its `downloadedBytes` to the
  `Content-Length` reported at request start; mark that track (and the group) `FAILED` with a
  retry option if they don't match.
- [ ] 5.8 **Mux step (video + audio only, `requiresMux == true`):** once *both* child tracks reach
  `COMPLETED`, set the group status to `MUXING` and combine them using Android's built-in
  `MediaExtractor` + `MediaMuxer` (no third-party/FFmpeg dependency needed for a straight
  remux — video and audio elementary streams go in, one `.mp4` with both comes out):
  1. Open a `MediaExtractor` on the staged video file, select and read its video track format.
  2. Open a second `MediaExtractor` on the staged audio file, select and read its audio track
  format.
  3. Create a `MediaMuxer` pointed at a new staging output file, add both tracks
  (`addTrack()`), call `start()`.
  4. Pump samples from each extractor into the muxer via `writeSampleData()` until both are
  exhausted, tracking each sample's `presentationTimeUs`.
  5. `stop()`/`release()` the muxer and both extractors, delete the two now-unneeded raw
  staging files, copy the muxed output into `MediaStore` (5.6), set group status
  `COMPLETED`.
  - Run this on a background dispatcher; it's CPU-bound, not network-bound, but should still
  report a determinate "Finalizing…" state in the notification/UI rather than looking stuck.
  - If muxing throws (malformed/incompatible streams), mark the group `FAILED` with a specific
  "couldn't finalize video" error rather than silently keeping only the video-only file.
- [ ] 5.9 Signed-URL expiry: if a track request comes back `403`/expired mid-download or on
  resume (per the §3.3 note on time-limited CDN URLs), call `MediaRepository.resolve()` again
  for the group's `sourceUrl`, update that track's `remoteUrl`, and retry — don't surface this
  as a hard failure unless the re-resolve itself fails.

### Phase 6 — Home Screen (paste link + download)
- [ ] 6.1 `HomeScreen` composable: `OutlinedTextField` for the link, a "Paste" button that reads
  `ClipboardManager`, a "Fetch" button.
- [ ] 6.2 `HomeViewModel`: on Fetch, run `IsInstagramUrlUseCase` → if invalid show inline error;
  if valid, run `FetchMediaInfoUseCase`, expose `HomeUiState` (`Idle`, `Loading`, `Preview(MediaInfo)`,
  `Error(message)`).
- [ ] 6.3 Preview state UI: thumbnail (Coil `AsyncImage`), media type badge (Reel/Post/Image),
  caption snippet, and a "Download" button that calls `StartDownloadUseCase` and navigates to
  the Downloads screen (or shows a snackbar "Added to downloads").
- [ ] 6.4 A reel's video + audio tracks are downloaded together as one `DownloadItem` (Task 3.5) —
  the user just taps one "Download" button per link; there's no separate video/audio choice
  shown in the UI. If a genuine multi-image carousel response is ever confirmed from the API
  (not observed in the sample response — see §3.3), revisit this as a "Download all" option.
- [ ] 6.5 Empty/loading/error states with proper Compose previews for each `HomeUiState` variant.

### Phase 7 — Downloads Screen (progress, pause/resume/delete)
- [ ] 7.1 `DownloadsScreen`: `LazyColumn` observing `ObserveDownloadsUseCase` as `Flow` via
  `collectAsStateWithLifecycle()`.
- [ ] 7.2 Per-item row: thumbnail, filename, `LinearProgressIndicator` bound to
  `downloadedBytes/totalBytes`, human-readable size ("12.4 MB / 48.0 MB"), computed speed,
  and status chip (Queued/Downloading/Paused/Completed/Failed).
- [ ] 7.3 Action buttons per row, conditional on status:
  - `DOWNLOADING` → Pause, Delete
  - `PAUSED` → Resume, Delete
  - `MUXING` → Delete only (no pause/resume mid-finalize — show an indeterminate "Finalizing…"
  indicator instead of a determinate progress bar, per Task 5.8)
  - `FAILED` → Retry, Delete
  - `COMPLETED` → Open (via `Intent.ACTION_VIEW` + `MediaStore` URI), Delete
- [ ] 7.4 Delete confirmation dialog (destructive action — don't delete on a single tap).
- [ ] 7.5 Swipe-to-delete gesture as a secondary affordance (optional polish).
- [ ] 7.6 Empty state ("No downloads yet") when the list is empty.

### Phase 8 — SSL Pinning
- [ ] 8.1 Extract the SHA-256 pin of your **own proxy domain's** certificate (not RapidAPI's,
  since the app only talks to your proxy per §2):
  ```
  openssl s_client -connect api.yourdomain.dev:443 -servername api.yourdomain.dev < /dev/null 2>/dev/null \
    | openssl x509 -pubkey -noout \
    | openssl pkey -pubin -outform der \
    | openssl dgst -sha256 -binary | base64
  ```
- [ ] 8.2 Also extract a **backup pin** — either the issuing CA's public key, or (if on
  Cloudflare) a second Cloudflare edge cert — so a routine cert rotation doesn't hard-brick
  the app. Never ship a single pin with no backup.
- [ ] 8.3 Wire into `NetworkModule`'s `OkHttpClient.Builder`:
  ```kotlin
  CertificatePinner.Builder()
      .add("api.yourdomain.dev", "sha256/PRIMARY_PIN=")
      .add("api.yourdomain.dev", "sha256/BACKUP_PIN=")
      .build()
  ```
- [ ] 8.4 Alternatively/additionally declare the same pins in
  `res/xml/network_security_config.xml` and reference it in the manifest — belt-and-braces,
  and it also protects any WebView usage if you add one later.
- [ ] 8.5 Handle `SSLPeerUnverifiedException` gracefully: show a "secure connection couldn't be
  verified — please update the app" message rather than a raw crash; log (without the payload)
  to your crash reporting tool so you know if a pin needs rotating.
- [ ] 8.6 **Operational task, not a one-time code task:** put a calendar reminder ~30 days before
  your proxy's TLS cert is due to rotate, so you can ship an app update with the new pin ahead
  of expiry. A pinned app with an expired/rotated, un-updated pin set fails 100% of requests.
- [ ] 8.7 Test pinning actually works: point a MITM proxy (e.g. Charles/mitmproxy with a trusted
  root cert on a test device) at the app and confirm requests are rejected.

### Phase 9 — Permissions & Manifest
- [ ] 9.1 `POST_NOTIFICATIONS` runtime permission request (API 33+) before starting the foreground
  download service, since its notification won't show without it.
- [ ] 9.2 Foreground service type declaration matching current Play policy for file-transfer-type
  services; test that Android doesn't kill the service mid-download on Doze/App Standby.
- [ ] 9.3 `WRITE_EXTERNAL_STORAGE` only on `maxSdkVersion="28"` in the manifest (unused/ignored on
  29+ where `MediaStore` is used instead — see 5.6).
- [ ] 9.4 Internet permission, and no unnecessary broad permissions (avoid Play Console policy
  friction).

### Phase 10 — Error Handling & Edge Cases
- [ ] 10.1 Private/deleted/age-restricted post → proxy/RapidAPI will fail to resolve; surface a
  clear, specific message ("This post is private or unavailable") rather than a generic error.
- [ ] 10.2 No network connectivity → check before hitting Fetch/Download, show offline state.
- [ ] 10.3 RapidAPI quota exhausted (surfaced by your proxy) → distinct message + optional
  client-side backoff so users aren't repeatedly hammering a dead quota.
- [ ] 10.4 Storage full → catch `IOException` from the `MediaStore`/file write, mark item `FAILED`
  with a specific "not enough storage" reason.
- [ ] 10.5 App killed mid-download and relaunched → Phase 5.5 already handles the Room-state
  reconciliation; verify the Downloads screen reflects `PAUSED` correctly on relaunch.
- [ ] 10.6 Duplicate link pasted twice → either dedupe (reuse existing `DownloadItem` if same
  `sourceUrl` + not yet completed) or allow duplicates explicitly — decide and document the
  chosen behavior.

### Phase 11 — Testing
- [ ] 11.1 Unit tests: use cases (mock repositories), mappers (DTO→domain, domain→entity),
  `IsInstagramUrlUseCase` regex edge cases.
- [ ] 11.2 Repository tests: `MediaRepositoryImpl` against a fake `InstagramApiService`
  (MockWebServer), covering success, 4xx, malformed JSON.
- [ ] 11.3 `DownloadEngine` tests: resume-from-offset logic against MockWebServer serving partial
  content with `Range` support.
- [ ] 11.4 Compose UI tests: `HomeScreen` state transitions, `DownloadsScreen` action buttons
  appearing/disappearing per status.
- [ ] 11.5 Manual test matrix: reel (video), single image post, carousel post, story link if
  supported by the API, on both a fresh install and after a paused+resumed download.

### Phase 12 — Release Prep
- [ ] 12.1 R8/ProGuard rules reviewed; confirm release build still resolves links correctly
  (Retrofit/Moshi/Room reflection rules are a common release-only breakage point).
- [ ] 12.2 Signed release build, Play App Signing enrolled.
- [ ] 12.3 Confirm the app **never bundles the RapidAPI key** in the release artifact — grep the
  final APK/AAB (`unzip` + `strings`) for the key literal as a final sanity check.
- [ ] 12.4 Privacy policy covering: what's sent to your proxy (the pasted URL), that no Instagram
  login/credentials are ever collected, and any analytics/crash reporting in use.
- [ ] 12.5 **Distribution-risk note:** apps that download social media content are a recurring
  Play Store policy gray area (Instagram's ToS prohibits unauthorized downloading of content,
  and Google has removed downloader apps before). This doesn't block building/using the app,
  but budget time for possible Play Console review friction, and consider whether Play Store
  distribution or direct APK/sideload distribution is the right call for this project.

---

## 5. Acceptance Criteria

- [ ] Pasting a valid Instagram reel/post link and tapping Download shows a preview, then starts a
  trackable download with zero RapidAPI key material anywhere in the shipped app.
- [ ] Downloads screen shows live progress, accurate speed, and correct byte counts for every
  active item.
- [ ] Pause truly stops network I/O (verify via network inspector, not just UI state) and Resume
  continues from the same byte offset, not from zero.
- [ ] Delete removes both the Room row and any on-disk/MediaStore artifact — no orphaned files.
- [ ] Killing the app mid-download and reopening it shows the item as `PAUSED`, never a phantom
  stuck `DOWNLOADING` state.
- [ ] A MITM proxy on a test device cannot intercept traffic to your API domain (SSL pinning
  verified per Task 8.7).
- [ ] Completed files are visible in the system Downloads/Gallery app via `MediaStore`, on both a
  device running API 29+ and one running API 26–28.
- [ ] A saved reel **has audio** when played back — confirms the video+audio mux step (Task 5.8)
  actually ran and wasn't skipped, since the two tracks arrive from the API separately.

---

## 6. Open Items You Need to Resolve Before Coding Starts

1. Confirm the `type: "image"` single-track assumption for photo posts with a real API call
   (Task 4.1) — only the reel (video+audio) shape has been verified so far.
2. Decide whether your Cloudflare Worker proxy is a modification of your existing Instagram reel
   downloader Worker or a new one — reusing existing infra is likely faster.
3. Decide min SDK (26 assumed above) based on your target audience's device spread.
4. Decide Play Store vs. sideload/direct-APK distribution given the policy note in Task 12.5.
5. Confirm how long the CDN URLs stay valid (§3.3's signed-URL expiry note) — worth one manual
   test of resolving a link, waiting, then trying the raw URL later, so Task 5.9's retry logic is
   tuned to a real expiry window rather than a guess.