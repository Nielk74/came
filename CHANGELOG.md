# Changelog

## Unreleased

## 0.16.0 - 2026-07-29

- Rotate the finished photograph automatically from the phone's physical orientation. Portrait,
  both landscape directions, and reverse portrait now produce upright pixels even though camé's
  viewfinder controls remain locked in their portrait layout.
- Drive CameraX capture and QR-analysis rotation from a lifecycle-bound orientation listener, then
  keep the existing EXIF-aware decode as the single place that bakes orientation into the rendered
  JPEG.

## 0.15.0 - 2026-07-29

- Recognize web links in QR codes directly in the live viewfinder. A single understated, tappable
  text link appears when a code is in frame—without adding a scanner mode or another camera icon—
  and brief missed frames no longer make it flicker.
- Keep QR recognition entirely on-device with a bundled scanner model, restrict it to QR codes and
  safe HTTP(S) destinations, and retain vendor Auto/HDR capture on devices that support concurrent
  image analysis.

## 0.14.4 - 2026-07-29

- Make the Portra 400 end panel fill its square thumbnail edge to edge, removing the scan-colored
  side gutters while retaining the complete stock name, roll count, format, and Kodak footer.

## 0.14.3 - 2026-07-29

- Reframe Portra 400 around the complete left end of the current five-roll box. The new thumbnail
  keeps the panel's real proportions and all four identifying lines, including the full Kodak
  Professional footer, instead of zooming in until the packaging was clipped.

## 0.14.2 - 2026-07-29

- Keep the real square film-packaging crops square in the camera carousel. Its fixed-height card now
  reserves the full 52 × 52 dp artwork slot instead of constraining that slot to a rectangle.
- Put a compact EV control directly above the settings gear, with both controls sharing one vertical
  rail. Its thumbwheel opens centred above the camera controls, stays clear of the film carousel,
  and remains open until Back, an outside tap, or another camera action dismisses it.

## 0.14.1 - 2026-07-29

- Keep camé alive after deleting a photograph. Gallery changes now invalidate the activity's stale
  thumbnail URI before reloading, and a MediaStore item that disappears during an in-flight bitmap
  read becomes an empty image result instead of an uncaught `FileNotFoundException`.
- Replace the wide packaging-front thumbnails with compact 360 × 360 crops from the square side or
  end face of every unfolded box, rotating the CineStill and Superia labels upright. The Vision3
  500T bulk-can profile keeps a square crop of its round can lid.
- Rework the EV popover above the settings gear. Every detent now fills its pager slot so the white
  tick sits exactly under the red index, and a tap outside dismisses the wheel while camera-control
  taps continue through to their intended action.

## 0.14.0 - 2026-07-28

- Put exposure compensation under the photographer's thumb. An `EV` badge opens a five-detent,
  haptic thumbwheel for -2, -1, 0, +1, and +2 stops; each detent is mapped to the closest index in
  the active camera's own supported range and step rather than assuming every HAL uses thirds.
- Add composition-only pinch zoom from 1× to 4×. The preview scales the exact 4:3 capture frame,
  while CameraX still records the complete frame and every development, stock, sky, halation, and
  grain pass runs at full size. The matching centred crop is made only after the renderer returns.
- Add an optional electronic level. The camera menu enables a lifecycle-aware rotation-vector
  horizon that filters sensor jitter, stays hidden without a stable reading, and changes its small
  white moving line to green within 1.5 degrees of level.
- Replace abstract colour swatches with crops of real film packaging from the Film Packaging
  Archive throughout the carousel, settings, and capture-progress card, with an asset manifest
  linking every source scan and recording the archive's image-use notice.

## 0.13.0 - 2026-07-27

- Finish the sky on the print instead of before it. Recovering a washed-out sky and then letting the
  stock read the result sounds right and measures wrong: the negative and print curves exist to
  compress a large, bright, low-chroma region, so they took most of the recovered blue straight back
  out and the sky arrived at the photograph as the grey ceiling it started as. Measured on a frame
  through the whole pipeline, the sky went in at (152,177,201) and came out at (138,153,159). The
  same work placed after the stock survives to the picture.
