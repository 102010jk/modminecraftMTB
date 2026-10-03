# Descent MTB

A mountain-biking mod for **Minecraft 1.21.1 / NeoForge** with arcade,
*Descenders*-style physics: momentum, downhill acceleration, ramp/kicker
launches, charged bunny hops, lean and air control — with **full controller
support** alongside keyboard.

## Build

```powershell
cd C:\MTBMod
.\gradlew.bat build
```

The finished mod jar lands in `build\libs\descentmtb-0.1.0.jar`. Drop it into
your `.minecraft\mods` folder (NeoForge 1.21.1 profile) together with NeoForge.

For a dev test run: `.\gradlew.bat runClient`.

## How to ride

1. Grab a **Mountain Bike** from the *Descent MTB* creative tab (or
   `/give @s descentmtb:mountain_bike`).
2. **Right-click a block** to deploy the bike.
3. **Right-click the bike** to get on. **Sneak (Shift)** to get off.
4. Hit the bike (attack) to pick it back up.

## Controls (Descenders mapping)

| Action            | Controller            | Keyboard         |
|-------------------|-----------------------|------------------|
| Steer (turns bars)| Left stick X          | A / D            |
| **Lean / tilt**   | **Right stick X**     | Z / C            |
| Pedal (accel)     | Right trigger (RT)    | W                |
| Lean back / slow  | Left stick down       | S                |
| Brake             | Left trigger (LT)     | Left Ctrl        |
| Bunny hop         | A (hold to charge)    | Space (hold)     |

Steering only **turns the handlebars** and the heading — it no longer rolls the
whole bike. The body **lean/tilt** is its own thing on the **right stick** (Z/C
on keyboard).

The bunny hop is more than one button: **tap** = small hop; **hold** A/Space to
charge; **pull back** (left stick down / S) just before releasing to preload a
**manual-style pop** — the three stack. Controller input is read directly from
the OS gamepad (GLFW); on mounting, a chat line tells you which controller was
detected (or that none was). All keys rebind under
**Options → Controls → Descent MTB**.

## Ramps & jumps

Launches come straight out of the physics: ride up any incline and you fly off
the lip, with launch height scaling to the ramp's steepness. Build kickers from
**stairs or slabs** (the bike samples the real collision surface), or just hit
natural terrain. The steeper the lip and the faster you go, the bigger the air.

## Tuning the feel

Physics is modelled on the **Automobility** mod: steering is a smoothed
angular-speed (you turn more at speed, never twitchy, can't spin on the spot),
momentum comes from a grip blend, terrain is handled by gravity + a real step
height so it **climbs blocks/stairs**, and riding off a lip launches you using
the actual climb rate. The rider's view turns with the bike. All feel constants
live at the top of
[`MountainBikeEntity.java`](src/main/java/com/descentmtb/entity/MountainBikeEntity.java):
`COMFORT_SPEED`, `MAX_SPEED`, `ACCEL`, `BRAKE`, `SLOPE_GAIN`, `TURN_BASE`,
`TURN_SPEED`, `STEP_HEIGHT`, `HOP_BASE`, etc. Presentation/direction knobs:

- If A/D (or the stick) steer the *wrong* way, flip `STEER_SIGN` (currently `-1f`).
- If it climbs walls too eagerly / not enough, change `STEP_HEIGHT` (default `1.0`
  — set `0.6` for slabs/stairs only).
- If the bike visually floats or sinks, nudge `MODEL_Y` in
  [`MountainBikeRenderer.java`](src/main/java/com/descentmtb/client/MountainBikeRenderer.java).
- If the model faces backwards, change the `180.0f - entityYaw` term in the same
  file to `-entityYaw`.

## Not yet in (planned)

Tricks/whips (right stick), crash/bail on bad landings, sound, and a dedicated
ramp block. The input and physics are already structured to grow into these.
