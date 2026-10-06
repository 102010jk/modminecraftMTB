# Milestones 10–12 — unfinished immersion features

2026-10-06. Verified with Java 21 / NeoForge 21.1.250 `gradlew.bat assemble --console=plain` only. No Minecraft launch, screenshots, audio runtime test or multiplayer test was performed.

## 10: audio devices

The audio WIP is now compiled into the mod. A placed boombox, or one attached by right-clicking a bike with its item, can play a selected Windows application or a music disc carried in the inventory. Right-click the placed boombox to open its screen; use P while riding a fitted bike. Sneak-click a fitted bike with a held item also opens audio settings; sneak-click with an empty hand still picks up the bike.

Right-click headphones to equip them. P (or sneak-use the headphone item) opens personal audio settings. Application capture starts only after selecting a source. The source includes the selected application's process tree, including all its browser tabs; dedicated voice/chat applications and Minecraft are filtered out. Process-loopback capture requires Windows build 20348+. Source enumeration is on the default Windows render endpoint. Disc playback works without native Windows capture.

The native source is played locally as streamed 48 kHz mono PCM. Boombox listeners receive bounded, rate-limited 16 kHz mono mu-law chunks, within earshot. Device radius is 10–32 metres, with a smooth squared distance falloff. One broadcast per owner; other players cannot take over an active source unless creative. Capture restores the source mixer volume on stop, disconnect, dimension change, capture failure and normal shutdown. A restore record is written before parking volume and recovered on next client startup after a crash.

Worn headphones attenuate world sounds during personal playback, configured independently from device music volume. A server-confirmed bike crash drops worn headphones as an item. Headphones and the attached boombox are visible; the attachment is preserved in bike item/save data.

Native capture and audio-device behavior remain unverified in Minecraft. Music-disc late listeners begin playback from the beginning rather than seeking to the broadcaster's elapsed position. Browser capture operates per application, not per tab.

## 11: GPS maps

Use GPS to start/stop a recording; keep it in the hotbar/offhand while riding. Use a stopped GPS carrying a route on a trail sign to link it. The linked route is persisted and synchronized with the sign; a small GPS label identifies linked signs. Use a trail map on the sign to copy its route. Alternatively hold the recorded GPS in the other hand and use the map. Hold another filled map in the other hand to copy its routes.

Maps store up to 12 routes, simplified to 512 points per route to bound item synchronization. The screen shows route shapes, start/finish markers, distance, ascent/descent, elevation profile, dimension and a live player marker. Click a route or scroll to select it. Routes from different dimensions are shown separately. The actual recorded map is rendered in inventories, hands and item frames. Map copies do not depend on the sign remaining loaded.

## 12: riding effects

The server accumulates bike mud on mud or wet loose ground. It persists through bike pickups and saves and is synchronized to other viewers. Transparent mud speckles affect frame, fork lowers, rims, pedals and tyres, including the bike item. Water rinses it; a water bucket washes a parked bike. Rain slowly rinses a stationary bike.

Nearby bikes throw dirt particles when braking/sliding on loose ground. Wooden decking does not throw dirt. The helmet camera gets peripheral mud splashes on wet trails. Y requests a goggle tear-off from the inventory; the server consumes one film with a cooldown before acknowledging a wipe. Using the film item directly also works.

Peripheral speed streaks appear smoothly above 28 km/h in helmet view. Existing smoothed speed FOV now has a configurable strength. Config group `immersion`: `mudEffects`, `roostParticles`, `speedLines`, `roostDensity`, `speedLineStrength`, `speedFovStrength`. Audio group `audioDevices`: `musicVolume`, `headphoneWorldVolume`.

## Artifacts and remaining work

- `dist/descentmtb-milestone-10-audio-devices.jar`
- `dist/descentmtb-milestone-11-gps-maps.jar`
- `dist/descentmtb-milestone-12-immersion-complete.jar`
- `dist/descentmtb-latest.jar` points to milestone 12.

These features are implemented and compile; build success does not verify their appearance, audio behavior or multiplayer behavior in game. The separate manufacturer-specific rear suspension geometry work mentioned in the earlier handoff is still pending; milestone 09 repaired the frame connection errors, not faithful per-brand suspension kinematics. The source WIP folder remains as historical material; edit the active `src/main/java/.../audio` code going forward.
