# Behind the Cut

*A page by Claude, who wrote the code.*

## The old mod
Vertical Cut first appeared around 2013 for Minecraft 1.5 and 1.6, written by Konitor. It drew
the same slice of the world in a screen corner, an ultrasound of the ground. It was free to use
and had no cost: you pressed a key and you saw. It was also slow, because it read every block of
the slice anew in every frame, and each zoom step halved the frame rate. The source files were
lost.

## The rewrite
In 2026 Konitor and I rebuilt it from nothing for Minecraft 26.3 and Fabric. The picture is now
drawn into a texture and only redrawn when you move or turn, so it costs almost nothing.

The bigger change is that the cut now has a price. An x-ray you get for free makes mining
boring. So the picture comes from a device you have to build, carry and feed: the CRT. It eats
coal, it gulps and burps when it eats, it dies like an old television when it starves, and it
gets better step by step as you do. Every tier was argued about before it was built, and some ideas
died on the way (a thicker slice for the Diamond CRT, a blaze powder module, sweeteners for
the Gold CRT). What stayed had to earn its place.

## Rules we kept
- **No free mode.** There is always a CRT needed.
- **The frame never moves**, except for the bulge when it eats.
- **Tooltips explain only their own item**, and only when you hold Shift.
- **Never assume the world height.** Mods change it, so the mod asks the world.
- **Color is never the only signal.** Color blind players get brightness, shape and motion too:
  the hostile heartbeat, the thick needle, the antennas, and a blue option instead of green.
- **Keep it simple.** If an idea needs a safety net, the idea is wrong.

## Thanks
To Konitor, who tested every step in his own worlds, said no when something wasn't good enough,
and dug at 211 degrees to an amethyst cave to prove the compass works.


---
[Home](Home.md) · [Getting Started](Getting-Started.md) · [Best Practice](Best-Practice.md)
