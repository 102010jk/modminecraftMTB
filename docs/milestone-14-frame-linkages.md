# Milestone 14 — frame linkages and dirt saddle

Release: `dist/descentmtb-milestone-14-frame-linkages.jar`; identical copy at `dist/descentmtb-latest.jar`. Includes milestone 13's launch fix; do not keep an older Descent MTB JAR alongside it.

The screenshots exposed a defect that milestone 09's rest-pose checker missed: brand shock eyes had been moved while the old common rocker/rear assembly still rendered. Some additional decorative bars were fixed to the front triangle. Passing the checker was not evidence that these looked right.

This release replaces those brand rear assemblies with generated linked members, fixed frame pivots and mounts that reach their supporting tubes. Nomad and META use two-link rear triangles. Torque, ONE77, Capra and Stumpjumper use separate chainstay, upper rocker and wheel/seatstay members. Stumpjumper's shock now sits below the top tube; Nomad's is low and almost horizontal at rest. Geometry and animation read the same profile points, eliminating the separate hard-coded shock-eye switch. Chain, wheel, shock and pivots follow the solved members. Render state is restored between bikes and previews.

Dirt saddles have a compact nose, rear and base with shorter inset rails; the low inserted post and its aligned clamp are retained. The seatstay bridge has moved to the actual stay junction. Sticker anchors and both bikes' UV textures were regenerated using the existing material pipeline. This is geometry/UV maintenance, not new texture artwork.

Verification:

- `gradlew.bat assemble --console=plain` succeeded on Java 21.
- The asset generator rejected the initial Nomad pivot arrangement because it reached only about 58 mm; the revised profiles all satisfy its monotonic four-bar closure calculation through 160 mm axle rise. This calculation is not a runtime riding test.
- Both UV generators completed without overlaps or holes. `gen_custom_assets.py` regenerated sticker anchors.
- No game launch, game screenshots, audio/multiplayer checks, frame preview rendering, unit test suite or independent agent review was performed. `check_frames.py` was updated for the new profiles/bones but was not run during this build-only pass.

These remain stylised interpretations, not manufacturer CAD or a dynamic suspension force solver. Visual proportions and the animated appearance still require user inspection in Minecraft. Physics handling and generic enduro suspension layouts are unchanged. Manufacturer references and the regeneration workflow are in `docs/frame-catalog.md`.
