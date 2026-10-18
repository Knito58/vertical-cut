# Changelog

## 1.0.1 (not released yet)

- Finder bundle with a chest: also finds modded inventories that only offer Fabric's transfer API, like drawers.
- Stained glass panes were invisible in the cut; they now show in their glass color.
- Modded blocks without a map color, like the walls of Colossal Chests, were invisible in the cut.
  Blocks you can't walk through now show in the color of their texture.

## 1.0.0 (2026-10-17)

First release of the rewrite for Minecraft 26.3 (Fabric). Needed on client and server.

- Six CRTs (Coal-powered Rock Tomographs) from Stone to Netherite, each one made from the one before:
  Stone (caves, water, lava), Copper (ores, black and white), Iron (color, 64×64), Gold (zoom 32/64),
  Diamond (zoom 32/64/128, 3 module slots), Netherite (living things as dots, immune to interference,
  fire-proof).
- CRTs burn coal (4 minutes per coal by default, 60 to 600 seconds in the settings), refill themselves
  from your inventory and die like an old TV when they starve.
- Modules (sneak + right-click with the CRT): coal pouch (any bundle, burns anything a furnace burns),
  finder bundle (bundle with an amethyst shard; ores of the samples inside flash, modded ores via `c:` tags,
  a chest finds containers), lightning rod (shields against interference), compass (K locks the direction),
  spyglass (J looks one picture deeper), and the Depth Gauge (Y levels).
- Interference from lit portals and endermen.
- Advancement tab with a riddle for every tier.
- Settings via Mod Menu, including a color blind option.
