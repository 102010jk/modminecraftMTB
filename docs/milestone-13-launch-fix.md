# Milestone 13 — remove duplicate JNA platform module

The user supplied a Modrinth launch log using Minecraft 1.21.1 / NeoForge 21.1.252. ModLauncher failed before initialization with `ResolutionException: Module com.twelvemonkeys.imageio.core reads more than one module named com.sun.jna.platform`.

Milestone 10 bundled `jna-platform-5.14.0.jar` through Jar-in-Jar, while Minecraft already supplies that module. The dependency is now compile-only; JNA/platform is supplied by Minecraft at runtime. The packaged dependency remains Sable Companion only. No audio implementation was removed.

Validation: `gradlew.bat assemble --console=plain` succeeded. The resulting archive and `META-INF/jarjar/metadata.json` were inspected: no JNA JAR or platform classes are packaged. The user's base Minecraft JNA platform library is present in the Modrinth library directory. No game launch or screenshots were performed.

Artifact: `dist/descentmtb-milestone-13-launch-fix.jar`; `dist/descentmtb-latest.jar` is its identical copy. Replace the existing Descent MTB mod JAR; do not leave both versions in the mods folder.

The frame geometry correction requested through screenshots remains a separate ongoing task.
