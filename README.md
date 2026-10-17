# Vertical Cut

A Fabric mod for Minecraft 26.3 (needed on both client and server). It shows a vertical cross-section of the
terrain in front of you in a corner of the screen, like an ultrasound scan of the world,
so you can spot ores, caves and structures without digging.

This is a rewrite of the original Vertical Cut mod for Minecraft 1.5–1.6
([old forum page](https://www.minecraftforum.net/forums/mapping-and-modding-java-edition/minecraft-mods/1290593-vertical-cut-find-diamonds-iron-and-dungeons)).

**Status:** Stone, Copper and Iron CRT (the first three scanning devices) are in.

- The cut only works while you hold a CRT (offhand or main hand).
- **Stone CRT**, the starter device. Recipe (cobblestone, blackstone or cobbled deepslate all work):
  ```
  C G C      C = cobblestone, G = glass pane
  C T C      T = torch
  C C C
  ```
  It is a crude cave finder: it shows caves, water and lava, but no ores. The picture lags
  behind when you walk, and it flickers and buzzes now and then.
- **Copper CRT**, the upgrade. Shows ores as bright dots, a steady picture, and only flickers
  rarely. Made on the smithing table: torch + Stone CRT + Copper Picture Tube. The tube:
  ```
  C C C      C = copper ingot
  C T C      T = torch
  C C C
  ```
  The fuel in the CRT carries over to the upgrade.
- **Iron CRT**, the next upgrade: the first color picture, sees twice as far (64×64 blocks),
  no flicker of its own and 2 module slots. Smithing table: torch + Copper CRT + Iron Picture
  Tube. The tube:
  ```
  I I I      I = iron ingot
  I R I      R = redstone torch
  I I I
  ```
- Recipes appear in the recipe book when you pick up cobblestone, a copper or iron ingot, or the
  CRT to be upgraded.
- In both black and white CRTs, water is mid grey and lava blinks.
- CRTs burn coal: one coal or charcoal lasts 4 minutes of scanning (adjustable from
  60 to 600 seconds in the settings; on a server, the server's setting counts) and is taken from
  your inventory automatically when the CRT runs dry. It only burns while the cut is shown.
  The durability bar shows the coal that is burning right now. A new CRT starts empty.
- **Module slots**: sneak + right-click with the CRT opens them. The Stone CRT has none,
  the Copper CRT has one.
- **Coal pouch**: put any bundle (modded bundles too) into a module slot. The CRT then eats
  only from the bundle, never from your inventory, and it eats *anything* that burns in a
  furnace, worth as much as it would burn there compared to coal (a coal block is 10 coals,
  a lava bucket 12.5 and gives the empty bucket back). Careful: it burns enchanted wooden
  tools just as happily. Small fuels like sticks are eaten in batches of about one coal's
  worth. The CRT stores up to 256 coals' worth; whatever doesn't fit stays in the bundle.
  With a pouch, a thin bar beside the picture shows stored fuel plus the bundle
  (full at 64 coals), and the tooltip shows both numbers.
- **Interference**: lit portals (nether portal, end portal, end gateway; not the frames) within
  about 6 blocks and the nearest enderman within 8 blocks disturb the picture with snow and
  buzzing, the closer the worse; an angry enderman twice as much.
- **Lightning rod module**: any lightning rod in a module slot shields against three quarters
  of the interference.
- **Depth Gauge module** (3 sticks in a column, 3 copper nuggets beside them): a white ruler
  along the left edge of the picture with the Y level at your feet and every 8 blocks above and below.
- Hold **Shift** over a CRT or the tube for a short explanation.
- Stone and Copper CRT show 32×32 blocks.
- **V** shows or hides the cut. That is the only key bound by default.
- Settings (needs CRT on/off, corner, distance from edge, size, opacity, CRT sounds,
  seconds per coal, zoom, color of the stick figure that shows you) are in
  **Mod Menu → Vertical Cut → Configure** and are saved in `config/verticalcut.json`.
  With "Needs CRT in hand" off, the cut works without a device, in color, at the configured zoom.
- Keys for opacity, zoom and the settings screen exist but are unbound; bind them in
  Options → Controls → Key Binds → Vertical Cut if you want them.

## Translations

All player-facing text is in `src/main/resources/assets/verticalcut/lang/en_us.json`.
To translate, copy it to your language code (for example `de_de.json`), translate the
values and keep the keys and `%s` placeholders as they are.

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

MIT. See [LICENSE](LICENSE). (The original 1.5–1.6 mod was GPL-2.0; this is a full rewrite.)