- Give the sky its colour by expanding the blue it still has rather than by pulling red down. The
  correction now works on chroma around the sky's own luminance and pushes along a cyan-leaning
  blue, with one shared gamut limit so a strong sky keeps its hue instead of turning as each channel
  clips in turn. It also takes far less brightness out than before, since by this point the print
  has already brought the frame down.
- Keep cloud white. Cloud is lit by the whole sky and comes back neutral; a sky that a computational
  exposure has run up against the top of the range comes back pale but still faintly blue, because
  the blue was diluted rather than clipped. The correction now turns on that difference, so a cloud,
  a white wall, and a genuinely blown patch hold their white while the sky around them goes blue.
- Print every stock on a common base: blue through the low-mids, warmth through the tones a subject
  occupies, and nothing at all in the highlights. A phone JPEG is close to neutral all the way up
  its range, which is the one thing colour film never is. Each stock's own highlight tint is now
  rolled off as it approaches paper white for the same reason — white in the picture should be
  white.

## 0.12.0 - 2026-07-27

- Treat the sky by its colour instead of by a mask. Both sky stages used to carry a region outline
  into the pixels themselves — a block flood fill for the recovery, a row-wise connectivity pass for
  the stock's sky colour — and every place that outline stopped short left a seam: a branch or a
  wire spanning the frame cut off everything below it, so the sky under the line kept the pale,
  neutral rendering while the sky above it was corrected. Which pixels are sky is now decided from
  each pixel's own brightness, warmth, and remaining colour, which follows a skyline exactly and
  cannot draw an edge of its own.
- Recover each part of the sky by how much colour it has actually lost, rather than by one amount
  for the whole frame. A sky running from deep blue overhead to washed out at the horizon now keeps
  the top as it was and pulls the pale band back, instead of averaging the two into a single
  correction that suits neither.
- Find the sky once per photograph and let both stages share it. Detection now also recognises a
  deep blue sky rather than only a bright near-neutral one, seeds on real confidence and grows on
  little, and follows the sky's colour past a thin obstruction — so a frame whose sky was previously
  missed altogether, or truncated at the first wire, is now read to the horizon. It is used only to
  say how far down the frame the sky reaches, easing off over a wide band, which keeps the treatment
  off a bright cool wall or a lit interior without putting a boundary back into the picture.

## 0.11.0 - 2026-07-25

- Report the capture on a card carrying the stock's own swatch, with the pipeline laid out as a
  trail of dots. The dots show only the stages that capture will actually run: halation and grain
  are conditional in the renderer, so a stock that authors no halation, or a photograph taken with
  grain switched off, is no longer promised a stage it will never reach. `CaptureRunTest` holds the
  announced list against the stages the renderer really emits, so the two cannot drift apart.
- Name the stock that is in the pipeline rather than the one the carousel has moved on to. The
  photograph was always printed with the stock selected at the shutter, but the progress card read
  from the live selection, so choosing another film mid-render relabelled the work in flight.
- Dim the whole viewfinder while a photograph is being made, the film carousel included. The
  shutter, the library and the menu were already inert during a capture; the carousel was not,
  which is how a selection could change under a render.
- Show an armed self-timer beside the flash badge, so the viewfinder says a delay is set before the
  shutter is pressed rather than only while it counts down. Both badges are now pills that keep
  clear of the status bar strip and of a display cutout, and the countdown pops on each second.
- Give the film carousel page dots in place of a "FILM" label, glide a neighbouring card into the
  centre when it is tapped, and mark each detent with a haptic — the shutter, the focus tap and a
  lens change are felt too. The detent is only felt for a real gesture: it used to fire for the
  page the carousel simply opened on, which meant a buzz on every launch and on every return from
  the menu or the library.
- Pop the latest photograph into the library button as it lands, square off the library's grid
  cells, and add double-tap to zoom in the full-screen viewer.
