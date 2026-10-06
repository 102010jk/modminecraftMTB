# Milestone 16 — close missing trail surfaces

Release: `dist/descentmtb-milestone-16-shaped-terrain.jar`; identical copy at `dist/descentmtb-latest.jar`. Includes milestones 13–15 (startup, frames and live audio).

The supplied screenshot shows black gaps and exposed stone between shaped dirt surfaces. Two concrete rendering defects were found:

- `ShapedQuads.surface` skipped every fully filled cell. The last layer of a flat surface at an integer world height is exactly full, so it lost its entire top; the filled lower layers also lost exposed side walls.
- `TrailSurfaceBlock.skipRendering` hid all faces adjoining another trail block, although their corner heights and deck thickness are stored in block entities and can differ completely.

Filled dirt layers now draw six boundary quads, including their top and walls. Filled subcells of partially clipped layers also keep their boundary top. The baked model computes per-face neighbor coverage from actual shape/model data; a lower, thinner or transparent shaped neighbor cannot hide an exposed face. Matching internal boundaries are still culled. Raised/sloped wooden undersides are unculled rather than being incorrectly removed by a block below them. Plain ramps use the same material-aware coverage instead of state-only culling.

Saved shaped blocks use this renderer without regenerating their trail. This fixes missing rendered faces; it does not backfill actual saved air/caves or change the route generator, surface materials, physics or textures.

Verification: Java 21 `assemble` passed. Source inspection covered integer-height dirt layers, clipped multi-layer slopes, matching/mismatching neighbors and wooden undersides. No game launch, new screenshots, visual preview, gameplay/unit tests or independent agent review occurred. The user's actual world and audible playback remain unverified.
