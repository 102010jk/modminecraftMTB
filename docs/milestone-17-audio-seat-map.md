# Milestone 17 — music level, dirt saddle and framed maps

Release: `dist/descentmtb-milestone-17-audio-seat-map.jar`, also copied to `dist/descentmtb-latest.jar`.

The live sound entry inherited the wind placeholder's 45% volume. Live audio additionally went through the records slider, local music volume, device volume and a squared distance falloff. Simply increasing sound-instance volume above unity cannot amplify PCM because Minecraft clamps that value. Live audio now uses the master channel, keeps Minecraft master mute/volume, removes the placeholder attenuation and uses a full-level inner two metres followed by a linear fade to the selected radius. Discs continue using the records category.

`audioDevices.musicGain` in `descentmtb-client.toml` defaults to 3 (range 0–8). `GainAudioStream` applies this amplification and the device volume to signed 16-bit PCM before OpenAL, with the existing soft limiter. `musicVolume` remains the local 0–1 playback level. The device editor starts at 100%, remembers the current device/headphone selection and offers 20–200% when choosing a source. Server sanitization allows that range without changing the packet layout. The live sound entry is also preserved by the audio importer. Stereo and short live buffers from milestone 15 remain in use.

All three dirt frame shapes share a rebuilt low saddle assembly: the inserted post exposes a short neck, the coaxial clamp touches the rails, and the tapered nose, raised middle and wider rear attach to one shell. The assembly has a mild six-degree nose-up angle. `SADDLE_TOP` follows its upper surface. Hardtail textures were regenerated from the model using the existing painter and new, separate UV regions; custom material rendering traverses the new saddle bone. The geometry-check tool includes that bone but was not run.

The map and audio screens rendered their blurred background twice: the second `Screen.render` call covered their finished content. Both now render the background once and then render their widgets. Empty-map instructions wrap within the map. Map textures explicitly use nearest filtering.

Trail maps in regular and glow item frames use the vanilla map backboard and render at full-block size, with vanilla depth and quarter-turn rotation instead of the generic item's half-size transform. Vanilla prepares wall/floor/ceiling and invisible-frame placement; the NeoForge frame-item event supplies the custom route image. The client mixin changes only the map backboard selection for `TrailMapItem`. Route data still comes from the synchronized item component; the image remains a trail diagram rather than a new terrain-survey feature.

Verification: Java 21 `assemble` passed. The texture generator reported no overlapping or missing UV regions. No game launch, listening, visual preview, screenshot, frame-check run or multiplayer verification was performed. Runtime behavior and the new saddle appearance still need user confirmation in game.
