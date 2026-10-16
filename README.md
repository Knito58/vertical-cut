# Vertical Cut

A client-side Fabric mod for Minecraft 26.3. It shows a vertical cross-section of the
terrain in front of you in a corner of the screen, like an ultrasound scan of the world,
so you can spot ores, caves and structures without digging.

This is a rewrite of the original Vertical Cut mod for Minecraft 1.5–1.6
([old forum page](https://www.minecraftforum.net/forums/mapping-and-modding-java-edition/minecraft-mods/1290593-vertical-cut-find-diamonds-iron-and-dungeons)).

**Status:** Copper CRT (the first scanning device) is in.

- The cut only works while you hold a **Copper CRT** (offhand or main hand). Recipe:
  ```
  C G C      C = copper ingot, G = glass pane
  C R C      R = redstone
  C T C      T = torch
  ```
- The CRT burns coal: one coal or charcoal lasts 4 minutes of scanning (adjustable from
  60 to 600 seconds in the settings; on a server, the server's setting counts) and is taken from
  your inventory automatically when the tank is empty. It only burns while the cut is shown.
  The durability bar is the fuel gauge. A new CRT starts empty.
- The Copper CRT shows a black and white picture, 32×32 blocks. Ores are bright dots.
- **V** shows or hides the cut. That is the only key bound by default.
- Settings (needs CRT on/off, corner, distance from edge, size, opacity, zoom) are in
  **Mod Menu → Vertical Cut → Configure** and are saved in `config/verticalcut.json`.
  With "Needs CRT in hand" off, the cut works without a device, in color, at the configured zoom.
- Keys for opacity, zoom and the settings screen exist but are unbound; bind them in
  Options → Controls → Key Binds → Vertical Cut if you want them.

## Building and testing (Linux)

You need JDK 25 (`java -version` and `javac -version` must both say 25), git and IntelliJ IDEA.

First time:

1. In IntelliJ: **File → New → Project from Version Control**, URL `https://github.com/Knito58/vertical-cut`.
2. Wait until the Gradle import at the bottom right finishes. The first time it downloads
   Minecraft and Fabric, which takes a few minutes.
3. Open the **Gradle** tool window (elephant icon on the right) and run
   **Tasks → fabric → runClient**. Minecraft starts with the mod loaded.

After that, to get the newest version: **Git → Pull**, then run `runClient` again.

To build the mod file for your modpack, run **Tasks → build → build**. The jar is in
`build/libs/` (take the one without `-sources`) and goes into the modpack's `mods` folder.

Every push also builds the mod on GitHub. The jar can be downloaded from the
**Actions** tab, under the latest run, in "Artifacts".

## License

GPL-2.0-only, as the original mod. See [LICENSE](LICENSE).
