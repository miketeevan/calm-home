# Calm Home 2.0 — tvOS guidance applied

Reviewed 23 October 2026 against [Designing for tvOS](https://developer.apple.com/design/human-interface-guidelines/designing-for-tvos), [Top Shelf](https://developer.apple.com/design/human-interface-guidelines/top-shelf) and [Focus and selection](https://developer.apple.com/design/human-interface-guidelines/focus-and-selection).

- Edge-to-edge backgrounds replace padded app-icon previews. Shared films retain their artwork and source-app details links. Apps without shared programmes use a bundled brand plate or resolution-independent native backdrop.
- A static brand image cannot take focus; directional exploration is reserved for actual shared programmes. Select opens the source app’s details; Play/Pause opens playback.
- App focus grows gently by 5.5%, with a restrained white gloss edge. Containers allow overflow and tile margins preserve space for the enlarged state. Placement uses the same fixed 16:9 grid.
- The normal screen contains the clock and app row; persistent instruction text is removed. Move mode retains clear instructions and cancellation. The grid Settings tile opens Fire TV settings.
- Icons and banners come from each installed app’s own package resources. Existing branding keeps its aspect ratio and is fitted into a 16:9 canvas. All enabled, exported, launchable installed packages are included; non-launchable Android services are not app tiles.

## Asset and rendering facts

Disney’s preferred brand plate is the official 1920×1080 press asset. Apple CDN brand plates are delivered at their available native size (1280×768 in the checked examples), rather than described as 4K sources. BBC Sounds, Frame, Settings and unprovided brands have native vector/text backdrops, drawn at display resolution. Brand images load off the UI thread into a bounded cache. Image transitions preserve full bitmap opacity.

The checked device renders the UI at 1920×1080 (existing window-manager override, density 320); HDMI output is 3840×2160 at 59.94 Hz. This update does not change system resolution or density. These are Fire OS adaptations of the guidance, not Apple frameworks.
