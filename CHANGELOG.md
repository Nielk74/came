# Changelog

## Unreleased

## 0.4.0 - 2026-07-25

- Discover the rear logical camera's physical camera ratios at runtime instead of assuming a model
  or a fixed 0.5×/1×/telephoto configuration.
- Add an animated, accessible lens capsule that is hidden automatically on single-lens devices.
- Switch field of view through the bound logical camera so OEM sensor fusion and Auto/HDR
  extensions remain active without a preview rebind.
- Filter suggestions against the active camera's real zoom range, deduplicate approximate device
  metadata, and avoid presenting digital maximum zoom as an optical lens.
- Block shutter input during a lens transition and retain the prior selection if CameraX rejects
  the requested ratio.

## 0.3.0 - 2026-07-25

- Prefer the device vendor's automatic/HDR CameraX extension on supported Pixel phones and fall
  back safely to the standard back camera.
- Capture full-quality JPEG input with flash kept off, replacing the previous latency-first mode.
- Develop flat camera JPEGs before film emulation with guarded auto-levels, stable exposure,
  restrained contrast, and edge-aware local separation.
- Bound histogram work and use luminance lookup tables to keep full-resolution processing fast.
- Add regression coverage for tonal expansion, hue preservation, histogram bounds, edge halos,
  and exposure stability around mixed-light scenes.

## 0.2.0 - 2026-07-24

- Keep hardware flash off by default and switch capture to CameraX's low-latency mode.
- Replace vertical filter switching with an animated horizontal film carousel and persistent shutter.
- Apply each film profile's color matrix to the live preview on the GPU.
- Add tap-to-focus and auto-exposure with an animated focus reticle.
- Add an in-app photo library with thumbnails, zoom, pan, navigation, metadata, sharing, and deletion.

## 0.1.1 - 2026-07-24

- Keep the full-screen shutter gesture from intercepting taps on the transient settings gear.
- Add a regression test for viewfinder taps consumed by camera controls.

## 0.1.0 - 2026-07-24

- Launch the control-free CameraX viewfinder with tap-to-capture and volume-key shutter.
- Add vertical film-look switching and a transient rotating settings affordance.
- Adapt eleven film profiles and optional tone-driven grain from the Ricoh GR III film pipeline.
- Add a full-screen Fujifilm-inspired menu for grain, active looks, timer, and updates.
- Add pagination-safe, checksum-verifying in-app updates from this public repository.
- Add tested CI and signed tag-driven APK/AAB releases.
