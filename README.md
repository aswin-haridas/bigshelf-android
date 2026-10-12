# BigShelf for Android

Native Android client for BigShelf, written in Kotlin with Jetpack Compose. It talks to the
same FastAPI backend as the React app in `../frontend` and uses the same design system:
the same tokens, components and Phosphor icons.

## Run it

1. Open the `android/` folder in a recent Android Studio (Meerkat or newer), or use the Gradle wrapper.
2. Point the app at your backend. The default is `http://10.0.2.2:8000`, which is the host
   machine as seen from the emulator (`docker compose up` exposes the API on port 8000).
   ```sh
   ./gradlew installDebug -Pbigshelf.apiUrl=https://photos.example.com
   ```
   You can also set `bigshelf.apiUrl` in `gradle.properties` or `~/.gradle/gradle.properties`.
3. Sign in with an existing account, or create one from the Sign Up link.

Requires JDK 17+. minSdk 26, targetSdk 35.

## Design system parity

Every visual value comes from `frontend/src/index.css` and the shared web components.
**Change a token on both sides.**

| Web | Android |
| :-- | :-- |
| `@theme` colours (`--color-*`) | `ui/theme/Color.kt` → `BsColors` |
| `--text-*`, `--font-weight-*` | `ui/theme/Type.kt` → `BsText`, `BsWeight` |
| `--radius-*` / Tailwind `rounded-*` | `ui/theme/Shape.kt` → `BsShapes` |
| `@phosphor-icons/react` | `ui/theme/Icons.kt` → `BsIcons` (`com.adamglin:phosphor-icon`) |
| `font-mono` | `MonoFamily` |
| `button:active { scale(0.96) }` | `Modifier.pressScale` / `bsClickable` |

| Web component | Compose port |
| :-- | :-- |
| `Button.tsx` (primary / secondary / ghost, loading) | `BsButton` |
| header icon buttons (`p-2 bg-surface border rounded-lg`) | `BsIconButton` |
| `Input.tsx`, `Field.tsx` (password eye, hint) | `BsInput`, `BsField` |
| `SearchBar.tsx` | `BsSearchBar` |
| `CustomModal.tsx` | `BsModal` (+ `ConfirmModal` in place of `window.confirm`) |
| `ui/Tabs.tsx`, `ui/Switch.tsx`, `ui/Progress.tsx`, `ui/EmptyState.tsx` | `BsTabs`, `BsSwitch`, `BsProgress`, `EmptyState` |
| sonner `<Toaster>` (compact, top-center) | `Toaster` + `toast.success/error/info` |
| `image-card.tsx`, `face-tile.tsx`, `images-grid.tsx` | `ImageCard`, `FaceTile`, `ImagesGrid` |
| `SelectionActionBar.tsx`, `UploadProgressModal.tsx` | same names under `ui/shell/` |
| `sidebar.tsx` | bottom nav in `AppShell` + the Library tab (`ui/shell/Library.kt`) |

The same rules apply as on the web (see `../AGENTS.MD`): use the shared components as they
are, take colours only from `BsColors`, keep icons at 12 / 14 / 16 / 20 / 24 / 32 dp, page
titles `BsText.Lg` semibold, panel headers `BsText.Sm` medium, and no bold.

Where the phone has no hover, a few hover-only details are adapted for touch:

- Photo tiles show their selection circle once a selection is active, and a long press
  starts a selection.
- Face tiles always show their name caption.
- The upload drop zone opens the system photo picker.
- Images shared from another app (Gallery, Photos, Files) via the share sheet are uploaded.

## What's in the app

- Sign in / sign up, with the session kept across restarts through the `refresh_token`
  cookie, like the web.
- Home timeline with month headers, infinite scroll, text search and the "show AI images"
  setting. Uploads go through the same presigned flow as the web, with the progress box.
- Bottom navigation (Photos, Favourites, People, Library); Library holds upload, Archive,
  Bin, the tools, Settings and Log out.
- Photo viewer: swipe between photos (the bars stay put, a tap hides them), plus favourite,
  mark as AI, rotate, download, delete, and an Info sheet with tagged faces.
- People: the People, Detected Faces and Untagged Photos tabs, a person page with rename
  and download all, and face groups.
- Favourites, Bin (restore, delete permanently, empty Bin), Archive folders, Settings.
- Backup & storage (Library): automatic backup of every photo and video on the phone. It runs
  as a WorkManager job every 15 minutes on Wi-Fi when the battery isn't low, with a progress
  notification (with a Pause button), Pause / Resume, and notices when an upload fails or
  finishes. Each file's SHA-256 goes along with the upload, so the API refuses ones already
  in the library before any bytes are sent. The same page shows the storage used, plus item
  counts for Library, Archive and Bin.
- Sharing: Share in the selection bar makes a public link to the selected photos, and an album's
  Share button makes one to the album, adds people by username or email (they can add and
  upload their own photos to it) or lets a member leave. Library > Sharing lists albums
  shared with you and your links, to share again or revoke. Links open on the web app at
  `/s/<token>`, so they point at `bigshelf.apiUrl` and only work where the web app is served
  from the same origin (as in production behind Caddy).
- A selection bar that takes the bottom nav's place while photos are selected: add to album,
  untag, download, archive or unarchive, move to Bin.
- Pipelines (run, progress, run detail with step logs - polled instead of the web's SSE),
  Duplicates (one pair at a time, keep / keep both / pick the best, undo) and Organize
  (review buckets, select / download all, reprocess failed).

Framer, Triptych, Triplets and Composer are listed in Library but show an "open BigShelf
on the web" placeholder for now. The web features they depend on
(face tagging modal, crop, split, merge, reverse image search) aren't ported yet either.

## Layout

```
app/src/main/java/com/bigshelf/app/
  data/      Api (OkHttp + kotlinx.serialization), models, cookie jar, view settings, downloads
  state/     ports of the zustand stores (auth, selection, upload) + a tiny query/refetch helper
  ui/theme/  design tokens
  ui/components/  shared component library
  ui/photos/ grid, tiles
  ui/shell/  app shell: bottom nav, Library tab, selection bar, upload progress
  ui/screens/ pages
```
