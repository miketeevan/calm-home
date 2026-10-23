# Calm Home stability pass — 23 October 2026

## Changes

- Full HD artwork now fits the media cache. Previously a 1920×1080 ARGB image could exceed the 6 MiB cache and be immediately evicted; callbacks could then schedule another download. The bounded cache is now 12 MiB.
- Station previews decode to a bounded 640×360 instead of allocating full hero images for every favourite.
- Black-grid navigation defers invisible hero work. Feed refreshes have their own worker so thumbnail downloads do not queue ahead of catalogue updates.
- Favourites retain the selected station across metadata refresh. An empty feed returns focus to the owning app. In-flight artwork binds only to current matching tiles; paused queued requests are deferred, with retry handling on return. Failed image requests have a retry interval.
- Stale layout callbacks are tied to their original view tree and generation. Rapid grid transitions cancel older transitions and restore final opacity/translation.
- Long press verifies that focus still belongs to the originally held app. Backgrounding cancels pending holds and rolls back unfinished moves instead of saving a partial rearrangement.
- Hidden apps retain positions in the full saved order while visible apps are moved. App discovery clears stale hidden-app records before rebuilding the inventory.
- Invalid or foreign-package station links are skipped individually instead of aborting the remaining feed. Feed scanning and artwork reads have bounds. No credentials are exposed in these reports.
- Icon fitting uses bulk pixel access rather than repeated per-pixel graphics calls.

## Actual Stick verification

The complete suite passed **117 checks** across eleven groups: stability edge cases, disappearing-tile regression, moving/saving/cancelling, hide/restore, installed-app inventory, favourites, reduced background work, artwork presentation, film-detail launching, native Settings and stock Fire OS escape. After the final asynchronous favourites safeguard, the affected stability/favourites/performance groups were repeated successfully; the final skipped-request race adjustment received another favourites run.

The edge-case group includes 35 rapid grid cycles, Full HD cache retention, invalid artwork, bounded thumbnail decoding, memory-pressure handling, station-focus preservation, empty-feed recovery, a real Settings handoff while moving, activity recreation and navigation with a single remaining app.

Temporary fixtures are removed after verification. User order and hidden choices are restored by the tests. The favourites integration uses a temporary opt-in provider because Sounds Like has not yet been installed/integrated; its real feed is not validated here. Streaming app launches and Frame film details are exercised; this pass does not certify prolonged audio/video playback in every third-party app.

The test scripts and synthetic feed are included in `tests/`. Device logs, inventories, preferences and recovery backups are deliberately excluded from this repository.

This was a point-in-time device pass, not a multi-day soak or a guarantee of 60 fps. Fire OS Home redirection remains subject to the firmware's launcher restrictions.
