# Changelog

## Unreleased

## 0.6.0 - 2026-07-25

- Strengthen every film look for computational-camera input. Measured against six real Pixel 8 and
  8 Pro photographs, the stocks were contributing almost nothing on top of the scene development:
  each stock's negative and print curves multiplied to a logit-space slope of essentially 1.0 —
  0.995 for Portra 400 — so the "film" amounted to a ±10/255 wobble that was then diluted ~20% back
  toward the untouched capture. The colour stocks now move a developed frame 1.5–2.3× further.
- Amplify each stock's authored deviation from its own neutral response rather than retuning
  eleven stocks by hand, so the relationships between them survive. The neutral for the print curve
  is the contrast that would exactly cancel the negative; cross-talk and saturation amplify around
  identity. Deviations are amplified through a soft limit, so a mild stock receives nearly the full
  gain while an expressive one stays inside the renderer's parameter range instead of clipping
  against it.
- Treat added contrast and reduced contrast differently. Amplifying a flat stock's flatness
  increases its measured distance from the source but reads as a weaker look, so Eterna and the
  Vision3 stocks keep their authored tone and take their character from colour instead.
- Stop diluting the look toward the untouched capture. With the response above carrying the
  stock's identity, blending the source back in only undid what the stock had established.
- Add a JVM regression guard on look strength, separation between stocks, and endpoint safety, and
  make the renderer's pixel path callable without a `Bitmap` so it can be measured off-device.

## 0.5.0 - 2026-07-25

- Add the selective foliage and sky colour response from the reference colour science: eligible
  vegetation greens rotate toward cyan-green and open sky toward cyan, both at their original
  linear-light luminance. Gating is deliberately local — a soft hue/chroma/tone likelihood for
  foliage, plus connectivity to the top frame edge for sky — so skin, blue clothing, signage, and
  neutrals are left alone rather than dragged along by a global channel rotation. Authored for
  Portra 400 and Portra 800.
- Rebuild halation as a two-lobe response in linear light: a broad, red-biased base-reflection
  lobe plus a tighter emulsion-scatter lobe, screen-composited with the pixel's own unblurred
  source core subtracted. The highlight core therefore stays clean and the effect reads as a
  fringe around it instead of a red wash over it, and a receiver term keeps spill off surfaces
  that are already bright.
- Scale halation radii with output resolution. Radii are authored against a 1600px long edge, so a
  full-size capture now shows the intended halo rather than a few-pixel one, and previews match.
- Replace halation's single box blur with a three-pass Gaussian approximation, removing the square
  halos the previous blur left around bright edges.

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
