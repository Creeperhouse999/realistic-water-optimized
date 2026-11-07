---
title: "Test realistic world game rules"
labels: [world-conversion, untested, performance]
---
`realistic-water:convert_water / convert_lava / convert_sand / convert_radius / convert_per_second / convert_max_body` are written but untested.

Design: a whole connected body is converted in one tick (half a lake would drain into its vanilla half through the ocean sink). Bodies above `convert_max_body` or reaching unloaded chunks stay vanilla. Sources that feed a fall (flowing liquid going down within one step) become springs (`infinite=true`), flowing liquid becomes air, kelp/seagrass/bubble columns become water, waterlogged blocks are drained.

- [ ] Rules show in Create World, More, Game Rules under "Realistic Water" with readable names
- [ ] Small pond (< 200 blocks): converts at once, water settles, `/water stats` drops to fluid 0
- [ ] Lake of ~3000 blocks: waits for tokens, converts whole; tick time spike when it does
- [ ] Ocean next to the player: stays vanilla, no lag from repeated searches
- [ ] Waterfall: top becomes a spring, the fall keeps running, pool below fills without flooding the area
- [ ] Lava pool next to water: cobblestone/obsidian edges as expected
- [ ] Falling sand only (no whole beaches)
- [ ] Panel World tab toggles and sliders change the rules (singleplayer)