- Print the running version at the foot of the menu, and draw the menu, the library and the
  overlays over the viewfinder from one shared dark-room palette instead of three near-identical
  sets of literals.

## 0.10.0 - 2026-07-25

- Advertise camé as a camera app. It previously declared only a launcher entry, so nothing in the
  system could offer it where a still camera was requested and it could not be picked as a camera
  default anywhere. It now handles `android.media.action.STILL_IMAGE_CAMERA`.
- The lock-screen variant of that intent is deliberately not handled: it runs over the lock screen
  and camé carries a photo library that must not be reachable without unlocking. `ACTION_IMAGE_CAPTURE`
  is also left alone, since that intent is a contract to return the photograph to the calling app,
  which camé does not implement yet.

## 0.9.0 - 2026-07-25

- Say what the camera is doing while it renders. A full-resolution frame passes through scene
  development, sky recovery, the stock's own response, halation, and grain, and that takes real
  time. The viewfinder now names the stage in progress — exposing, reading the frame, developing,
  recovering sky, printing the selected stock, halation, grain, saving — rather than showing an
  unexplained pause. Stages that do no work for the selected stock are never announced.

## 0.8.0 - 2026-07-25

- Frame what you actually get. The viewfinder filled the screen by cropping the sensor frame, so on
  a tall phone it showed a much tighter picture than the one that was saved. It now fits the frame
  instead of filling it, letterboxed against the black backdrop, and the viewfinder and the capture
  are pinned to one aspect ratio so they cannot drift apart. Saved photographs keep their full field
  of view and resolution.
- Rebuild grain on the reference's film-plane model. The tent response is now integrated
  analytically over each output pixel's footprint on the emulsion instead of the lattice being
  sampled bilinearly, so a preview holds the area-average of the same field a full-resolution export
  samples and grain keeps its physical size rather than turning into soft blur at high resolution.
  Density is perturbed in the log-odds of linear luminance, which is how density actually
  accumulates, and the result is gamut-compressed toward the new luminance rather than clipped per
  channel. Clumping is variance-normalised, so it changes the shape of the distribution's tails
  instead of quietly amplifying the whole field.
- Step the develop stage back. Its tone curve was tuned when the film profiles were nearly neutral
  and it was carrying the contrast on their behalf; since the stocks began rendering their own
  authored response the two compounded and the result read as overcooked. Measured across ten real
  Pixel 8 frames, the contrast it adds falls from about +19% to +9%.
- Stop crushing shadows. Anchoring the black point on the 2nd percentile clipped exactly that share
  of every frame by construction. It now samples nearer the true floor and stops short of it, which
  takes near-black clipping from 2–3% of the frame to under 1%.
- Speed up the full-resolution render: a tabulated sRGB encode, per-row lattice resolution, and
  trimmed kernel taps make the new grain about six times faster than its first implementation.

## 0.7.0 - 2026-07-25

- Recover a washed-out sky during scene development, before any film stock is applied. A
  computational camera exposes for the subject and lets a bright sky run up against the top of the
  range, so it arrives pale and close to neutral. The new stage pulls the sky's brightness back and
  takes red down further than blue, returning colour to it rather than just making a grey sky
  darker. On an overcast test frame the sky fell from 238 to 211 mean level and gained 46% relative
  saturation.
- Detect sky from two independent kinds of evidence. A coarse block grid judges brightness,
  flatness, and coolness and then keeps only regions that flood-fill back to the top edge of the
  frame, which is what separates sky from any other bright flat surface. Each pixel is then gated
  on its own brightness and colour, so the correction stops exactly at the skyline instead of
  leaving a darkened band along whatever borders it.
- Scale the correction to what the sky actually needs: an already-deep blue sky is left nearly
  alone while a bright near-neutral one gets the full treatment. Verified against real Pixel 8
  frames, including negative cases — a blue-lit station interior, a bright plastic tunnel over
  snow, a sunlit white stone tower, and a warm sunset sky are all left untouched.

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
