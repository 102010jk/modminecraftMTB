# Milestone 15 — live audio buffering

Release: `dist/descentmtb-milestone-15-live-audio.jar`, also copied to `dist/descentmtb-latest.jar`.

Minecraft's `Channel.attachBufferStream` requests and queues four one-second buffers. The previous `PcmStream` returned those full buffers despite a ring capacity of only 250 ms, padding the remaining samples with silence. At startup it queued several seconds of silence; during playback it repeatedly mixed short audio sections with long gaps. This defect was found in the local Minecraft/NeoForge source, not through a game test.

Live streams now return 80 ms buffers and start after 400 ms of actual samples are available (four queued blocks plus one scheduling margin). Local queues hold up to 750 ms; remote mono queues hold up to one second. A brief fade handles genuine underruns, with a startup grace period to avoid repeatedly restarting an asynchronously opened sound. Headphones retain the captured stereo channels and use listener-relative position zero; the positioned boombox remains mono.

Network chunk sequences are assigned at capture time. The sender keeps the newest bounded backlog. Remote clients discard duplicates and preserve elapsed time for small sequence gaps instead of concatenating separated pieces of music. No native application volume or protocol format was changed.

Verification: Java 21 `assemble` passed. No game, playback, headphones, multiplayer, native capture or independent agent review was performed. The buffer sizes follow the source contract; actual smoothness still needs user listening. Terrain rendering from the screenshot is the next separate slice.
