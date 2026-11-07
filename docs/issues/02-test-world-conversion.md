---
title: "Test realistic world game rules"
labels: [world-conversion, untested, performance]
---
`realistic-water:convert_water / convert_lava / convert_sand / convert_radius / convert_per_second / convert_max_body` are written but untested.

Design: a whole connected body is converted in one tick (half a lake would drain into its vanilla half through the ocean sink). Bodies above `convert_max_body` or reaching unloaded chunks stay vanilla. Sources that feed a fall (flowing liquid going down within one step) become springs (`infinite=true`), flowing liquid becomes air, kelp/seagrass/bubble columns become water, waterlogged blocks are drained.

- [x] Rules show in Create World, More, Game Rules under "Realistic Water" with readable names
- [ ] Small pond (< 200 blocks): converts at once, water settles, `/water stats` drops to fluid 0
- [ ] Lake of ~3000 blocks: waits for tokens, converts whole; tick time spike when it does
- [x] Ocean next to the player: stays vanilla, no lag from repeated searches
- [ ] Waterfall: top becomes a spring, the fall keeps running, pool below fills without flooding the area
- [ ] Lava pool next to water: cobblestone/obsidian edges as expected
- [ ] Falling sand only (no whole beaches)
- [x] Panel World tab toggles and sliders change the rules (singleplayer)

Tested 2026-11-07 (RTX 4070 laptop, 4K): 75-block sealed basin and 2000-block sealed lake converted whole with no visible hitch; a floating water source became a spring and kept falling; a lava basin converted and poured out as realistic lava. Found and fixed: an ocean search skipped its whole chunk for 60 s, delaying a pond in the same chunk (skips are now per block).
