# Sounds Like → Calm Home favourites

Calm Home now accepts an opt-in read-only Android ContentProvider from any installed app. No package name is hard-coded. Sounds Like must implement this feed; installation alone cannot expose its private favourites.

Declare an exported, enabled provider with an authority owned by Sounds Like (for example `your.package.calmhome`) and provider metadata:

```xml
<provider android:name=".CalmHomeProvider"
    android:authorities="your.package.calmhome"
    android:exported="true">
    <meta-data android:name="uk.michael.calmhome.FAVOURITES" android:value="true" />
</provider>
```

Calm Home queries `content://your.package.calmhome/favourites`. Return a Cursor in the user's chosen order, at most 40 stations, with these columns:

| Column | Value |
| --- | --- |
| `_id` | Stable unique station ID |
| `title` | Station name |
| `thumbnail_uri` | App-provided landscape artwork URI |
| `intent_uri` | `Intent.toUri(Intent.URI_INTENT_SCHEME)` for opening that station |

`poster_art_uri` is accepted if `thumbnail_uri` is empty. Supply 16:9 artwork, preferably 640×360 or larger. Artwork may be HTTPS or a readable `content://` URI served by the opted-in provider. Keep assets below 4 MiB. Calm Home bounds decoded images to 1920×1080. Do not publish server credentials, bearer tokens, stream URLs containing passwords, or private account details. Use opaque station IDs in exported deep links; resolve playback privately in Sounds Like.

The station Intent must resolve to an exported activity in the provider's own package. Calm Home strips selectors, clip data and flags, then fixes the owner package. A normal VIEW deep link is appropriate. Return an empty Cursor when no favourites exist or the account has signed out. Unsupported/denied feeds are ignored.

Calm Home refreshes when it resumes and via Menu → Refresh Top Shelf. It displays only the currently selected app's favourites, directly above the dock. Previews are 16:9 and 4/3 of app-icon width and height. Favourites replace the hero artwork with black. Up enters the row; Left/Right scroll; Select opens the exact station; Down/Back returns to its app. Moving down to the app grid hides both favourites and hero art. Normal hero art returns when the selected app has no favourites.

Verification: a temporary provider app on the actual Fire TV passed feed discovery, content artwork loading, dimensions, artwork suppression and remote navigation. The test app and its sample stations were removed. Real Sounds Like integration remains to be checked after that app implements this contract and is installed.
