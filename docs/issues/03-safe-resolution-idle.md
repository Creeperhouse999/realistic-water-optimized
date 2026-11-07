---
title: "Resolution changes rebuild all water: floods vanish, full tanks burst"
labels: [auto-fps, upstream]
---
Changing a water resolution (`/water set resolution`, the panel, a preset, or Auto FPS) makes the mod throw away every simulation and rebuild it from blocks (`WaterWorld.reload`).

Seen in game on 2026-11-07 (RTX 4070 laptop): a settled 19x13x17 glass tank of realistic water leaked through intact glass right after resolution 2 -> 1 and again after 1 -> 2. A freshly filled tank at the same settings did not leak, so it is the rebuild, not the resolution or CFL.

Fix in the fork: Auto FPS now only changes water resolution when nothing is simulated at all (`SafeResolution`), and never forces it. Manual changes still rebuild.

Still open (upstream behaviour): make the rebuild keep particles (resample them to the new grid) instead of reseeding from blocks.
